package com.zmd.charge

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.zmd.charge.chargepop.ChargePop
import com.zmd.charge.log.LogFile
import com.zmd.charge.settings.Prefs
import com.zmd.charge.widget.BatteryData

/**
 * 把「插电弹岛」从「依赖前台服务常驻」里解放出来，并尽量减轻冻结带来的影响。
 *
 * 背景：国产 ROM（尤其 vivo/OriginOS）会冻结静置进程，冻结期间广播收不到。
 * 真机实测（V2403A / OriginOS 6）结论：
 *  - 冻结中 ACTION_POWER_CONNECTED 会被 DEFERRED 挂起，收不到；
 *  - 冻结中 JobScheduler 即使 cmd jobscheduler run -f 强推，进程也不执行；
 *  - 一旦解冻（打开 App / 点组件），广播与 Job 立刻恢复正常。
 * 所以 JobScheduler 不是"免疫冻结"的通道，它的价值是在【未冻结】时兜底、
 * 以及在【刚解冻】时立刻恢复武装。
 *
 * 本类做三件事：
 *  1. 动态 ACTION_POWER_CONNECTED：进程活着时秒级响应插电（主路径）。
 *  2. JobScheduler 充电周期任务：未冻结时提供周期性兜底唤醒。
 *  3. rearm() 自愈：挂到所有能进进程的入口（开 App / 开机 / 点组件 / Job onStart /
 *     前台服务启动），每次进来都重新武装，幂等。
 */
object Keepalive {

    /** 周期性兜底任务：系统托管（充电时每 15 分钟给一次拉起机会）。 */
    private const val JOB_ID_PERIODIC = 1002
    private const val PERIODIC_INTERVAL_MS = 15 * 60 * 1000L
    private const val SETTLE_DELAY_MS = 3600L   // 息屏插电时避让系统充电动画
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 插拔电监听接收器（弹岛）。进程活着时动态注册即可，不依赖前台服务。 */
    private var powerReceiver: BroadcastReceiver? = null

    /** 统一自愈入口：所有唤醒路径都调它，幂等。 */
    fun rearm(context: Context) {
        registerPower(context)      // 1. 广播武装（进程活着才能收）
        schedulePeriodic(context)   // 2. 周期性充电任务（未冻结时兜底）
        // 4. 若此刻已在充电，补一次弹岛（解冻自愈后不必等下一次插拔）
        //    加闸：进程刚启动时会有 App/LiveService/Job 等多个入口并发调 rearm，
        //    若各自都补播会连弹多次（实测 8 次）。这里限成"进程内只补播一次"。
        try {
            if (Prefs.chargePop(context) && BatteryData.isCharging(context)) {
                if (markRearmPopupOnce()) {
                    LogFile.i("Keepalive", "rearm：当前正在充电，补播一次插电动画")
                    ChargePop.show(context)
                }
            }
        } catch (t: Throwable) {
            LogFile.e("Keepalive", "rearm 补播失败", t)
        }
    }

    /** rearm 补播闸：同一进程内只允许补播一次，避免多入口并发连弹。 */
    @Volatile
    private var rearmPopupDone = false
    @Synchronized
    private fun markRearmPopupOnce(): Boolean {
        if (rearmPopupDone) return false
        rearmPopupDone = true
        return true
    }

    /**
     * 被 JobScheduler 唤醒时的入口：只重新武装广播 + 必要时补弹岛。
     * 刻意【不重排 any Job】—— 周期任务由系统自动重复触发，one-shot 由外部入口排，
     * 在这里重排会造成"充电约束已满足→立刻再触发→再重排"的 hot loop（实测每秒数次）。
     */
    fun onWakeFromJob(context: Context) {
        registerPower(context)
        try {
            if (Prefs.chargePop(context) && BatteryData.isCharging(context)) {
                // 与 rearm 共用同一个"进程内只补播一次"闸，避免广播/Job/服务多入口连弹
                if (markRearmPopupOnce()) {
                    LogFile.i("Keepalive", "Job 唤醒：当前在充电，补播插电动画")
                    ChargePop.show(context)
                }
            }
        } catch (t: Throwable) {
            LogFile.e("Keepalive", "Job 唤醒补播失败", t)
        }
    }

    /** 插电事件统一入口（动态广播触发，真插电）。 */
    fun onChargeConnected(context: Context) {
        if (!Prefs.chargePop(context)) return
        // 真插电已经要播了，占住补播闸，避免紧接着 rearm/Job 因"正在充电"再补一次
        markRearmPopupOnce()
        LogFile.i("Keepalive", "插电事件到达，开始弹岛")
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm?.isInteractive != false) {
                ChargePop.show(context)
            } else {
                // 息屏插电：系统先播自己的充电动画（OriginOS FlashCharge 圆环盖住整屏），
                // 延后 3.6s 再弹，否则会被压在下面看不见。
                LogFile.i("Keepalive", "息屏插电，动画延后 " + SETTLE_DELAY_MS + "ms")
                mainHandler.postDelayed({ ChargePop.show(context) }, SETTLE_DELAY_MS)
            }
        } catch (t: Throwable) {
            LogFile.e("Keepalive", "插电弹岛失败", t)
        }
    }

    // ---------- 广播武装 ----------

    @Synchronized
    private fun registerPower(context: Context) {
        if (powerReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        LogFile.i("Keepalive", "收到插电广播")
                        onChargeConnected(c)
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> LogFile.i("Keepalive", "收到拔电广播")
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        try {
            context.registerReceiver(r, f)
            powerReceiver = r
            LogFile.i("Keepalive", "插拔电广播已注册（动态）")
        } catch (t: Throwable) {
            powerReceiver = null
            LogFile.e("Keepalive", "注册插拔电广播失败", t)
        }
    }

    // ---------- JobScheduler 充电唤醒（防冻结主通道） ----------

    /** 周期性兜底：由系统托管、不会被单次执行吃掉，进程被冻死也有稳定拉起机会。 */
    fun schedulePeriodic(context: Context) {
        try {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            if (js.allPendingJobs.any { it.id == JOB_ID_PERIODIC }) return  // 幂等
            val info = JobInfo.Builder(JOB_ID_PERIODIC, ComponentName(context, ChargeTriggerJobService::class.java))
                .setRequiresCharging(true)
                .setPeriodic(PERIODIC_INTERVAL_MS)
                .setPersisted(true)   // 重启后仍在（需 RECEIVE_BOOT_COMPLETED，已有）
                .build()
            val rc = js.schedule(info)
            LogFile.i("Keepalive", "已排程周期性充电兜底任务 result=" + rc)
        } catch (t: Throwable) {
            LogFile.e("Keepalive", "排程周期性充电任务失败", t)
        }
    }

    fun cancelChargeJobs(context: Context) {
        try {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            js.cancel(JOB_ID_PERIODIC)
        } catch (_: Throwable) {}
    }

}
