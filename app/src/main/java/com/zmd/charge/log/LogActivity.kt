package com.zmd.charge.log

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.zmd.charge.R

/** 运行日志查看器：可分享 / 复制 / 清空，方便把现场证据发回来定位问题。 */
class LogActivity : AppCompatActivity() {

    private lateinit var logText: TextView
    private lateinit var logMeta: TextView
    private lateinit var scroller: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        logText = findViewById(R.id.log_text)
        logMeta = findViewById(R.id.log_meta)
        scroller = findViewById(R.id.log_scroll)

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btn_refresh).setOnClickListener { load() }
        findViewById<TextView>(R.id.btn_share).setOnClickListener { share() }
        findViewById<TextView>(R.id.btn_copy).setOnClickListener { copyToClipboard() }
        findViewById<TextView>(R.id.btn_clear).setOnClickListener { confirmClear() }

        load()
    }

    private fun load() {
        try {
            logText.text = LogFile.read()
        } catch (t: Throwable) {
            logText.text = "读取日志失败：" + t.message
        }
        logMeta.text = "共 " + LogFile.lineCount() + " 行 · " + (LogFile.sizeBytes() / 1024) + " KB"
        Handler(Looper.getMainLooper()).post {
            try { scroller.fullScroll(ScrollView.FOCUS_DOWN) } catch (_: Throwable) {}
        }
    }

    private fun body(): String = try { LogFile.read() } catch (t: Throwable) { "日志不可用" }

    private fun share() {
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "终末地电量 · 运行日志")
                putExtra(Intent.EXTRA_TEXT, body())
            }
            startActivity(Intent.createChooser(send, "分享日志"))
        } catch (t: Throwable) {
            Toast.makeText(this, "分享失败：" + t.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("zmd-log", body()))
            Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "复制失败：" + t.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("清空日志？")
            .setMessage("会删除当前记录的全部运行日志。")
            .setPositiveButton("清空") { _, _ ->
                LogFile.clear()
                load()
                Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
