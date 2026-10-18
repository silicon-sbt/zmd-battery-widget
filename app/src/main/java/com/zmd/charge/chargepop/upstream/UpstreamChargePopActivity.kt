package com.zmd.charge.chargepop.upstream

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.zmd.charge.widget.DeviceCapacity

/**
 * 上游移植版的插电动画（来源 NingmengLemon/EndfieldChargingAnim，MIT）。
 */
class UpstreamChargePopActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var view: UpstreamPopView? = null
    private val finishTask = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, 50) ?: 50
        val plugged = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val cap = try { DeviceCapacity.lookup(this) } catch (t: Throwable) { 5500 }

        val v = UpstreamPopView(this, level, plugged, cap)
        v.setOnClickListener { finish() }
        view = v
        setContentView(v)

        val duration = v.durationMillis + 350L
        handler.postDelayed(finishTask, duration)
    }

    override fun finish() {
        view?.stop()
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        handler.removeCallbacks(finishTask)
        view?.stop()
        super.onDestroy()
    }
}
