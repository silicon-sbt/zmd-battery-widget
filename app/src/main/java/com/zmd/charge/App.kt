package com.zmd.charge

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.zmd.charge.log.LogFile
import com.zmd.charge.widget.BatteryData
import com.zmd.charge.widget.BatteryWidgetProvider

/**
 * 动态注册电池广播（ACTION_BATTERY_CHANGED 是 sticky 受保护广播，只能用动态接收器），
 * 并在插电瞬间播放几秒的"充电提示"。
 *
 * 注意：这里刻意【不使用 setAlpha/setFloat】做淡入淡出——部分第三方桌面（华为/荣耀 EMUI）
 * 对反射类 RemoteViews 动作容错差，一旦 apply 失败整个组件会变成"加载窗口小工具时出现问题"。
 * 改为交替切换"亮帧/暗帧"两张预渲染图 + 两种文字颜色，所有帧都是完全可见的静态资源，
 * 组件在任何一帧被中断都不会显示成空白或报错。
 */
class App : Application() {

    private val handler = Handler(Looper.getMainLooper())
    private var hintActive = false
    private var lastPercent = -1
    private var lastCharging: Boolean? = null
    /** goAsync 保住进程，让提示动画跑完（总时长 3.2s，远小于广播接收器超时上限）。 */
    private var pendingResult: BroadcastReceiver.PendingResult? = null

    /** 脉冲：每 650ms 换一次亮/暗帧。 */
    private val pulse = object : Runnable {
        override fun run() {
            if (!hintActive) return
            val t = SystemClock.elapsedRealtime() - BatteryWidgetProvider.hintStartedAt
            BatteryWidgetProvider.hintBright = ((t / 650L) % 2L) == 0L
            BatteryWidgetProvider.refresh(this@App)
            handler.postDelayed(this, 650L)
        }
    }

    private val endHintRunnable = Runnable { endHint(true) }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    // 先锁定"充电中"，避免滞后的 sticky 广播把绿环刷回黄环
                    BatteryWidgetProvider.forceChargingState(true)
                    finishPending()                       // 收掉上一次的，避免 PendingResult 泄漏导致广播超时
                    pendingResult = goAsync()             // 保住进程直到提示动画跑完
                    LogFile.i("Charge", "POWER_CONNECTED：锁定充电状态并开始提示动画")
                    startHint(context)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    BatteryWidgetProvider.forceChargingState(false)
                    endHint(false)
                    BatteryWidgetProvider.refresh(context)
                    LogFile.i("Charge", "POWER_DISCONNECTED：已锁定未充电并刷新")
                }
                else -> {
                    // 电量广播：只在"电量/充电状态真的变了"时记一行，避免刷屏
                    val snap = try { BatteryData.read(context, null) } catch (t: Throwable) { null }
                    if (snap != null && (snap.percent != lastPercent || snap.charging != lastCharging)) {
                        lastPercent = snap.percent
                        lastCharging = snap.charging
                        LogFile.i("Battery", "电量 " + snap.percent + "% 充电中=" + snap.charging +
                                " 剩余 " + snap.remainingMah + "/" + snap.designMah + " mAh")
                    }
                    BatteryWidgetProvider.refresh(context)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        LogFile.init(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        // 个别 ROM 在极端情况下会拒绝注册，绝不能因此让整个进程挂掉
        try {
            registerReceiver(batteryReceiver, filter)
        } catch (t: Throwable) {
            LogFile.e("App", "registerReceiver 失败", t)
        }
        // 兜底：即使进程被杀，也靠周期闹钟唤醒刷新组件（最多 1 分钟延迟）
        BatteryWidgetProvider.scheduleRefresh(this)
    }

    private fun startHint(context: Context) {
        hintActive = true
        BatteryWidgetProvider.showChargeHint = true
        BatteryWidgetProvider.hintStartedAt = SystemClock.elapsedRealtime()
        BatteryWidgetProvider.hintFast = isFastCharge(context)
        BatteryWidgetProvider.hintBright = true
        LogFile.i("Charge", "提示动画开始 快充=" + BatteryWidgetProvider.hintFast +
                " 时长=" + BatteryWidgetProvider.HINT_DURATION_MS + "ms")
        handler.removeCallbacks(pulse)
        handler.removeCallbacks(endHintRunnable)
        handler.post(pulse)                 // 立即画第一帧（亮帧）
        handler.postDelayed(endHintRunnable, BatteryWidgetProvider.HINT_DURATION_MS)
    }

    /** @param render true 时顺带刷新一次组件（断电路径由调用方自己刷新，避免重复推送）。 */
    private fun endHint(render: Boolean) {
        handler.removeCallbacks(pulse)
        handler.removeCallbacks(endHintRunnable)
        if (hintActive) {
            hintActive = false
            BatteryWidgetProvider.showChargeHint = false
            BatteryWidgetProvider.hintBright = true
            if (render) BatteryWidgetProvider.refresh(this)
            LogFile.i("Charge", "提示动画结束，已回到电量显示")
        }
        finishPending()
    }

    private fun finishPending() {
        val pr = pendingResult ?: return
        pendingResult = null
        try { pr.finish() } catch (t: Throwable) {}
    }

    /**
     * 快充判定：读实际充电电流 CURRENT_NOW(通常 µA，部分机型 mA)。
     * 电流 >= 1500mA 视为快充；读不到时回退到"墙充(AC)且非 USB"的粗略判断。
     * 任何异常都退化成"普通充电"，绝不抛出（否则插电瞬间进程会崩）。
     */
    private fun isFastCharge(context: Context): Boolean {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val cur = try {
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            } catch (e: Exception) { null }
            var mA = 0
            if (cur != null && cur != Int.MIN_VALUE && cur != 0) {
                val a = kotlin.math.abs(cur)
                mA = if (a > 20000) a / 1000 else a   // 量级判断：µA -> mA
            }
            if (mA > 0) return mA >= 1500
            val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            plugged == BatteryManager.BATTERY_PLUGGED_AC
        } catch (t: Throwable) {
            false
        }
    }
}
