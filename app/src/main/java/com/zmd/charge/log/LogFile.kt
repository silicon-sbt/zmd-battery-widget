package com.zmd.charge.log

import android.content.Context
import android.os.Build
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量运行日志：滚动写入 files/zmd-log.txt，超过 256KB 自动裁掉前半段。
 *
 * 设计原则：
 * - 所有写入都吞异常 —— 日志永远不会反过来把 App 搞崩；
 * - 同时镜像一份到 logcat（tag = ZmdCharge），adb 调试时可直接 grep；
 * - 安装全局未捕获异常处理器，崩溃堆栈会留在文件里（"查不到原因"时这是唯一线索）。
 */
object LogFile {

    private const val LOG_TAG = "ZmdCharge"
    private const val LOG_NAME = "zmd-log.txt"
    private const val MAX_BYTES = 256 * 1024L
    private const val KEEP_BYTES = 128 * 1024
    /** 分享/复制时的最大长度，避免超 binder 上限。 */
    const val SHARE_LIMIT = 96 * 1024

    private val lock = Any()
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile private var file: File? = null
    @Volatile private var ready = false
    @Volatile private var crashHandlerInstalled = false

    /** 在 Application.onCreate 里调用一次。 */
    fun init(context: Context) {
        if (ready) return
        synchronized(lock) {
            if (ready) return
            file = try { File(context.filesDir, LOG_NAME) } catch (t: Throwable) { null }
            ready = true
        }
        installCrashHandler()
        i("App", "==== 启动 " + context.packageName +
                " · Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")" +
                " · " + Build.MANUFACTURER + " " + Build.MODEL + " · 日志开始 ====")
    }

    fun i(tag: String, msg: String) = write("I", tag, msg)

    fun w(tag: String, msg: String) = write("W", tag, msg)

    fun e(tag: String, msg: String, t: Throwable? = null) {
        write("E", tag, if (t == null) msg else msg + " :: " + t.javaClass.name + ": " + t.message)
        if (t != null) {
            write("E", tag, "  " + android.util.Log.getStackTraceString(t).trim())
        }
    }

    private fun write(level: String, tag: String, msg: String) {
        val f = file
        val time = synchronized(stamp) { stamp.format(Date()) }
        val line = time + " " + level + " [" + Thread.currentThread().name + "] " + tag + ": " + msg + "\n"
        if (f != null) {
            synchronized(lock) {
                try {
                    if (f.length() > MAX_BYTES) trim(f)
                    f.appendText(line)
                } catch (_: Throwable) {}
            }
        }
        try {
            if (level == "E") android.util.Log.e(LOG_TAG, tag + ": " + msg)
            else android.util.Log.println(android.util.Log.INFO, LOG_TAG, tag + ": " + msg)
        } catch (_: Throwable) {}
    }

    /** 文件过大时只留尾部 KEEP_BYTES，并丢弃被截断的半行。 */
    private fun trim(f: File) {
        try {
            val len = f.length()
            if (len <= KEEP_BYTES) return
            val tail = ByteArray(KEEP_BYTES)
            RandomAccessFile(f, "r").use { raf ->
                raf.seek(len - KEEP_BYTES)
                raf.readFully(tail)
            }
            val text = String(tail, Charsets.UTF_8)
            val cut = text.indexOf('\n')
            val kept = if (cut >= 0) text.substring(cut + 1) else text
            f.writeText("----- 日志已滚动（只保留最近一段）-----\n" + kept)
        } catch (_: Throwable) {}
    }

    /** 读取日志尾部（最多 SHARE_LIMIT 字节）。无日志时返回提示文案。 */
    fun read(): String {
        val f = file ?: return "（日志不可用）"
        return try {
            if (!f.exists() || f.length() == 0L) return "（暂无日志）"
            val len = f.length()
            val from = if (len > SHARE_LIMIT) len - SHARE_LIMIT else 0L
            val bytes = ByteArray((len - from).toInt())
            RandomAccessFile(f, "r").use { raf ->
                raf.seek(from)
                raf.readFully(bytes)
            }
            var text = String(bytes, Charsets.UTF_8)
            if (from > 0) {
                val cut = text.indexOf('\n')
                if (cut >= 0) text = text.substring(cut + 1)
                text = "（仅显示最近部分）\n" + text
            }
            text
        } catch (t: Throwable) {
            "（读取日志失败：" + t.message + "）"
        }
    }

    fun clear() {
        val f = file ?: return
        synchronized(lock) {
            try { f.writeText("") } catch (_: Throwable) {}
        }
        i("App", "---- 日志已清空 ----")
    }

    fun sizeBytes(): Long = try { file?.length() ?: 0L } catch (t: Throwable) { 0L }

    fun lineCount(): Int = try {
        val f = file
        if (f == null || !f.exists()) 0 else f.readLines().size
    } catch (t: Throwable) { 0 }

    /** 概要文案，给设置页做 summary。 */
    fun summary(): String {
        val n = lineCount()
        return if (n == 0) "暂无日志；点此查看" else "已记录 " + n + " 行 · " + (sizeBytes() / 1024) + " KB"
    }

    private fun installCrashHandler() {
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true
        try {
            val prev = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { t, ex ->
                try { e("CRASH", "未捕获异常，线程=" + t.name, ex) } catch (_: Throwable) {}
                try { prev?.uncaughtException(t, ex) } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }
}
