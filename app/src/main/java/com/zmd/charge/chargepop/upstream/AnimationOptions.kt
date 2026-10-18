package com.zmd.charge.chargepop.upstream

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/**
 * Tunables mirrored from `zmd-charge`'s `AnimationOptions`
 * (`references/zmd-charge/Animations/HudAnimations.cs`).
 */
data class AnimationOptions(
    /** Total playback length in seconds, 3..10. */
    val durationSeconds: Double = 6.0,
    /** Overshoot amount 0..0.5, mapped to the BackOut spline's second control point Y. */
    val bounceStrength: Double = 0.275,
    /** Ripple peak opacity multiplier, 0..2. */
    val rippleIntensity: Double = 1.0,
    /** Ripple end scale multiplier, 0.5..1.5. */
    val rippleSpread: Double = 1.0,
) {
    val clampedDuration: Double get() = durationSeconds.coerceIn(3.0, 10.0)
    val clampedBounce: Double get() = bounceStrength.coerceIn(0.0, 0.5)
    val clampedRippleIntensity: Double get() = rippleIntensity.coerceIn(0.0, 2.0)
    val clampedRippleSpread: Double get() = rippleSpread.coerceIn(0.5, 1.5)

    companion object {
        val Default = AnimationOptions()
    }
}

/**
 * One animated scalar.
 *
 * Avalonia applies a `KeyFrame`'s `KeySpline` to the segment *ending* at that key frame, so the
 * spline is stored on the destination key. Android's [PathInterpolator] has identical semantics
 * (including overshoot, since only the X control points have to stay inside 0..1), which is what
 * makes a faithful port possible.
 */
internal class Track private constructor(private val keys: List<Key>) {

    internal class Key(val cue: Double, val value: Float, val spline: Interpolator?)

    fun at(cue: Double): Float {
        if (cue <= keys.first().cue) return keys.first().value
        val last = keys.last()
        if (cue >= last.cue) return last.value

        for (i in 1 until keys.size) {
            val to = keys[i]
            if (cue > to.cue) continue
            val from = keys[i - 1]
            val span = to.cue - from.cue
            if (span <= 0.0) return to.value
            val local = (cue - from.cue) / span
            val eased = to.spline?.getInterpolation(local.toFloat()) ?: local.toFloat()
            return from.value + (to.value - from.value) * eased
        }
        return last.value
    }

    internal class Builder {
        private val keys = mutableListOf<Key>()

        fun key(cue: Double, value: Float, spline: Interpolator? = null) = apply {
            keys.add(Key(cue, value, spline))
        }

        fun build(): Track {
            require(keys.isNotEmpty()) { "a track needs at least one key" }
            return Track(keys.toList())
        }
    }
}

/** Easing curves, copied verbatim from `HudAnimations.cs`. */
internal object Splines {

    val In = PathInterpolator(0.42f, 0f, 1f, 1f)
    val Out = PathInterpolator(0f, 0f, 0.58f, 1f)
    val InOut = PathInterpolator(0.42f, 0f, 0.58f, 1f)

    /** easeInOutCubic, used for every position change because it does not overshoot. */
    val Smooth = PathInterpolator(0.65f, 0f, 0.35f, 1f)

    /** Back-out curve; the overshoot is driven by the bounce setting. */
    fun backOut(options: AnimationOptions): Interpolator =
        PathInterpolator(0.175f, 0.885f, 0.32f, (1.0 + options.clampedBounce).toFloat())
}

internal fun track(block: Track.Builder.() -> Unit): Track = Track.Builder().apply(block).build()
