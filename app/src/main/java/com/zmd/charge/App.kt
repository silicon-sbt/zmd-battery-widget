package com.zmd.charge

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import com.zmd.charge.widget.BatteryWidgetProvider

class App : Application() {

    private val handler = Handler(Looper.getMainLooper())
    private var hintActive = false
    // goAsync 保住进程，让充电提示动画+回落能完整跑完
    private var pendingResult: BroadcastReceiver.PendingResult? = null

    private val fadeIn = object : Runnable {
        override fun run() {
            val a = (BatteryWidgetProvider.hintAlpha + 0.16f).coerceAtMost(1f)
            BatteryWidgetProvider.hintAlpha = a
            BatteryWidgetProvider.refresh(this@App)
            if (a < 1f) handler.postDelayed(this, 55L) else handler.post(pulse)
        }
    }

    private val pulse = object : Runnable {
        override fun run() {
            val a = if (BatteryWidgetProvider.hintAlpha > 0.9f) 0.82f else 1f
            BatteryWidgetProvider.hintAlpha = a
            BatteryWidgetProvider.refresh(this@App)
            if (hintActive) handler.postDelayed(this, 300L)
        }
    }

    private val fadeOut = object : Runnable {
        override fun run() {
            val a = (BatteryWidgetProvider.hintAlpha - 0.25f).coerceAtLeast(0f)
            BatteryWidgetProvider.hintAlpha = a
            BatteryWidgetProvider.refresh(this@App)
            if (a > 0f) {
                handler.postDelayed(this, 55L)
            } else {
                BatteryWidgetProvider.showChargeHint = false
                BatteryWidgetProvider.hintAlpha = 1f
                BatteryWidgetProvider.refresh(this@App)
                finishHint()
            }
        }
    }

    private val endHintRunnable = Runnable { endHint() }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    BatteryWidgetProvider.forceChargingState(true)
                    pendingResult = goAsync()   // 保活进程直到动画+回落完成
                    startHint(context)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    stopAll()
                    BatteryWidgetProvider.showChargeHint = false
                    BatteryWidgetProvider.hintAlpha = 1f
                    // 拔电后 5 秒内锁定"未充电"，避免滞后的 sticky 广播把绿环刷回来
                    BatteryWidgetProvider.forceChargingState(false)
                    BatteryWidgetProvider.refresh(context)
                    finishHint()
                }
                else -> BatteryWidgetProvider.refresh(context)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(batteryReceiver, filter)
        // 兜底：即使进程被杀，也靠周期闹钟唤醒刷新组件（最多 1 分钟延迟）
        BatteryWidgetProvider.scheduleRefresh(this)
    }

    private fun startHint(context: Context) {
        hintActive = true
        BatteryWidgetProvider.showChargeHint = true
        BatteryWidgetProvider.hintFast = isFastCharge(context)
        BatteryWidgetProvider.hintAlpha = 0f
        BatteryWidgetProvider.refresh(context)
        handler.removeCallbacks(fadeIn)
        handler.removeCallbacks(pulse)
        handler.removeCallbacks(fadeOut)
        handler.removeCallbacks(endHintRunnable)
        handler.post(fadeIn)
        handler.postDelayed(endHintRunnable, 3400L)
    }

    private fun endHint() {
        if (!hintActive) return
        hintActive = false
        handler.removeCallbacks(fadeIn)
        handler.removeCallbacks(pulse)
        handler.post(fadeOut)
    }

    private fun stopAll() {
        hintActive = false
        handler.removeCallbacks(fadeIn)
        handler.removeCallbacks(pulse)
        handler.removeCallbacks(fadeOut)
        handler.removeCallbacks(endHintRunnable)
    }

    private fun finishHint() {
        val pr = pendingResult ?: return
        pendingResult = null
        pr.finish()
    }

    /**
     * 快充判定：读实际充电电流 CURRENT_NOW(通常 µA，部分机型 mA)。
     * 电流 >= 1500mA 视为快充；读不到时回退到"墙充(AC)且非 USB"的粗略判断。
     */
    private fun isFastCharge(context: Context): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val cur = try { bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) } catch (e: Exception) { null }
        var mA = 0
        if (cur != null && cur != Int.MIN_VALUE && cur != 0) {
            val a = kotlin.math.abs(cur)
            mA = if (a > 20000) a / 1000 else a   // 量级判断：µA → mA
        }
        if (mA > 0) return mA >= 1500
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        return plugged == BatteryManager.BATTERY_PLUGGED_AC
    }
}