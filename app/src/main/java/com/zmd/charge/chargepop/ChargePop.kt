package com.zmd.charge.chargepop

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.zmd.charge.R
import com.zmd.charge.log.LogFile

/**
 * 插电动画的入口：做冷却去重后拉起 [ChargePopActivity]。
 * 冷却用于挡住"充电器接触不良导致反复重连"时的连续弹窗。
 */
object ChargePop {

    private const val COOLDOWN_MS = 8_000L
    private var lastShownAt = 0L

    /** 设置页的"预览"按钮：无视冷却与前台判断，强制播一次。 */
    fun showNow(ctx: Context): Boolean {
        lastShownAt = 0L
        return launch(ctx)
    }

    /** 返回 true 表示这次真的发起了显示。 */
    fun show(ctx: Context): Boolean {
        // 注：这里刻意不再判断"本应用是否在前台" —— 插电弹岛是核心功能，
        // 任何场景（桌面/别的 App/自己的设置页）都该出现；旧判断在按 HOME 后
        // 没有及时复位，会把应用外的插电误判成"在前台"而静默跳过。
        val now = SystemClock.elapsedRealtime()
        if (lastShownAt != 0L && now - lastShownAt < COOLDOWN_MS) {
            LogFile.i("ChargePop", "冷却中，跳过本次插电动画")
            return false
        }
        return launch(ctx)
    }

    private fun launch(ctx: Context): Boolean {
        // 优先走悬浮窗：只有它才能盖在桌面/别的 App 上
        val (level, plugged) = ChargePopOverlay.readSticky(ctx)
        if (ChargePopOverlay.canShow(ctx)) {
            val ok = ChargePopOverlay.show(ctx, level, plugged)
            if (ok) {
                lastShownAt = SystemClock.elapsedRealtime()
                return true
            }
        }
        return try {
            // 用资源里声明的类名，这样 recreate/copied 两个 flavor 共用同一段调度逻辑
            val target = ctx.getString(R.string.charge_pop_activity)
            val i = Intent().setClassName(ctx, target).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }
            ctx.startActivity(i)
            lastShownAt = SystemClock.elapsedRealtime()
            LogFile.i("ChargePop", "已请求显示插电动画")
            true
        } catch (t: Throwable) {
            LogFile.e("ChargePop", "拉起插电动画失败", t)
            false
        }
    }
}
