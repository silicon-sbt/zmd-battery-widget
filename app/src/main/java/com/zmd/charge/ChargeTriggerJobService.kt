package com.zmd.charge

import android.app.job.JobParameters
import android.app.job.JobService
import com.zmd.charge.log.LogFile
import com.zmd.charge.widget.BatteryWidgetProvider

/**
 * JobScheduler 充电唤醒任务：system_server 判定"正在充电"时拉起本组件，
 * 从而绕过"进程被厂商冻结=收不到插电广播"的死结。
 *
 * 触发流程：充电接通 → JobSchedulerService 判定充电约束满足 → 拉起本服务进程 →
 * onStartJob → 重新武装(Keepalive.rearm，含重注册广播 + 检测"正在充电则补弹岛") + 刷新组件。
 *
 * 说明：充电动画的真正触发在 Keepalive.onChargeConnected（不依赖本任务）——
 * 本任务只是当动态广播因进程被冻结而收不到时，靠 system_server 拉起进程，
 * 再由 rearm 检测到"正在充电"从而补播。厂商可能延迟数秒～数十秒触发，但总好过完全收不到。
 */
class ChargeTriggerJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        LogFile.i("Job", "充电任务触发（system_server 拉起进程）")
        try {
            // 只做"重新武装 + 补弹岛 + 刷新组件"这三件事。
            // 绝不能在这里再排 Job —— 充电约束已满足时会立刻再次触发，形成 hot loop。
            // 周期任务本身会被系统反复触发，不需要我们重排；one-shot 只在外部入口排。
            Keepalive.onWakeFromJob(this)
            BatteryWidgetProvider.refresh(this)
        } catch (t: Throwable) {
            LogFile.e("Job", "充电任务执行失败", t)
        }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false  // 不重试
    }
}
