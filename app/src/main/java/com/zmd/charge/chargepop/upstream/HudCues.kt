package com.zmd.charge.chargepop.upstream

/**
 * Baseline cue table and the cue remapping, ported from `zmd-charge`'s `HudAnimations`.
 *
 * Deliberately free of Android imports so it can be unit tested on the JVM against the vectors in
 * `references/zmd-charge/tests/EndfieldCharge.Tests/HudAnimationsTests.cs`.
 */
object HudCues {

    const val BASELINE_SECONDS = 6.0
    const val INTRO_END_CUE = 0.42

    const val T_START = 0.04
    const val T_APPEAR = 0.07
    const val T_PILL_OUT = 0.09
    const val T_BOLT_POP = 0.10
    const val T_EXPAND = 0.12
    const val T_MOVE = 0.20
    const val T_TITLE = 0.25
    const val T_HOLD_B = 0.30
    const val T_CONTRACT = 0.36
    const val T_HOLD_C = 0.86
    const val T_CLOSE = 0.89
    const val T_NUM_IN = 0.38
    const val T_NUM_READY = 0.42

    const val SIMPLE_BASELINE_SECONDS = 5.0
    const val SIMPLE_INTRO_END_CUE = 0.08
    const val T_SIMPLE_APPEAR = 0.05
    const val T_SIMPLE_HOLD = 0.75
    const val T_SIMPLE_CLOSE = 0.80

    /**
     * Every baseline cue of the full timeline, ascending.
     *
     * Out of order cues collapse a segment to a few milliseconds and a 179 unit slide starts
     * looking like a teleport, so [HudCuesTest] guards this invariant.
     */
    val BASELINE_CUES = doubleArrayOf(
        0.0, T_START, T_APPEAR, T_PILL_OUT, T_BOLT_POP, T_EXPAND, T_EXPAND + 0.02,
        T_MOVE, T_TITLE, T_HOLD_B, T_CONTRACT, T_NUM_IN, T_NUM_READY, T_HOLD_C, T_CLOSE,
    )

    val SIMPLE_BASELINE_CUES = doubleArrayOf(
        0.0, T_SIMPLE_APPEAR, T_SIMPLE_APPEAR + 0.03, T_SIMPLE_HOLD, T_SIMPLE_CLOSE,
    )

    /**
     * Baseline cue to real cue for the full timeline.
     *
     * The intro segment (0 -> [INTRO_END_CUE]) keeps the baseline absolute length of
     * `0.42 * 6 = 2.52 s`; only the hold segment stretches or shrinks with the configured
     * duration. Otherwise asking for a longer display would also slow the entrance down.
     * `durationSeconds == 6` makes this the identity.
     */
    fun mapCue(durationSeconds: Double, cue: Double): Double {
        val duration = durationSeconds.coerceIn(3.0, 10.0)
        val introFraction = INTRO_END_CUE * BASELINE_SECONDS / duration
        if (cue <= INTRO_END_CUE) return cue / INTRO_END_CUE * introFraction
        return introFraction + (cue - INTRO_END_CUE) / (1.0 - INTRO_END_CUE) * (1.0 - introFraction)
    }

    /** Same contract as [mapCue] for the simplified unplug timeline. */
    fun mapCueSimple(durationSeconds: Double, cue: Double): Double {
        val duration = durationSeconds.coerceIn(3.0, 10.0)
        val introFraction = SIMPLE_INTRO_END_CUE * SIMPLE_BASELINE_SECONDS / duration
        if (cue <= SIMPLE_INTRO_END_CUE) return cue / SIMPLE_INTRO_END_CUE * introFraction
        return introFraction +
            (cue - SIMPLE_INTRO_END_CUE) / (1.0 - SIMPLE_INTRO_END_CUE) * (1.0 - introFraction)
    }
}
