package com.zmd.charge.chargepop.upstream

/** One expanding ring, already evaluated. */
data class RippleState(val alpha: Float, val scale: Float)

/**
 * Everything the renderer needs for one instant of the animation.
 *
 * Units are `zmd-charge` design units (the 560x90 logical pixel canvas the Avalonia window uses);
 * the renderer multiplies by the UI scale and display density.
 */
data class HudState(
    val pillHeight: Float,
    val pillCorner: Float,
    val pillAlpha: Float,
    val pillScale: Float,
    val hostScale: Float,
    val boltAlpha: Float,
    val boltScale: Float,
    val boltTranslateX: Float,
    val circleAlpha: Float,
    val squareAlpha: Float,
    val titleAlpha: Float,
    val numAlpha: Float,
    val rippleTranslateX: Float,
    val rippleTranslateY: Float,
    val ripples: List<RippleState>,
)
