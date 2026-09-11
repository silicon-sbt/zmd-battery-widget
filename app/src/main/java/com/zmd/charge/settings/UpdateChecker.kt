package com.zmd.charge.settings

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val latest: String, val url: String, val notes: String)

/**
 * 检查更新：读取 GitHub Releases 最新版本，与本地 versionName 比较。
 */
object UpdateChecker {

    private const val API =
        "https://api.github.com/repos/silicon-sbt/zmd-battery-widget/releases/latest"

    fun currentVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    /** 网络请求，需在子线程调用。失败返回 null。 */
    fun check(): UpdateInfo? = try {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "zmd-charge-android")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val json = JSONObject(body)
        val tag = json.optString("tag_name").trim().removePrefix("v").removePrefix("V")
        val url = json.optString("html_url")
        val notes = json.optString("body")
        if (tag.isNotEmpty()) UpdateInfo(tag, url, notes) else null
    } catch (e: Exception) {
        null
    }

    /** latest 是否比 current 新（点分数字比较）。 */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split(".").mapNotNull { it.trim().toIntOrNull() }
        val b = current.split(".").mapNotNull { it.trim().toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
