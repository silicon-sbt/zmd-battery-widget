package com.zmd.charge.chargepop.upstream

import kotlin.math.min

/**
 * Port of `zmd-charge`'s `HudAnimations` (`references/zmd-charge/Animations/HudAnimations.cs`).
 *
 * The original is a 6 s baseline timeline whose cues are fractions of the total duration. The
 * intro segment keeps a fixed absolute length and the hold segment stretches or shrinks with the
 * configured duration, which is what [mapCue] reproduces. `durationSeconds = 6` makes [mapCue] the
 * identity.
 *
 * Cues must be strictly increasing: out of order cues collapse a segment to a few milliseconds and
 * a 179 unit slide starts looking like a teleport.
 */
class HudTimeline(private val options: AnimationOptions = AnimationOptions.Default) {

    /** Total playback length in milliseconds, for whatever drives the frame loop. */
    val durationMillis: Long = (options.clampedDuration * 1000.0).toLong()

    private val backOut = Splines.backOut(options)

    private val rippleSpread = options.clampedRippleSpread.toFloat()
    private val rippleIntensity = options.clampedRippleIntensity.toFloat()

    // ---------------- full (plug) timeline ----------------

    private val pillCorner = track {
        key(0.0, PILL_RADIUS_A)
        key(c(T_APPEAR), PILL_RADIUS_A, Splines.In)
        key(c(T_EXPAND), PILL_RADIUS_B, Splines.InOut)
        key(c(T_HOLD_B), PILL_RADIUS_B, Splines.In)
        key(c(T_CONTRACT), PILL_RADIUS_A, Splines.InOut)
    }

    private val pillAlpha = track {
        key(0.0, 0f)
        key(c(T_START), 0f, Splines.In)
        key(c(T_APPEAR), 0f, Splines.In)
        key(c(T_PILL_OUT), 1f, backOut)
        key(c(T_HOLD_C), 1f, Splines.In)
    }

    private val pillScale = track {
        key(0.0, 0.6f)
        key(c(T_START), 0.6f, Splines.In)
        key(c(T_APPEAR), 0.6f, Splines.In)
        key(c(T_PILL_OUT), 1f, backOut)
        key(c(T_HOLD_C), 1f, Splines.In)
    }

    private val pillHeight = track {
        key(0.0, PILL_HEIGHT_A)
        key(c(T_APPEAR), PILL_HEIGHT_A, Splines.In)
        key(c(T_EXPAND), PILL_HEIGHT_B, backOut)
        key(c(T_HOLD_B), PILL_HEIGHT_B, Splines.In)
        key(c(T_CONTRACT), PILL_HEIGHT_A, Splines.InOut)
        key(c(T_HOLD_C), PILL_HEIGHT_A, Splines.In)
    }

    private val hostScale = track {
        key(0.0, 1f)
        key(c(T_HOLD_C), 1f, Splines.In)
        key(c(T_CLOSE), 0f, Splines.In)
    }

    private val boltAlpha = track {
        key(0.0, 0f)
        key(c(T_START), 0f, Splines.In)
        key(c(T_BOLT_POP), 1f, backOut)
        key(c(T_EXPAND), 1f, Splines.Out)
        key(c(T_MOVE), 1f, Splines.Smooth)
        key(c(T_HOLD_B), 1f, Splines.In)
        key(c(T_CONTRACT), 1f, Splines.Smooth)
        key(c(T_HOLD_C), 1f, Splines.In)
    }

    private val boltScale = track {
        key(0.0, 0.4f)
        key(c(T_START), 0.4f, Splines.In)
        key(c(T_BOLT_POP), 1.12f, backOut)
        key(c(T_EXPAND), 1f, Splines.Out)
        key(c(T_MOVE), 1f, Splines.Smooth)
        key(c(T_HOLD_B), 1f, Splines.In)
        key(c(T_CONTRACT), 1f, Splines.Smooth)
        key(c(T_HOLD_C), 1f, Splines.In)
    }

    private val boltTranslateX = track {
        key(0.0, 0f)
        key(c(T_START), 0f, Splines.In)
        key(c(T_BOLT_POP), 0f, backOut)
        key(c(T_EXPAND), 0f, Splines.Out)
        key(c(T_MOVE), ICON_OFFSET_B, Splines.Smooth)
        key(c(T_HOLD_B), ICON_OFFSET_B, Splines.In)
        key(c(T_CONTRACT), ICON_OFFSET_C, Splines.Smooth)
        key(c(T_HOLD_C), ICON_OFFSET_C, Splines.In)
    }

    private val circleAlpha = track {
        key(0.0, 0f)
        key(c(T_START), 0f, Splines.In)
        key(c(T_APPEAR), 1f, Splines.Out)
        key(c(T_HOLD_B), 1f, Splines.In)
        key(c(T_CONTRACT), 0f, Splines.InOut)
    }

    private val squareAlpha = track {
        key(0.0, 0f)
        key(c(T_HOLD_B), 0f, Splines.In)
        key(c(T_CONTRACT), 1f, Splines.InOut)
    }

    private val titleAlpha = track {
        key(0.0, 0f)
        key(c(T_MOVE), 0f, Splines.In)
        key(c(T_TITLE), 1f, Splines.Out)
        key(c(T_HOLD_B), 1f, Splines.In)
        key(c(T_CONTRACT), 0f, Splines.InOut)
    }

    private val numAlpha = track {
        key(0.0, 0f)
        key(c(T_CONTRACT), 0f, Splines.In)
        key(c(T_NUM_IN), 0f, Splines.In)
        key(c(T_NUM_READY), 1f, Splines.Out)
        key(c(T_HOLD_C), 1f, Splines.In)
    }

    private val rippleTranslateX = track {
        key(0.0, 0f)
        key(c(T_EXPAND), 0f, Splines.In)
        key(c(T_MOVE), ICON_OFFSET_B, Splines.Smooth)
        key(c(T_HOLD_B), ICON_OFFSET_B, Splines.In)
        key(c(T_CONTRACT), ICON_OFFSET_C, Splines.Smooth)
    }

    private val rippleTranslateY = track {
        key(0.0, 16f)
        key(c(T_EXPAND), 16f, Splines.In)
        key(c(T_EXPAND + 0.02), 16f, Splines.Out)
        key(c(T_HOLD_B), 0f, Splines.Out)
        key(c(T_CONTRACT), 0f, Splines.InOut)
    }

    private val rippleInnerAlpha = rippleAlphaTrack(0.50f)
    private val rippleInnerScale = rippleScaleTrack(1.5f)
    private val rippleMidAlpha = rippleAlphaTrack(0.50f)
    private val rippleMidScale = rippleScaleTrack(2.0f)
    private val rippleOuterAlpha = rippleAlphaTrack(0.60f)
    private val rippleOuterScale = rippleScaleTrack(2.5f)

    // ---------------- simplified (unplug) timeline ----------------

    private val simplePillAlpha = track {
        key(0.0, 0f)
        key(s(T_SIMPLE_APPEAR), 1f, Splines.Out)
        key(s(T_SIMPLE_HOLD), 1f, Splines.In)
    }

    private val simplePillScale = track {
        key(0.0, 0.6f)
        key(s(T_SIMPLE_APPEAR), 1f, Splines.Out)
        key(s(T_SIMPLE_HOLD), 1f, Splines.In)
    }

    private val simpleFadeIn = track {
        key(0.0, 0f)
        key(s(T_SIMPLE_APPEAR), 0f, Splines.In)
        key(s(T_SIMPLE_APPEAR + 0.03), 1f, Splines.Out)
        key(s(T_SIMPLE_HOLD), 1f, Splines.In)
    }

    private val simpleHostScale = track {
        key(0.0, 1f)
        key(s(T_SIMPLE_HOLD), 1f, Splines.In)
        key(s(T_SIMPLE_CLOSE), 0f, Splines.In)
    }

    // ---------------- evaluation ----------------

    /** [cue] is the fraction of the total playback, 0..1. */
    fun evaluateCharge(cue: Double): HudState {
        val t = cue.coerceIn(0.0, 1.0)
        return HudState(
            pillHeight = pillHeight.at(t),
            pillCorner = pillCorner.at(t),
            pillAlpha = pillAlpha.at(t),
            pillScale = pillScale.at(t),
            hostScale = hostScale.at(t),
            boltAlpha = boltAlpha.at(t),
            boltScale = boltScale.at(t),
            boltTranslateX = boltTranslateX.at(t),
            circleAlpha = circleAlpha.at(t),
            squareAlpha = squareAlpha.at(t),
            titleAlpha = titleAlpha.at(t),
            numAlpha = numAlpha.at(t),
            rippleTranslateX = rippleTranslateX.at(t),
            rippleTranslateY = rippleTranslateY.at(t),
            ripples = listOf(
                RippleState(rippleInnerAlpha.at(t), rippleInnerScale.at(t)),
                RippleState(rippleMidAlpha.at(t), rippleMidScale.at(t)),
                RippleState(rippleOuterAlpha.at(t), rippleOuterScale.at(t)),
            ),
        )
    }

    /** [cue] is the fraction of the total playback, 0..1. */
    fun evaluateUnplug(cue: Double): HudState {
        val t = cue.coerceIn(0.0, 1.0)
        val fade = simpleFadeIn.at(t)
        return HudState(
            pillHeight = PILL_HEIGHT_A,
            pillCorner = PILL_RADIUS_A,
            pillAlpha = simplePillAlpha.at(t),
            pillScale = simplePillScale.at(t),
            hostScale = simpleHostScale.at(t),
            boltAlpha = fade,
            boltScale = 1f,
            boltTranslateX = ICON_OFFSET_C,
            circleAlpha = 0f,
            squareAlpha = 1f,
            titleAlpha = 0f,
            numAlpha = fade,
            rippleTranslateX = 0f,
            rippleTranslateY = 16f,
            ripples = ZERO_RIPPLES,
        )
    }

    // ---------------- helpers ----------------

    private fun rippleAlphaTrack(peakOpacity: Float) = track {
        val peak = min(1f, peakOpacity * rippleIntensity)
        key(0.0, 0f)
        key(c(T_EXPAND), 0f, Splines.In)
        key(c(T_EXPAND + 0.02), peak, Splines.Out)
        key(c(T_HOLD_B), peak, Splines.Out)
        key(c(T_CONTRACT), 0f, Splines.InOut)
    }

    private fun rippleScaleTrack(endScale: Float) = track {
        val target = endScale * rippleSpread
        key(0.0, 0f)
        key(c(T_EXPAND), 0f, Splines.In)
        key(c(T_EXPAND + 0.02), 0.05f, Splines.Out)
        key(c(T_HOLD_B), target, Splines.Out)
        key(c(T_CONTRACT), target, Splines.InOut)
    }

    /** Baseline cue to real cue. Identity when `durationSeconds == 6`. */
    internal fun mapCue(cue: Double): Double = HudCues.mapCue(options.clampedDuration, cue)

    internal fun mapCueSimple(cue: Double): Double =
        HudCues.mapCueSimple(options.clampedDuration, cue)

    private fun c(cue: Double) = mapCue(cue)

    private fun s(cue: Double) = mapCueSimple(cue)

    private companion object {
        const val T_START = HudCues.T_START
        const val T_APPEAR = HudCues.T_APPEAR
        const val T_PILL_OUT = HudCues.T_PILL_OUT
        const val T_BOLT_POP = HudCues.T_BOLT_POP
        const val T_EXPAND = HudCues.T_EXPAND
        const val T_MOVE = HudCues.T_MOVE
        const val T_TITLE = HudCues.T_TITLE
        const val T_HOLD_B = HudCues.T_HOLD_B
        const val T_CONTRACT = HudCues.T_CONTRACT
        const val T_HOLD_C = HudCues.T_HOLD_C
        const val T_CLOSE = HudCues.T_CLOSE
        const val T_NUM_IN = HudCues.T_NUM_IN
        const val T_NUM_READY = HudCues.T_NUM_READY

        const val T_SIMPLE_APPEAR = HudCues.T_SIMPLE_APPEAR
        const val T_SIMPLE_HOLD = HudCues.T_SIMPLE_HOLD
        const val T_SIMPLE_CLOSE = HudCues.T_SIMPLE_CLOSE

        const val PILL_RADIUS_A = 30f
        const val PILL_RADIUS_B = 18f
        const val PILL_HEIGHT_A = 60f
        const val PILL_HEIGHT_B = 90f
        const val ICON_OFFSET_B = -179f
        const val ICON_OFFSET_C = -245f

        val ZERO_RIPPLES = listOf(
            RippleState(0f, 0f),
            RippleState(0f, 0f),
            RippleState(0f, 0f),
        )
    }
}
