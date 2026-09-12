package com.zmd.charge.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.zmd.charge.LiveService

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
        }
        // 若用户已开启"实时刷新"，进入 App 时确保前台服务在跑（前台启动合法）
        val on = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("live_service", false)
        if (on) {
            try {
                ContextCompat.startForegroundService(this, Intent(this, LiveService::class.java))
            } catch (_: Throwable) {}
        }
    }
}
