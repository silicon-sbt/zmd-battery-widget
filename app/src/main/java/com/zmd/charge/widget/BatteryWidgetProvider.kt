package com.zmd.charge.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.zmd.charge.R
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

        /** 是否正在显示"插入充电提示动画"（仅插入后几秒内为 true）。 */
        @JvmStatic
        var showChargeHint: Boolean = false
        /** true=快充，false=普通充电。 */
        @JvmStatic
        var hintFast: Boolean = false
        /** 充电提示的透明度(0..1)，用于淡入/淡出过渡动画。 */
        @JvmStatic
        var hintAlpha: Float = 1f

        /** 刷新所有已放置的组件实例（设置页变更/电量变化/闹钟触发时调用）。 */
        fun refresh(context: Context, chargingOverride: Boolean? = null) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, BatteryWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            ids.forEach { renderWidget(context, mgr, it, chargingOverride) }
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

private fun renderWidget(context: Context, mgr: AppWidgetManager, id: Int, chargingOverride: Boolean? = null) {
    val snap = BatteryData.read(context, chargingOverride)
    val views = RemoteViews(context.packageName, R.layout.widget_layout)

    val bg = if (Prefs.background(context) == "transparent")
        R.drawable.widget_bg_transparent else R.drawable.widget_bg_capsule
    views.setInt(R.id.capsule_bg, "setBackgroundResource", bg)

    if (BatteryWidgetProvider.showChargeHint) {
        views.setViewVisibility(R.id.normal_content, View.GONE)
        views.setViewVisibility(R.id.charge_hint, View.VISIBLE)
        val fast = BatteryWidgetProvider.hintFast
        views.setTextViewText(R.id.charge_sub, if (fast) "/// SUPER CHARGE" else "/// CHARGING")
        views.setTextViewText(R.id.charge_title, if (fast) "快充模式" else "充电中")
        views.setFloat(R.id.charge_hint, "setAlpha", BatteryWidgetProvider.hintAlpha)
    } else {
        views.setViewVisibility(R.id.normal_content, View.VISIBLE)
        views.setViewVisibility(R.id.charge_hint, View.GONE)

        views.setTextViewText(R.id.txt_percent, snap.percent.toString() + "%")
        views.setImageViewResource(R.id.ring_image, arcRes(context, snap.percent, snap.charging))
        views.setTextViewText(R.id.txt_remaining, snap.remainingMah.toString())
        views.setTextViewText(R.id.txt_max, "/ " + snap.designMah.toString())

        val threshold = Prefs.lowThreshold(context)
        val isLow = snap.percent < threshold
        views.setTextColor(R.id.txt_percent,
            if (isLow) ContextCompat.getColor(context, R.color.accent_red)
            else ContextCompat.getColor(context, R.color.text_primary))
        views.setTextColor(R.id.txt_remaining,
            if (isLow) ContextCompat.getColor(context, R.color.accent_red)
            else ContextCompat.getColor(context, R.color.text_primary))
        views.setTextColor(R.id.txt_max,
            if (isLow) ContextCompat.getColor(context, R.color.accent_red)
            else ContextCompat.getColor(context, R.color.text_secondary))
    }

    val pi = PendingIntent.getActivity(
        context, 0,
        Intent(context, SettingsActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_root, pi)

    mgr.updateAppWidget(id, views)
}

/** 电量百分比 -> 预渲染进度环 PNG 资源。按最近 5% 取档；充电时用绿色环(arcg_)，否则黄绿环(arc_)。 */
private fun arcRes(context: Context, percent: Int, charging: Boolean): Int {
    val level = (((percent + 2) / 5) * 5).coerceIn(0, 100)
    val prefix = if (charging) "arcg_%02d" else "arc_%02d"
    return context.resources.getIdentifier(prefix.format(level), "drawable", context.packageName)
}
