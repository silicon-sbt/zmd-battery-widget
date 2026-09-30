package com.zmd.charge.widget

import android.content.Context
import com.zmd.charge.log.LogFile
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 远程容量表：从仓库拉 data/capacity.json，缓存到 filesDir，匹配时【优先于内置表】。
 *
 * 目的：机型/容量的修正可以「改表即生效」，不必等发新版。
 * 匹配优先级：手填（强制） > 远程表 > 内置表 > 兜底值。
 *
 * 双源 + 12h 节流 + 全程静默失败（拉不到就继续用内置/缓存表，绝不影响组件）。
 */
object CapacityStore {

    private const val FILE_NAME = "capacity_remote.json"
    private const val MIN_INTERVAL_MS = 12 * 60 * 60 * 1000L   // 12 小时节流
    private const val MAX_BYTES = 512 * 1024
    private const val MIN_MAH = 500
    private const val MAX_MAH = 30000

    /** jsDelivr 在国内通常更稳；raw 作为兜底。 */
    private val URLS = listOf(
        "https://cdn.jsdelivr.net/gh/silicon-sbt/zmd-battery-widget@main/data/capacity.json",
        "https://raw.githubusercontent.com/silicon-sbt/zmd-battery-widget/main/data/capacity.json"
    )

    @Volatile private var entries: List<Pair<String, Int>> = emptyList()
    @Volatile var version: Int = 0
        private set
    @Volatile var updatedAt: Long = 0L
        private set
    @Volatile var lastError: String? = null
        private set
    @Volatile var lastFetchAt: Long = 0L
        private set

    fun init(context: Context) {
        loadCache(context)
    }

    /** 远程条目（按顺序匹配，越靠前优先级越高）。 */
    fun remoteEntries(): List<Pair<String, Int>> = entries

    fun count(): Int = entries.size

    fun describe(context: Context): String {
        if (entries.isEmpty()) {
            val err = lastError
            return if (err == null) "远程表未更新，当前用内置表" else "远程表更新失败：" + err
        }
        val tail = if (updatedAt > 0) "· " + timeText(updatedAt) else ""
        return "远程表 " + entries.size + " 条 · 版本 " + version + tail + "（优先于内置表）"
    }

    /** 异步刷新；force=true 时忽略 12h 节流（设置页手动点）。 */
    fun refreshAsync(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && entries.isNotEmpty() && now - lastFetchAt < MIN_INTERVAL_MS) return
        lastFetchAt = now
        Thread {
            val ok = fetch(context)
            if (ok) {
                LogFile.i("Capacity", "远程容量表已更新：" + describe(context))
                // 新表立即生效
                try { BatteryWidgetProvider.refresh(context) } catch (_: Throwable) {}
            } else {
                LogFile.i("Capacity", "远程容量表更新失败：" + (lastError ?: "未知"))
            }
        }.start()
    }

    private fun fetch(context: Context): Boolean {
        for (url in URLS) {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 10000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "zmd-charge")
                    setRequestProperty("Accept", "application/json")
                }
                val code = conn.responseCode
                if (code != 200) {
                    conn.disconnect()
                    lastError = "HTTP " + code + " @ " + host(url)
                    continue
                }
                val text = conn.inputStream.use { it.readBytes() }
                    .let { if (it.size > MAX_BYTES) String(it, 0, MAX_BYTES) else String(it) }
                conn.disconnect()
                val parsed = parse(text) ?: run {
                    lastError = "内容解析失败 @ " + host(url)
                    null
                } ?: continue
                entries = parsed.second
                version = parsed.first
                updatedAt = System.currentTimeMillis()
                saveCache(context, text)
                lastError = null
                return true
            } catch (t: Throwable) {
                lastError = (t.javaClass.simpleName + ": " + t.message) + " @ " + host(url)
                LogFile.w("Capacity", "拉取失败 " + url + " -> " + lastError)
            }
        }
        return false
    }

    private fun host(url: String): String = try { URL(url).host } catch (t: Throwable) { url }

    private fun parse(text: String): Pair<Int, List<Pair<String, Int>>>? = try {
        val obj = JSONObject(text)
        val arr = obj.optJSONArray("entries")
        if (arr == null || arr.length() == 0) {
            null
        } else {
            val list = ArrayList<Pair<String, Int>>(arr.length())
            for (i in 0 until arr.length()) {
                val row = arr.optJSONArray(i) ?: continue
                val k = row.optString(0, "").trim().lowercase()
                val c = row.optInt(1, 0)
                if (k.isEmpty() || k.length > 64) continue
                if (c < MIN_MAH || c > MAX_MAH) continue
                list.add(k to c)
            }
            if (list.isEmpty()) null else (obj.optInt("version", 0) to list)
        }
    } catch (t: Throwable) {
        LogFile.w("Capacity", "解析失败：" + t.message)
        null
    }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun loadCache(context: Context) {
        try {
            val f = file(context)
            if (!f.exists() || f.length() == 0L) return
            val parsed = parse(f.readText()) ?: return
            entries = parsed.second
            version = parsed.first
            updatedAt = f.lastModified()
            LogFile.i("Capacity", "已加载本地缓存的远程容量表：" + entries.size + " 条 · 版本 " + version)
        } catch (t: Throwable) {
            LogFile.w("Capacity", "读取缓存失败：" + t.message)
        }
    }

    private fun saveCache(context: Context, text: String) {
        try {
            file(context).writeText(text)
        } catch (t: Throwable) {
            LogFile.w("Capacity", "写入缓存失败：" + t.message)
        }
    }

    private fun timeText(ms: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(ms))
}
