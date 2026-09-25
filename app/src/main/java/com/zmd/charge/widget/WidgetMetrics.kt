package com.zmd.charge.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.SizeF
import com.zmd.charge.settings.Prefs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 桌面组件尺寸测量。
 *
 * 现实约束（Android 平台限制，非本应用问题）：
 * 1. 没有公开 API 能读到"桌面横排有几格"；
 * 2. 也没有任何 API 能让 App 程序化改变自己组件的格数——格数由桌面（Launcher）决定，
 *    用户只能长按组件拖动边缘来改，App 只能在【首次添加】时通过 appwidget-provider.xml
 *    的 minWidth / targetCellWidth 给出默认值。
 *
 * 因此这里的做法是：
 * - 默认配置让首次添加直接铺满整行（见 appwidget_provider.xml）；
 * - 运行时尽量"问"桌面要到实际格数：Android 12+ 会给出一份"可用尺寸列表"，
 *   最小尺寸=1 格、最大尺寸=整行，两者相除即格数；
 * - 拿不到就用宽度 dp 粗估；再拿不到就由用户在设置里手填。
 */
object WidgetMetrics {

    /** 手机桌面上每格约 74~80dp（含格间距），仅作最后兜底。 */
    private const val DP_PER_CELL = 80f

    /** 取任意一个已放置的组件 id；没有则返回 -1。 */
    fun anyWidgetId(context: Context): Int = try {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, BatteryWidgetProvider::class.java))
        if (ids.isEmpty()) -1 else ids[0]
    } catch (t: Throwable) { -1 }

    /** 组件当前宽度（dp，取横竖屏里较大的那个）。 */
    fun widthDp(context: Context, id: Int): Float {
        if (id < 0) return 0f
        return try {
            val opt = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
            val w = max(
                opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
                opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
            )
            w.toFloat()
        } catch (t: Throwable) { 0f }
    }

    /**
     * Android 12+ 精算：OPTION_APPWIDGET_SIZES 列出了该桌面下这个组件所有可拖到的尺寸，
     * 最小=1 格、最大=整行，比值就是横排格数。
     */
    fun columnsFromSizes(context: Context, id: Int): Int {
        if (id < 0) return 0
        return try {
            val opt = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
            val raw = opt.get(AppWidgetManager.OPTION_APPWIDGET_SIZES) ?: return 0
            val widths = (raw as? List<*>)
                ?.mapNotNull { (it as? SizeF)?.width }
                ?.filter { it > 0f }
                ?: return 0
            if (widths.size < 2) return 0
            (widths.max() / widths.min()).roundToInt().coerceIn(1, 8)
        } catch (t: Throwable) { 0 }
    }

    /** 按宽度粗估格数；读不到返回 0。 */
    fun columnsByWidth(context: Context, id: Int): Int {
        val w = widthDp(context, id)
        if (w <= 0f) return 0
        return (w / DP_PER_CELL).roundToInt().coerceIn(1, 8)
    }

    /** 最终生效格数：用户设置优先 -> 尺寸列表精算 -> 宽度粗估 -> 0（未知）。 */
    fun effectiveColumns(context: Context, id: Int): Int {
        val forced = Prefs.widgetColumns(context)
        if (forced > 0) return forced
        val precise = columnsFromSizes(context, id)
        if (precise > 0) return precise
        return columnsByWidth(context, id)
    }

    /** 给设置页看的实测描述。 */
    fun describe(context: Context): String {
        val id = anyWidgetId(context)
        if (id < 0) return "尚未在桌面添加组件"
        val w = widthDp(context, id).roundToInt()
        val precise = columnsFromSizes(context, id)
        val byWidth = columnsByWidth(context, id)
        val cols = if (precise > 0) precise else byWidth
        val how = if (precise > 0) "系统尺寸列表" else "宽度估算"
        return "桌面实测：" + cols + " 格 · 宽 " + w + "dp（" + how + "）"
    }
}
