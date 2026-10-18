package com.zmd.charge.chargepop.upstream

import java.util.Locale

/**
 * HUD copy, mirroring `zmd-charge`'s `Localization` (`IsChinese ? ... : ...`).
 *
 * Only the charge strings are ported: the power saver variants belong to a feature this module
 * does not implement.
 */
internal object HudStrings {

    fun tagline(language: String): String =
        if (isChinese(language)) "/// 超充模式" else "/// SUPER CHARGE MODE"

    fun title(language: String): String =
        if (isChinese(language)) "超充模式" else "Super Charge Mode"

    private fun isChinese(language: String): Boolean = when (language) {
        HudSettings.LANGUAGE_ZH -> true
        HudSettings.LANGUAGE_EN -> false
        else -> Locale.getDefault().language == "zh"
    }
}
