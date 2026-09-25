package com.zmd.charge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.zmd.charge.log.LogFile

/** 开机后若用户已开启"实时刷新"，则重新拉起前台服务。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        LogFile.init(context)
        LogFile.i("Boot", "开机广播收到")
        val on = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("live_service", false)
        if (on) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, LiveService::class.java))
            } catch (t: Throwable) {
                LogFile.e("Boot", "开机拉起前台服务失败", t)
            }
        }
    }
}
