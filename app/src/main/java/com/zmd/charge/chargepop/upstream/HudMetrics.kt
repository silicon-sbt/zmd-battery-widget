package com.zmd.charge.chargepop.upstream

import android.content.Context
import android.view.WindowInsets
import android.view.WindowManager

/**
 * Screen geometry the HUD has to respect.
 *
 * The reference sits 4 logical pixels below the top of the screen, but a phone has a punch hole
 * there: on the K60 the display cutout is 120 px (about 34 dp) tall and centred, which is exactly
 * where the bolt sits in states A and B. The HUD is therefore placed below the cutout instead of
 * copying the desktop offset.
 *
 * The preview screen and the SystemUI overlay both read the offset from here, so what the slider
 * shows is what the overlay does.
 */
object HudMetrics {

    /** Distance from the top of the screen to the top of the HUD window, in pixels. */
    fun topMarginPx(context: Context, gapDp: Int): Int =
        topInsetPx(context) + (gapDp * context.resources.displayMetrics.density).toInt()

    /** Same value in dp, for UI that talks in dp. */
    fun topMarginDp(context: Context, gapDp: Int): Int =
        (topInsetPx(context) / context.resources.displayMetrics.density).toInt() + gapDp

    /** Falls back to the framework's status bar height when the cutout cannot be read. */
    fun topInsetPx(context: Context): Int {
        val fromWindow = runCatching {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val insets = windowManager.currentWindowMetrics.windowInsets
            val cutout = insets.displayCutout?.safeInsetTop ?: 0
            val statusBar = insets.getInsets(WindowInsets.Type.statusBars()).top
            maxOf(cutout, statusBar)
        }.getOrDefault(0)

        if (fromWindow > 0) return fromWindow

        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }
}
