package com.zmd.charge.chargepop

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager

/**
 * 全屏插电动画。要点：
 * - showWhenLocked + turnScreenOn：息屏/锁屏时插电也能点亮屏幕并直接显示；
 * - 透明主题、excludeFromRecents、noHistory：不在最近任务里留痕；
 * - 动画结束自动 finish，点一下也能立刻关。
 */
class ChargePopActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
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

        val capMah = try { com.zmd.charge.widget.DeviceCapacity.lookup(this) } catch (t: Throwable) { 5500 }
        val view = com.zmd.charge.chargepop.upstream.UpstreamPopView(this, level, plugged, capMah)
        view.setOnClickListener { finish() }
        setContentView(view)
        handler.postDelayed(finishTask, view.durationMillis + 250L)
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        handler.removeCallbacks(finishTask)
        super.onDestroy()
    }
}
