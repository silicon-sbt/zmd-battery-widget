package com.zmd.charge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.zmd.charge.settings.SettingsActivity
import com.zmd.charge.widget.BatteryWidgetProvider

/**
 * 前台服务：常驻以保证进程存活，并让 App 处于前台状态（广播走前台队列），
 * 从而使电量/插拔电刷新稳定在毫秒级。
 * 通知为【静默】：IMPORTANCE_LOW、无声音、无震动、不显示角标。
 */
class LiveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("终末地电量")
            .setContentText("实时刷新中")
            .setSmallIcon(R.drawable.ic_bolt)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        BatteryWidgetProvider.refresh(this)
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(CHANNEL_ID, "实时刷新", NotificationManager.IMPORTANCE_LOW)
                ch.setShowBadge(false)
                ch.setSound(null, null)
                ch.enableVibration(false)
                ch.lockscreenVisibility = Notification.VISIBILITY_SECRET
                nm.createNotificationChannel(ch)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "zmd_live"
        const val NOTIF_ID = 1001
    }
}