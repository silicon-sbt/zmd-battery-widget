package com.zmd.charge.chargepop.upstream

/**
 * 参考实现里来自 core/settings 的常量，这里只做最小替身：
 * 复刻版不需要设置页那套宽度/语言选项。
 */
internal object HudSettings {
    const val DEFAULT_WIDTH_RATIO = 0.86f
    const val MIN_WIDTH_RATIO = 0.5f
    const val MAX_WIDTH_RATIO = 1.0f
    const val LANGUAGE_AUTO = "auto"
    const val LANGUAGE_ZH = "zh"
    const val LANGUAGE_EN = "en"
}
