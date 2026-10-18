package com.zmd.charge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.zmd.charge.chargepop.ChargePop
import com.zmd.charge.log.LogFile
import com.zmd.charge.settings.Prefs
import com.zmd.charge.settings.SettingsActivity
import com.zmd.charge.widget.BatteryWidgetProvider

/**
 * 前台服务：常驻以保证进程存活，并让 App 处于前台状态（广播走前台队列），
 * 从而使电量/插拔电刷新稳定在毫秒级；同时承担"插电弹动画"的监听。
 * 通知为【静默】：IMPORTANCE_LOW、无声音、无震动、不显示角标。
 */
class LiveService : Service() {

    private var powerReceiver: BroadcastReceiver? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingPop: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val popOn = Prefs.chargePop(this)
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("终末地电量")
            .setContentText(if (popOn) "插电动画待命" else "实时刷新中")
            .setSmallIcon(R.drawable.ic_bolt)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notif)
            }
            LogFile.i("Service", "前台服务已启动（静默通知），插电动画=" + popOn)
        } catch (t: Throwable) {
            LogFile.e("Service", "startForeground 失败", t)
        }
        registerPower()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogFile.i("Service", "onStartCommand flags=" + flags)
        BatteryWidgetProvider.refresh(this)
        registerPower()
        return START_STICKY
    }

    /**
     * 插拔电监听。注意：进程被厂商冻结时广播不会送达，
     * 所以这条链路依赖"用户已把 App 加入后台白名单"。
     */
    private fun registerPower() {
        if (powerReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        LogFile.i("Service", "收到插电广播")
                        if (!Prefs.chargePop(c)) return
                        val pm = c.getSystemService(Context.POWER_SERVICE) as? PowerManager
                        if (pm?.isInteractive != false) {
                            ChargePop.show(c)
                        } else {
                            // 息屏插电时系统会先播自己的充电动画（OriginOS 的 FlashCharge 圆环会盖住整屏），
                            // 延后一会儿再弹，否则会被压在下面看不见
                            val task = Runnable { ChargePop.show(c) }
                            pendingPop = task
                            mainHandler.postDelayed(task, 3600L)
                            LogFile.i("Service", "息屏插电，动画延后 3.6s（避让系统充电动画）")
                        }
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> LogFile.i("Service", "收到拔电广播")
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        try {
            registerReceiver(r, f)
            powerReceiver = r
        } catch (t: Throwable) {
            LogFile.e("Service", "注册电源广播失败", t)
        }
    }

    override fun onDestroy() {
        pendingPop?.let { mainHandler.removeCallbacks(it) }
        pendingPop = null
        powerReceiver?.let { try { unregisterReceiver(it) } catch (_: Throwable) {} }
        powerReceiver = null
        LogFile.i("Service", "前台服务已停止")
        super.onDestroy()
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
