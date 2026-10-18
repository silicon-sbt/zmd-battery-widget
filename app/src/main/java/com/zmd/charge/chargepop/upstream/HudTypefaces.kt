package com.zmd.charge.chargepop.upstream

import android.content.res.AssetManager
import android.graphics.Typeface
import android.util.Log

/**
 * The bundled Inter face.
 *
 * `zmd-charge` calls `WithInterFont()` in its Avalonia builder, so its Latin text and every numeral
 * is Inter. Android's system face is MiSans on HyperOS and OPPO Sans on OxygenOS, which is visibly
 * different in exactly the places this HUD uses it: the mWh digits and the percent sign.
 *
 * Inter is OFL licensed; see `assets/fonts/Inter-OFL.txt`.
 */
internal object HudTypefaces {

    private const val TAG = "EndfieldCharge"
    private const val ASSET_PATH = "fonts/Inter-Variable.ttf"

    @Volatile
    private var cached: Typeface? = null

    @Volatile
    private var available = true

    /** Inter at weight 500, or the system medium face when the asset cannot be read. */
    fun medium(assets: AssetManager): Typeface = load(assets, 500, "sans-serif-medium", Typeface.NORMAL)

    /** Inter at weight 700, or the system bold face when the asset cannot be read. */
    fun bold(assets: AssetManager): Typeface = load(assets, 700, "sans-serif", Typeface.BOLD)

    private fun load(
        assets: AssetManager,
        weight: Int,
        fallbackFamily: String,
        fallbackStyle: Int,
    ): Typeface {
        val inter = inter(assets)
        if (inter == null) return Typeface.create(fallbackFamily, fallbackStyle)

        // The variable font's default instance is weight 400; ask for the instance we want.
        return runCatching {
            Typeface.Builder(assets, ASSET_PATH)
                .setFontVariationSettings("'wght' $weight")
                .build()
        }.getOrDefault(inter)
    }

    private fun inter(assets: AssetManager): Typeface? {
        cached?.let { return it }
        if (!available) return null

        synchronized(this) {
            cached?.let { return it }
            val built = runCatching {
                Typeface.Builder(assets, ASSET_PATH).build()
            }.onFailure {
                available = false
                Log.w(TAG, "[font] could not load $ASSET_PATH, falling back to the system face", it)
            }.getOrNull()
            cached = built
            return built
        }
    }
}
