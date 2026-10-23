package com.zmd.charge.settings

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.zmd.charge.log.LogFile

/**
 * 后台保活自检。
 *
 * vivo/OriginOS 有一套自己的后台管理：App 静置一段时间会被"冻结"（进 freezer，
 * 连广播都收不到），插电弹岛会因此失效。系统 API 查不到冻结状态本身，
 * 只能从三个可查的信号推断风险，并引导用户去开白名单。
 */
object BackgroundGuard {

    /** 已加入电池优化白名单（标准 API，vivo 也认）。 */
    fun ignoringBatteryOptimizations(ctx: Context): Boolean = try {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(ctx.packageName)
    } catch (t: Throwable) {
        false
    }

    /** 是否处于受限/极少使用待机桶（会被系统降级调度）。 */
    fun standbyBucket(ctx: Context): Int = try {
        if (Build.VERSION.SDK_INT >= 28) {
            val usm = ctx.getSystemService(android.app.usage.UsageStatsManager::class.java)
            usm?.appStandbyBucket ?: -1
        } else {
            -1
        }
    } catch (t: Throwable) {
        -1
    }

    fun bucketName(bucket: Int): String = when (bucket) {
        -1 -> "未知"
        5 -> "活跃"          // ACTIVE
        10 -> "活跃"         // ACTIVE（旧常量）
        20 -> "工作集"       // WORKING_SET
        30 -> "常用"         // FREQUENT
        40 -> "极少使用"     // RARE
        45 -> "受限"         // RESTRICTED
        50 -> "从不用"       // NEVER
        else -> "未知($bucket)"
    }

    /**
     * 一句话结论。bucket >= 40（极少使用/受限）或未加电池白名单都视为有风险。
     * 注意：vivo 的"冻结"没有公开 API，这里只能提示风险，无法直接检测。
     */
    fun describe(ctx: Context): String {
        val ignoring = ignoringBatteryOptimizations(ctx)
        val bucket = standbyBucket(ctx)
        val risky = !ignoring || bucket >= 40
        return buildString {
            append(if (ignoring) "电池优化：已豁免" else "电池优化：未豁免")
            append(" · 待机桶：").append(bucketName(bucket))
            if (risky) {
                append("\n有被系统冻结的风险，可能导致插电不弹岛 —— 点此去开启后台白名单")
            } else {
                append("\n后台状态正常")
            }
        }
    }

    /** 标准电池优化请求页；在 iQOO/vivo 上会落到「后台高耗电管理」。 */
    fun batteryOptIntent(ctx: Context): Intent {
        val pkg = ctx.packageName
        return try {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:" + pkg))
        } catch (t: Throwable) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
    }

    fun logState(ctx: Context) {
        try {
            LogFile.i(
                "Guard",
                "后台自检：电池优化豁免=" + ignoringBatteryOptimizations(ctx) +
                    " 待机桶=" + bucketName(standbyBucket(ctx))
            )
        } catch (_: Throwable) {
        }
    }
}
