package com.zmd.charge.chargepop.upstream

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.BatteryManager
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout

/**
 * 上游适配层：把参考实现的 EndfieldHudView + HudTimeline 接到本项目里。
 *
 * 与复刻版一致，做成"岛"：**背景全透明**，HUD 贴在状态栏下方居中，
 * 不占满屏幕、不遮挡下面的内容。
 *
 * 来源：NingmengLemon/EndfieldChargingAnim（2026-10 起 MIT，其 LICENSE 注明动画移植自 zmd-charge/MIT）。
 * 保留出处即可自由使用（见同目录 README-UPSTREAM.md）。
 */
class UpstreamPopView(
    context: Context,
    level: Int,
    plugged: Int,
    capacityMah: Int,
) : FrameLayout(context) {

    private val hud = EndfieldHudView(context, context.assets)
    val timeline = HudTimeline()

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = timeline.durationMillis
        interpolator = LinearInterpolator()
        addUpdateListener { a ->
            hud.render(timeline.evaluateCharge((a.animatedValue as Float).toDouble()))
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        hud.setSnapshot(
            BatterySnapshot(
                level = level,
                plugged = plugged,
                status = BatteryManager.BATTERY_STATUS_CHARGING,
                voltageUv = 0L,
                chargeFullUah = capacityMah * 1000L,
                chargeCounterRaw = capacityMah * 1000L * level / 100,
            )
        )
        val topMargin = HudMetrics.topMarginPx(context, 6)
        // 宽度铺满 View（HUD 内部按 w/560 算缩放），高度按 560:90 留足，否则 B 态会被裁
        val w = context.resources.displayMetrics.widthPixels
        val hudH = (w * 90f / 560f).toInt()  // 设计比例 560:90
        addView(
            hud,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                hudH,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { this.topMargin = topMargin }
        )
        animator.start()
        Log.i("UpstreamPop", "island start topMarginPx=" + topMargin + " hudW=" + w + " hudH=" + hudH + " durationMs=" + timeline.durationMillis)
    }

    val durationMillis: Long get() = timeline.durationMillis

    fun stop() {
        if (animator.isRunning) animator.cancel()
    }
}
