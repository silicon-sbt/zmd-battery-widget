package com.zmd.charge.widget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

data class BatterySnapshot(
    val percent: Int,
    val charging: Boolean,
    val remainingMah: Int,
    val designMah: Int
)

object BatteryData {
    /**
     * @param chargingOverride 非空时强制使用该充电状态。
     *   用于插拔电广播到达瞬间（此时 sticky 电量广播可能还没更新，会读到旧状态导致响应迟滞）。
     */
    fun read(context: Context, chargingOverride: Boolean? = null): BatterySnapshot {
        val design = DeviceCapacity.lookup(context)

        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        var percent = 0
        var charging = false
        if (intent != null) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) percent = (level * 100 / scale).coerceIn(0, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        }
        if (chargingOverride != null) charging = chargingOverride

        var remaining = design * percent / 100
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            if (counter != Int.MIN_VALUE && counter > 0) {
                val fromCounter = counter / 1000
                val expected = design * percent / 100
                if (fromCounter in (expected * 3 / 4)..(expected * 5 / 4)) {
                    remaining = fromCounter.coerceIn(0, design)
                }
            }
        } catch (_: Throwable) {}

        return BatterySnapshot(percent, charging, remaining, design)
    }

    fun isCharging(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent != null) {
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        }
        return false
    }
}
