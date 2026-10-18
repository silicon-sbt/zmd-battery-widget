package com.zmd.charge.chargepop.upstream

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Canvas renderer for the Endfield charge HUD.
 *
 * Ported from `zmd-charge`'s `Views/HudWindow.axaml` + `Views/HudWindow.axaml.cs`. The Avalonia
 * visual tree is a fixed 560x90 canvas whose parts are moved by the timeline, so this class keeps
 * the same design unit geometry and converts once at draw time through [unit].
 *
 * Structure, mirroring the original tree:
 *
 * ```
 * hostScale                     (ScaleHost, whole HUD shrinks away at the end)
 *   pill                        (rounded rect, clips the ripples, own scale + alpha)
 *     ripples                   (three rings, offset by rippleTranslateX/Y)
 *   bolt                        (circle form cross fades into square form)
 *   title                       (tagline + title, centred on the pill)
 *   numbers                     (Wh / percent / badge row)
 * ```
 */
class EndfieldHudView(
    context: Context,
    /**
     * Assets of the *module* package. Inside SystemUI the view is built with the SystemUI context,
     * whose assets do not contain our bundled font, so the controller hands over the module's own
     * AssetManager. Defaults to the ambient context, which is correct inside the module app.
     */
    assets: AssetManager = context.assets,
) : View(context) {

    /** Read through to the resources: density can change with the configuration. */
    private val density: Float get() = resources.displayMetrics.density

    /**
     * Fraction of the screen width the pill may occupy.
     *
     * `zmd-charge` uses 560x90 logical pixels with a 0.8 global scale, which is 448 dp wide and
     * does not fit a 411 dp phone, so the scale is capped by the screen width instead; every
     * length, radius, font size and stroke width scales together. Settable so the settings screen
     * can change it live.
     */
    var widthRatio: Float = HudSettings.DEFAULT_WIDTH_RATIO
        set(value) {
            val clamped = value.coerceIn(HudSettings.MIN_WIDTH_RATIO, HudSettings.MAX_WIDTH_RATIO)
            if (field == clamped) return
            field = clamped
            recomputeScale()
            requestLayout()
            invalidate()
        }

    /** [HudSettings.LANGUAGE_AUTO], [HudSettings.LANGUAGE_ZH] or [HudSettings.LANGUAGE_EN]. */
    var language: String = HudSettings.LANGUAGE_AUTO
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Design unit to pixel factor. */
    private var unit: Float = 0f

    private fun recomputeScale() {
        val widthDp = resources.displayMetrics.widthPixels / density
        val scale = min(GLOBAL_SCALE, widthDp * widthRatio / PILL_WIDTH)
        unit = scale * density
    }

    private var state: HudState? = null
    private var snapshot: BatterySnapshot? = null

    // ---------------- paints ----------------

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = TEXT_PRIMARY
    }

    private val medium = HudTypefaces.medium(assets)
    private val bold = HudTypefaces.bold(assets)

    init {
        recomputeScale()
    }

    // ---------------- reusable geometry ----------------

    private val pillPath = Path()
    private val rect = RectF()
    private val boltPath = Path()
    private val arcPath = Path()

    fun setSnapshot(value: BatterySnapshot?) {
        snapshot = value
        invalidate()
    }

    fun render(value: HudState) {
        state = value
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            (PILL_WIDTH * unit).toInt(),
            (PILL_MAX_HEIGHT * unit).toInt(),
        )
    }

    /** Rotation or a resolution change moves every design unit, so the scale is recomputed. */
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        recomputeScale()
        requestLayout()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val st = state ?: return
        if (st.hostScale <= 0.001f) return

        val cx = width / 2f
        val cy = height / 2f

        canvas.save()
        canvas.scale(st.hostScale, st.hostScale, cx, cy)

        drawPill(canvas, st, cx, cy)
        drawRipples(canvas, st, cx, cy)
        drawBolt(canvas, st, cx, cy)
        drawTitle(canvas, st, cx, cy)
        drawNumbers(canvas, st, cx, cy)

        canvas.restore()
    }

    // ---------------- pill + ripples ----------------

    private fun drawPill(canvas: Canvas, st: HudState, cx: Float, cy: Float) {
        if (st.pillAlpha <= 0.001f) return

        val halfW = PILL_WIDTH * unit / 2f
        val halfH = st.pillHeight * unit / 2f
        val radius = st.pillCorner * unit

        canvas.save()
        canvas.scale(st.pillScale, st.pillScale, cx, cy)

        rect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        pillPath.reset()
        pillPath.addRoundRect(rect, radius, radius, Path.Direction.CW)

        fillPaint.color = PILL_COLOR
        fillPaint.alpha = alpha(st.pillAlpha)
        canvas.drawPath(pillPath, fillPaint)
        canvas.restore()
    }

    /**
     * The ripples live inside the pill and are clipped by its rounded rectangle, exactly like
     * `Pill.ClipToBounds` in the original.
     */
    private fun drawRipples(canvas: Canvas, st: HudState, cx: Float, cy: Float) {
        if (st.pillAlpha <= 0.001f) return

        val halfW = PILL_WIDTH * unit / 2f
        val halfH = st.pillHeight * unit / 2f
        val radius = st.pillCorner * unit

        canvas.save()
        canvas.scale(st.pillScale, st.pillScale, cx, cy)
        rect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        pillPath.reset()
        pillPath.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(pillPath)

        val rippleCx = cx + st.rippleTranslateX * unit
        val rippleCy = cy + st.rippleTranslateY * unit

        drawRipple(canvas, st.ripples[0], rippleCx, rippleCy, RIPPLE_INNER_D, 0f, st.pillAlpha)
        drawRipple(canvas, st.ripples[1], rippleCx, rippleCy, RIPPLE_MID_D, 5.0f, st.pillAlpha)
        drawRipple(canvas, st.ripples[2], rippleCx, rippleCy, RIPPLE_OUTER_D, 3.5f, st.pillAlpha)

        canvas.restore()
    }

    private fun drawRipple(
        canvas: Canvas,
        ripple: RippleState,
        cx: Float,
        cy: Float,
        diameter: Float,
        strokeWidth: Float,
        pillAlpha: Float,
    ) {
        val alphaValue = ripple.alpha * pillAlpha
        if (alphaValue <= 0.001f || ripple.scale <= 0.001f) return

        val radius = diameter * unit / 2f * ripple.scale
        if (strokeWidth <= 0f) {
            fillPaint.color = RIPPLE_COLOR
            fillPaint.alpha = alpha(alphaValue)
            canvas.drawCircle(cx, cy, radius, fillPaint)
        } else {
            strokePaint.color = RIPPLE_COLOR
            strokePaint.alpha = alpha(alphaValue)
            strokePaint.strokeWidth = strokeWidth * unit
            canvas.drawCircle(cx, cy, radius, strokePaint)
        }
    }

    // ---------------- bolt ----------------

    private fun drawBolt(canvas: Canvas, st: HudState, cx: Float, cy: Float) {
        if (st.boltAlpha <= 0.001f) return

        val boltCx = cx + st.boltTranslateX * unit

        canvas.save()
        canvas.scale(st.boltScale, st.boltScale, boltCx, cy)

        if (st.circleAlpha > 0.001f) {
            fillPaint.color = ICON_PLATE_COLOR
            fillPaint.alpha = alpha(st.circleAlpha * st.boltAlpha)
            canvas.drawCircle(boltCx, cy, BOLT_CIRCLE * unit / 2f, fillPaint)

            buildBoltPath(boltCx, cy, BOLT_CIRCLE_ICON * unit)
            fillPaint.color = BOLT_DARK_COLOR
            fillPaint.alpha = alpha(st.circleAlpha * st.boltAlpha)
            canvas.drawPath(boltPath, fillPaint)
        }

        if (st.squareAlpha > 0.001f) {
            val half = BOLT_SQUARE * unit / 2f
            rect.set(boltCx - half, cy - half, boltCx + half, cy + half)
            fillPaint.color = ICON_PLATE_COLOR
            fillPaint.alpha = alpha(st.squareAlpha * st.boltAlpha)
            canvas.drawRoundRect(rect, BOLT_SQUARE_RADIUS * unit, BOLT_SQUARE_RADIUS * unit, fillPaint)

            buildBoltPath(boltCx, cy, BOLT_SQUARE_ICON * unit)
            fillPaint.color = BOLT_DARK_COLOR
            fillPaint.alpha = alpha(st.squareAlpha * st.boltAlpha)
            canvas.drawPath(boltPath, fillPaint)
        }

        canvas.restore()
    }

    /**
     * Reproduces `PathIcon`'s default `Stretch=Uniform`: the bolt geometry's own bounds (16x20 on
     * its 24x24 grid) are scaled uniformly into [box] and centred, not stretched to fill it.
     */
    private fun buildBoltPath(cx: Float, cy: Float, box: Float) {
        val factor = box / max(BOLT_BOUNDS_W, BOLT_BOUNDS_H)
        val originX = cx - BOLT_BOUNDS_W * factor / 2f - BOLT_MIN_X * factor
        val originY = cy - BOLT_BOUNDS_H * factor / 2f - BOLT_MIN_Y * factor

        fun x(v: Float) = originX + v * factor
        fun y(v: Float) = originY + v * factor

        boltPath.reset()
        boltPath.moveTo(x(13f), y(2f))
        boltPath.lineTo(x(4f), y(13f))
        boltPath.lineTo(x(12f), y(13f))
        boltPath.lineTo(x(18f), y(2f))
        boltPath.close()
        boltPath.moveTo(x(13f), y(11f))
        boltPath.lineTo(x(20f), y(11f))
        boltPath.lineTo(x(13f), y(22f))
        boltPath.lineTo(x(4f), y(22f))
        boltPath.close()
    }

    // ---------------- title ----------------

    private fun drawTitle(canvas: Canvas, st: HudState, cx: Float, cy: Float) {
        if (st.titleAlpha <= 0.001f) return

        textPaint.typeface = medium
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.letterSpacing = TAGLINE_LETTER_SPACING / TAGLINE_SIZE

        val taglineSize = TAGLINE_SIZE * unit
        val titleSize = TITLE_SIZE * unit

        // StackPanel with Spacing=2, vertically centred on the pill.
        val taglineMetrics = metrics(medium, taglineSize)
        val titleMetrics = metrics(bold, titleSize)
        val totalHeight = taglineMetrics.height + TITLE_SPACING * unit + titleMetrics.height
        var top = cy - totalHeight / 2f

        textPaint.textSize = taglineSize
        textPaint.typeface = medium
        textPaint.color = TEXT_PRIMARY
        textPaint.alpha = alpha(st.titleAlpha * TEXT_TERTIARY_ALPHA)
        canvas.drawText(HudStrings.tagline(language), cx, top - taglineMetrics.ascent, textPaint)
        top += taglineMetrics.height + TITLE_SPACING * unit

        textPaint.textSize = titleSize
        textPaint.typeface = bold
        textPaint.letterSpacing = TITLE_LETTER_SPACING / TITLE_SIZE
        textPaint.alpha = alpha(st.titleAlpha)
        canvas.drawText(HudStrings.title(language), cx, top - titleMetrics.ascent, textPaint)

        textPaint.letterSpacing = 0f
    }

    // ---------------- numbers + badge ----------------

    private fun drawNumbers(canvas: Canvas, st: HudState, cx: Float, cy: Float) {
        if (st.numAlpha <= 0.001f) return

        val left = cx - PILL_WIDTH * unit / 2f
        val pct = snapshot?.level ?: 0

        // Left side: bolt slot is drawn by drawBolt, so start after column 0 + 1 + 2.
        var x = left + (COL_PAD_LEFT + COL_BOLT_SLOT + COL_GAP) * unit

        val whValue = snapshot?.remainingMah?.let { formatValue(it) } ?: "--"
        val whMax = snapshot?.fullMah?.let { "/" + formatValue(it) } ?: ""

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = medium
        textPaint.letterSpacing = 0f

        val whSize = WH_VALUE_SIZE * unit
        val whMaxSize = WH_MAX_SIZE * unit
        val whValueWidth = measure(whValue, medium, whSize)

        textPaint.textSize = whSize
        textPaint.alpha = alpha(st.numAlpha)
        canvas.drawText(whValue, x, centeredBaseline(medium, whSize, cy), textPaint)

        if (whMax.isNotEmpty()) {
            x += whValueWidth + WH_SPACING * unit
            textPaint.textSize = whMaxSize
            textPaint.alpha = alpha(st.numAlpha * TEXT_SECONDARY_ALPHA)
            canvas.drawText(whMax, x, centeredBaseline(medium, whMaxSize, cy), textPaint)
        }

        // Right side: laid out from the right edge so the badge stays pinned.
        val right = cx + PILL_WIDTH * unit / 2f
        var cursor = right - COL_PAD_RIGHT * unit

        val badgeCx = cursor - BADGE_DIAMETER * unit / 2f
        cursor -= BADGE_DIAMETER * unit
        cursor -= COL_GAP * unit

        val pctText = pct.toString()
        val pctSize = PCT_VALUE_SIZE * unit
        val signSize = PCT_SIGN_SIZE * unit
        val pctWidth = measure(pctText, medium, pctSize)
        val signWidth = measure("%", medium, signSize)
        val pctLeft = cursor - (pctWidth + PCT_SPACING * unit + signWidth)

        textPaint.textSize = pctSize
        textPaint.alpha = alpha(st.numAlpha)
        canvas.drawText(pctText, pctLeft, centeredBaseline(medium, pctSize, cy), textPaint)

        textPaint.textSize = signSize
        textPaint.alpha = alpha(st.numAlpha * TEXT_SECONDARY_ALPHA)
        canvas.drawText("%", pctLeft + pctWidth + PCT_SPACING * unit, centeredBaseline(medium, signSize, cy), textPaint)

        drawBadge(canvas, st, badgeCx, cy, pct)
    }

    private fun drawBadge(canvas: Canvas, st: HudState, cx: Float, cy: Float, percent: Int) {
        val accent = if (percent < LOW_BATTERY_THRESHOLD) BADGE_LOW_COLOR else ACCENT_COLOR
        val radius = BADGE_DIAMETER * unit / 2f

        fillPaint.color = BADGE_DARK_COLOR
        fillPaint.alpha = alpha(st.numAlpha)
        canvas.drawCircle(cx, cy, radius, fillPaint)

        // Progress arc: 12 o'clock, clockwise, length = charge level.
        var sweep = 360f * (percent.coerceIn(0, 100) / 100f)
        sweep = sweep.coerceIn(0.5f, 359.5f)
        val arcRadius = (BADGE_DIAMETER - BADGE_ARC_STROKE) * unit / 2f
        arcPath.reset()
        arcPath.addArc(
            cx - arcRadius,
            cy - arcRadius,
            cx + arcRadius,
            cy + arcRadius,
            -90f,
            sweep,
        )
        strokePaint.color = accent
        strokePaint.alpha = alpha(st.numAlpha)
        strokePaint.strokeWidth = BADGE_ARC_STROKE * unit
        strokePaint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(arcPath, strokePaint)
        strokePaint.strokeCap = Paint.Cap.BUTT

        // Device glyph.
        //
        // The reference draws a laptop (a screen outline plus a wider base). A phone is the honest
        // analogue on Android, and it keeps the same two part structure: body outline plus a filled
        // home indicator, which is the phone equivalent of the laptop's base.
        val bodyW = PHONE_WIDTH * unit
        val bodyH = PHONE_HEIGHT * unit
        rect.set(cx - bodyW / 2f, cy - bodyH / 2f, cx + bodyW / 2f, cy + bodyH / 2f)
        strokePaint.color = accent
        strokePaint.alpha = alpha(st.numAlpha)
        strokePaint.strokeWidth = PHONE_STROKE * unit
        canvas.drawRoundRect(rect, PHONE_RADIUS * unit, PHONE_RADIUS * unit, strokePaint)

        val barW = PHONE_HOME_WIDTH * unit
        val barH = PHONE_HOME_HEIGHT * unit
        val barCy = cy + bodyH / 2f - PHONE_HOME_INSET * unit
        rect.set(cx - barW / 2f, barCy - barH / 2f, cx + barW / 2f, barCy + barH / 2f)
        fillPaint.color = accent
        fillPaint.alpha = alpha(st.numAlpha)
        canvas.drawRoundRect(rect, barH / 2f, barH / 2f, fillPaint)

        // Electrode: Margin="0,-14,0,0" on a centred 9x3 block.
        val electrodeW = ELECTRODE_WIDTH * unit
        val electrodeH = ELECTRODE_HEIGHT * unit
        val electrodeCy = cy - ELECTRODE_MARGIN_TOP * unit
        rect.set(
            cx - electrodeW / 2f,
            electrodeCy - electrodeH / 2f,
            cx + electrodeW / 2f,
            electrodeCy + electrodeH / 2f,
        )
        fillPaint.color = accent
        fillPaint.alpha = alpha(st.numAlpha)
        canvas.drawRoundRect(
            rect,
            ELECTRODE_RADIUS * unit,
            ELECTRODE_RADIUS * unit,
            fillPaint,
        )
    }

    // ---------------- text helpers ----------------

    private class Metrics(val ascent: Float, val descent: Float) {
        val height: Float get() = descent - ascent

        /** Offset to add to a desired visual centre to get the drawText baseline. */
        val baselineOffset: Float get() = -(ascent + descent) / 2f
    }

    private fun metrics(typeface: Typeface, size: Float): Metrics {
        textPaint.typeface = typeface
        textPaint.textSize = size
        val fm = textPaint.fontMetrics
        return Metrics(fm.ascent, fm.descent)
    }

    /** Baseline that vertically centres a single line of text on [centerY]. */
    private fun centeredBaseline(typeface: Typeface, size: Float, centerY: Float): Float =
        centerY + metrics(typeface, size).baselineOffset

    private fun measure(text: String, typeface: Typeface, size: Float): Float {
        textPaint.typeface = typeface
        textPaint.textSize = size
        textPaint.letterSpacing = 0f
        return textPaint.measureText(text)
    }

    private fun alpha(value: Float) = (value.coerceIn(0f, 1f) * 255f).toInt()

    private fun formatValue(value: Float) = value.toInt().toString()

    private companion object {
        const val PILL_WIDTH = 560f
        const val PILL_MAX_HEIGHT = 90f

        const val GLOBAL_SCALE = 0.8f

        const val BOLT_CIRCLE = 32f
        const val BOLT_CIRCLE_ICON = 18f
        const val BOLT_SQUARE = 18f
        const val BOLT_SQUARE_RADIUS = 4.5f
        const val BOLT_SQUARE_ICON = 12f

        const val BOLT_MIN_X = 4f
        const val BOLT_MIN_Y = 2f
        const val BOLT_BOUNDS_W = 16f
        const val BOLT_BOUNDS_H = 20f

        const val RIPPLE_INNER_D = 160f
        const val RIPPLE_MID_D = 220f
        const val RIPPLE_OUTER_D = 280f

        const val TAGLINE_SIZE = 9f
        const val TITLE_SIZE = 26f
        const val TAGLINE_LETTER_SPACING = 2f
        const val TITLE_LETTER_SPACING = 2f
        const val TITLE_SPACING = 2f

        // NumHost columns: 32 | 18 | 14 | auto | * | auto | 14 | auto | 5
        const val COL_PAD_LEFT = 32f
        const val COL_BOLT_SLOT = 18f
        const val COL_GAP = 14f
        const val COL_PAD_RIGHT = 5f

        const val WH_VALUE_SIZE = 26f
        const val WH_MAX_SIZE = 14f
        const val WH_SPACING = 2f
        const val PCT_VALUE_SIZE = 22f
        const val PCT_SIGN_SIZE = 13f
        const val PCT_SPACING = 1f

        const val BADGE_DIAMETER = 46f
        const val BADGE_ARC_STROKE = 4.5f

        // Phone glyph, sized to sit inside the badge's inner radius (18.5) with the electrode
        // clearing the body.
        const val PHONE_WIDTH = 12f
        const val PHONE_HEIGHT = 20f
        const val PHONE_RADIUS = 3f
        const val PHONE_STROKE = 2f
        const val PHONE_HOME_WIDTH = 4.5f
        const val PHONE_HOME_HEIGHT = 1.5f
        const val PHONE_HOME_INSET = 2.8f

        const val ELECTRODE_WIDTH = 9f
        const val ELECTRODE_HEIGHT = 3f
        const val ELECTRODE_RADIUS = 1f
        const val ELECTRODE_MARGIN_TOP = 14f

        const val TEXT_SECONDARY_ALPHA = 0.55f
        const val TEXT_TERTIARY_ALPHA = 0.40f

        const val LOW_BATTERY_THRESHOLD = 20

        val PILL_COLOR = Color.parseColor("#312F30")
        val ICON_PLATE_COLOR = Color.parseColor("#E9E7E4")
        val BOLT_DARK_COLOR = Color.parseColor("#141313")
        val TEXT_PRIMARY = Color.WHITE
        val RIPPLE_COLOR = Color.parseColor("#656363")
        val ACCENT_COLOR = Color.parseColor("#C6CA4C")
        val BADGE_LOW_COLOR = Color.parseColor("#FF4D4F")
        val BADGE_DARK_COLOR = Color.parseColor("#262425")
    }
}
