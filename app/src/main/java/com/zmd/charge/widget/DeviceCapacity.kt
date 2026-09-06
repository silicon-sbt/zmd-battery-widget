package com.zmd.charge.widget

import android.content.Context
import android.os.Build
import com.zmd.charge.settings.Prefs

/**
 * 满充设计容量表：读设备型号(Build.MODEL / Build.DEVICE) 后按子串匹配。
 * 越具体越靠前，未命中则回退到设置页的"容量表兜底值"。
 * 说明：机型代号/容量为尽力维护，若未命中可在设置里手填标称容量。
 */
object DeviceCapacity {

    private val TABLE: List<Pair<String, Int>> = listOf(
        // ---- iQOO Neo 系列 ----
        "neo9s pro+" to 5500, "neo9 s pro+" to 5500,
        "v2403a" to 5500, "pd2403" to 5500,          // Neo9S Pro+ 代号
        "neo9s pro" to 5500, "neo9 s pro" to 5500,
        "neo9 pro" to 5160, "neo9" to 5160,
        "neo8 pro" to 5000, "neo8" to 5000,
        "neo7 racing" to 5000, "neo7" to 5000,
        // ---- iQOO 数字旗舰 ----
        "iQOO 12 pro" to 5100, "iQOO 12" to 5000,
        "iQOO 11s" to 5000, "iQOO 11" to 5000,
        // ---- iQOO Z 系列 ----
        "z8x" to 6000, "z8" to 5000, "z7x" to 6000,
        // ---- vivo X 系列 ----
        "x100" to 5000,
        "x90 pro" to 4870, "x90" to 4810,
        // ---- vivo S / Y 系列 ----
        "s18" to 5000, "s17" to 4600,
        "y78" to 5000,
    )

    fun lookup(context: Context): Int {
        val m = Build.MODEL.orEmpty().lowercase()
        val d = Build.DEVICE.orEmpty().lowercase()
        for ((key, cap) in TABLE) {
            if (m.contains(key) || d.contains(key)) return cap
        }
        return Prefs.capacityFallback(context)
    }
}
