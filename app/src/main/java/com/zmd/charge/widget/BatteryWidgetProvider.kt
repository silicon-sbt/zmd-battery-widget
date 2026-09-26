package com.zmd.charge.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlin.math.roundToInt
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.zmd.charge.R
import com.zmd.charge.log.LogFile
import com.zmd.charge.settings.Prefs
import com.zmd.charge.settings.SettingsActivity

class BatteryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { renderWidget(context, mgr, it) }
    }

    override fun onEnabled(context: Context) {
        // 组件被添加：启动周期刷新兜底
        scheduleRefresh(context)
    }

    override fun onDisabled(context: Context) {
        // 最后一个组件被移除：停止周期刷新
        cancelRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            // 周期闹钟触发：把进程唤醒并刷新组件
            refresh(context)
            scheduleRefresh(context) // 保险：重排下一次
        } else {
            super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.zmd.charge.widget.REFRESH"
        /** 周期刷新间隔；系统对 setRepeating 的下限约 60s。 */
        private const val REFRESH_INTERVAL_MS = 60_000L

        /** 插电提示动画总时长。 */
        const val HINT_DURATION_MS = 3200L

        /** 是否正在显示"插入充电提示"（仅插入后几秒内为 true）。 */
        @JvmStatic
        var showChargeHint: Boolean = false
        /** true=快充，false=普通充电。 */
        @JvmStatic
        var hintFast: Boolean = false
        /** 当前脉冲帧：true=亮帧，false=暗帧。 */
        @JvmStatic
        var hintBright: Boolean = true
        /** 提示开始时刻（elapsedRealtime），用于超时自愈。 */
        @JvmStatic
        var hintStartedAt: Long = 0L

        /** 插拔电后强制锁定的充电状态（避免被滞后的 sticky 广播覆盖）；带有效期。 */
        @JvmStatic
        var forcedCharging: Boolean? = null
        private var forcedUntil: Long = 0L

        /** 插拔电瞬间锁定充电状态若干毫秒，保证绿环/黄绿环立即、稳定地切换。 */
        fun forceChargingState(value: Boolean, durationMs: Long = 5000L) {
            forcedCharging = value
            forcedUntil = SystemClock.elapsedRealtime() + durationMs
        }

        /** 当前生效的充电状态覆盖（过期返回 null）。 */
        fun effectiveChargingOverride(): Boolean? =
            if (SystemClock.elapsedRealtime() < forcedUntil) forcedCharging else null

        /**
         * 自愈：提示只允许存在 HINT_DURATION_MS。若动画因进程被杀等原因没跑完，
         * 下一次刷新（电量广播/周期闹钟）会把组件强制拉回正常显示，绝不长期停在提示帧。
         */
        private fun healHint() {
            if (!showChargeHint) return
            if (SystemClock.elapsedRealtime() - hintStartedAt > HINT_DURATION_MS + 1500L) {
                showChargeHint = false
                hintBright = true
                LogFile.i("Widget", "充电提示帧超时未收回，已自愈回正常显示")
            }
        }

        /** 刷新所有已放置的组件实例（设置页变更/电量变化/闹钟触发时调用）。 */
        fun refresh(context: Context) {
            healHint()
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, BatteryWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            ids.forEach { renderWidget(context, mgr, it) }
        }

        /** 安排周期刷新（幂等：同 PendingIntent 会覆盖）。 */
        fun scheduleRefresh(context: Context) {
            try {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.setRepeating(
                    AlarmManager.RTC,
                    System.currentTimeMillis() + REFRESH_INTERVAL_MS,
                    REFRESH_INTERVAL_MS,
                    refreshIntent(context)
                )
            } catch (_: Throwable) {}
        }

        fun cancelRefresh(context: Context) {
            try {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.cancel(refreshIntent(context))
            } catch (_: Throwable) {}
        }

        private fun refreshIntent(context: Context): PendingIntent {
            val intent = Intent(context, BatteryWidgetProvider::class.java)
                .setAction(ACTION_REFRESH)
            return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

/**
 * 渲染单个组件实例。
 * 双层保护：先尝试正常渲染，任何异常都退回到"极简兜底布局"，
 * 确保桌面端永远不会出现"加载窗口小工具时出现问题"。
 */
private fun renderWidget(context: Context, mgr: AppWidgetManager, id: Int) {
    try {
        mgr.updateAppWidget(id, buildViews(context, id))
    } catch (t: Throwable) {
        LogFile.e("Widget", "渲染组件失败 id=" + id + "，已降级为兜底布局", t)
        try {
            mgr.updateAppWidget(id, buildSafeViews(context))
        } catch (t2: Throwable) {
            LogFile.e("Widget", "兜底布局也失败 id=" + id, t2)
        }
    }
}

/** 极简兜底：不含自定义字体/图片/自定义 drawable，只显示电量百分比。 */
private fun buildSafeViews(context: Context): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_layout_safe)
    val percent = try { BatteryData.read(context, null).percent } catch (t: Throwable) { -1 }
    views.setTextViewText(R.id.safe_text, if (percent < 0) "--%" else percent.toString() + "%")
    return views
}

private fun buildViews(context: Context, id: Int): RemoteViews {
    val snap = BatteryData.read(context, BatteryWidgetProvider.effectiveChargingOverride())
    val views = RemoteViews(context.packageName, R.layout.widget_layout)

    // 显示模式按【实测宽度】决定（不是格数估算，估算在部分桌面会误判把数字藏掉）
    val cols = WidgetMetrics.effectiveColumns(context, id)
    val widthDp = WidgetMetrics.widthDp(context, id).roundToInt()
    val compact = WidgetMetrics.isCompact(context, id)

    val bg = if (Prefs.background(context) == "transparent")
        R.drawable.widget_bg_transparent else R.drawable.widget_bg_capsule
    views.setInt(R.id.capsule_bg, "setBackgroundResource", bg)

    if (BatteryWidgetProvider.showChargeHint) {
        // 充电提示：亮帧/暗帧交替脉冲（全部是静态资源，不使用 alpha/setFloat 反射动作）
        views.setViewVisibility(R.id.normal_content, View.GONE)
        views.setViewVisibility(R.id.charge_hint, View.VISIBLE)
        val fast = BatteryWidgetProvider.hintFast
        val bright = BatteryWidgetProvider.hintBright
        views.setTextViewText(R.id.charge_sub, if (fast) "/// SUPER CHARGE" else "/// CHARGING")
        views.setTextViewText(R.id.charge_title, if (fast) "快充模式" else "充电中")
        views.setImageViewResource(
            R.id.charge_logo,
            if (bright) R.drawable.ic_bolt_glow else R.drawable.ic_bolt_glow_dim
        )
        views.setTextColor(
            R.id.charge_sub,
            ContextCompat.getColor(
                context,
                if (bright) R.color.accent_yellow_green else R.color.accent_yellow_green_dim
            )
        )
        views.setTextColor(
            R.id.charge_title,
            ContextCompat.getColor(
                context,
                if (bright) R.color.text_primary else R.color.text_primary_dim
            )
        )
    } else {
        views.setViewVisibility(R.id.normal_content, View.VISIBLE)
        views.setViewVisibility(R.id.charge_hint, View.GONE)
        // 「显示剩余容量」开关真正生效：关掉、或窄到放不下时才收起来
        views.setViewVisibility(
            R.id.capacity_group,
            if (Prefs.showRemaining(context) && !compact) View.VISIBLE else View.GONE
        )

        views.setTextViewText(R.id.txt_percent, snap.percent.toString() + "%")
        views.setImageViewResource(R.id.ring_image, arcRes(context, snap.percent, snap.charging))
        views.setTextViewText(R.id.txt_remaining, snap.remainingMah.toString())
        views.setTextViewText(R.id.txt_max, "/ " + snap.designMah.toString())

        val threshold = Prefs.lowThreshold(context)
        val isLow = snap.percent < threshold
        views.setTextColor(R.id.txt_percent,
            ContextCompat.getColor(context,
                if (isLow) R.color.accent_red else R.color.text_primary))
        views.setTextColor(R.id.txt_remaining,
            ContextCompat.getColor(context,
                if (isLow) R.color.accent_red else R.color.text_primary))
        views.setTextColor(R.id.txt_max,
            ContextCompat.getColor(context,
                if (isLow) R.color.accent_red else R.color.text_secondary))
    }

    val pi = PendingIntent.getActivity(
        context, 0,
        Intent(context, SettingsActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_root, pi)

    LogFile.i("Widget", "渲染 " + (if (BatteryWidgetProvider.showChargeHint)
        "提示帧(" + (if (BatteryWidgetProvider.hintBright) "亮" else "暗") +
                " 快充=" + BatteryWidgetProvider.hintFast + ")"
    else "正常显示") + " 宽=" + widthDp + "dp 格数=" + cols + (if (compact) "(紧凑)" else "") +
            " " + snap.percent + "% 充电中=" + snap.charging +
            " " + snap.remainingMah + "/" + snap.designMah + "mAh")

    return views
}

/**
 * 电量百分比 -> 预渲染进度环 PNG 资源。按最近 5% 取档；充电时用绿色环(arcg_)，否则黄绿环(arc)。
 * 取不到时回退到 100% 档，绝不返回 0（返回 0 会让桌面端 getDrawable(0) 抛异常，
 * 整个组件直接变成"加载窗口小工具时出现问题"）。
 */
private fun arcRes(context: Context, percent: Int, charging: Boolean): Int {
    val level = (((percent + 2) / 5) * 5).coerceIn(0, 100)
    val prefix = if (charging) "arcg_%02d" else "arc_%02d"
    val found = context.resources.getIdentifier(prefix.format(level), "drawable", context.packageName)
    if (found != 0) return found
    return if (charging) R.drawable.arcg_100 else R.drawable.arc_100
}
