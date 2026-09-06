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

    fun capacityFallback(c: Context): Int =
        PreferenceManager.getDefaultSharedPreferences(c).getString("capacity_fallback", "5500")
            ?.toIntOrNull() ?: 5500
}
