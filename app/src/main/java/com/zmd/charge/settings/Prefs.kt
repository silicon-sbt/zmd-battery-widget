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

    /** 组件宽度覆盖值：0=自动（按桌面实测），>0=用户指定的格数。 */
    fun widgetColumns(c: Context): Int =
        PreferenceManager.getDefaultSharedPreferences(c).getString("widget_columns", "auto")
            ?.toIntOrNull() ?: 0

    fun capacityFallback(c: Context): Int =
        PreferenceManager.getDefaultSharedPreferences(c).getString("capacity_fallback", "5500")
            ?.toIntOrNull() ?: 5500
}
