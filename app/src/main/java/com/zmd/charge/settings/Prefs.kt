package com.zmd.charge.settings

import android.content.Context
import androidx.preference.PreferenceManager

object Prefs {
    fun showRemaining(c: Context) =
        PreferenceManager.getDefaultSharedPreferences(c).getBoolean("show_remaining", true)

    fun background(c: Context): String =
        PreferenceManager.getDefaultSharedPreferences(c).getString("background", "solid") ?: "solid"

    fun lowThreshold(c: Context): Int =
        PreferenceManager.getDefaultSharedPreferences(c).getInt("low_battery_threshold", 20)

    /**
     * 组件宽度覆盖值：0=自动（按桌面实测），>0=用户指定的格数。
     * 存的是 "auto" / "col4" 这类非纯数字字符串（纯数字会被 AAPT2 编译成 int，
     * 导致 ListPreference 取到 null 直接崩溃），这里同时兼容可能存在的纯数字历史值。
     */
    fun widgetColumns(c: Context): Int {
        val v = PreferenceManager.getDefaultSharedPreferences(c)
            .getString("widget_columns", "auto") ?: "auto"
        val digits = if (v.startsWith("col")) v.substring(3) else v
        return digits.toIntOrNull()?.coerceIn(0, 8) ?: 0
    }

    fun capacityFallback(c: Context): Int =
        PreferenceManager.getDefaultSharedPreferences(c).getString("capacity_fallback", "5500")
            ?.toIntOrNull() ?: 5500
}
