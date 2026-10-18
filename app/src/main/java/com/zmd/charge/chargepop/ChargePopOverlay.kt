package com.zmd.charge.chargepop

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.zmd.charge.log.LogFile

/**
 * 悬浮窗版本的岛：贴在状态栏下方，盖在**任何**应用之上。
 *
 * 为什么必须是悬浮窗而不是 Activity：后台 startActivity 会被系统静默拦掉
 * （只有在自己的界面在前台时才放行），要在桌面/别的 App 上看到岛，只能走
 * TYPE_APPLICATION_OVERLAY —— 需要用户手动授予「显示在其他应用上层」。
 *
 * 代价：悬浮窗进不了锁屏（息屏插电那一下仍只有系统自己的充电动画）。
 */
object ChargePopOverlay {

    /**
     * 叠几层窗口。实测 OriginOS 会把第三方悬浮层按约 0.8 合成（纯黑探针渲染成 #333333），
     * 单层看起来永远"蒙了一层灰"。同内容叠 N 层后不透明度 = 1-(1-0.8)^N，
     * 3 层约 0.992 → 视觉上就是实心的。用户把不透明度调低时按同一比例叠。
     */
    private const val LAYERS = 3

    private val views = ArrayList<View>(LAYERS)
    private val handler = Handler(Looper.getMainLooper())
    private val removeTask = Runnable { hide() }

    fun canShow(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        return try {
            Settings.canDrawOverlays(ctx)
        } catch (t: Throwable) {
            false
        }
    }

    /** 需要用户在系统设置里授权悬浮窗时用这个跳转。 */
    fun requestPermissionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + ctx.packageName))

    fun show(ctx: Context, level: Int, plugged: Int): Boolean {
        val app = ctx.applicationContext
        if (!canShow(app)) {
            LogFile.i("ChargePop", "没有悬浮窗权限，跳过后台弹出")
            return false
        }
        hide()
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val heightPx = (190f * app.resources.displayMetrics.density).toInt()
        // 多层的单层 alpha：让 N 层叠加后的等效不透明度 ≈ 用户设定值
        val target = com.zmd.charge.settings.Prefs.chargePopAlpha(app) / 100f
        val layerAlpha = if (target >= 0.999f) 1f
        else (1.0 - Math.pow((1.0 - target).toDouble(), 1.0 / LAYERS)).toFloat()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 0
            // 明确要求不透明合成，避免个别 ROM 把第三方悬浮层按半透明渲染
            alpha = 1.0f
        }
        return try {
            var duration = 6000L
            repeat(LAYERS) {
                val v = com.zmd.charge.chargepop.upstream.UpstreamPopView(app, level, plugged, capacityMah(app))
                v.alpha = layerAlpha
                wm.addView(v, lp)
                views.add(v)
                duration = v.durationMillis
            }
            handler.removeCallbacks(removeTask)
            handler.postDelayed(removeTask, duration + 250L)
            LogFile.i("ChargePop", "悬浮窗岛已显示（" + LAYERS + " 层）level=" + level + " plugged=" + plugged)
            true
        } catch (t: Throwable) {
            LogFile.e("ChargePop", "添加悬浮窗失败", t)
            hide()
            false
        }
    }

    fun hide() {
        handler.removeCallbacks(removeTask)
        if (views.isEmpty()) return
        val wm = views[0].context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        for (v in views) {
            try {
                wm?.removeViewImmediate(v)
            } catch (t: Throwable) {
                LogFile.e("ChargePop", "移除悬浮窗失败", t)
            }
        }
        views.clear()
    }

    private fun capacityMah(ctx: Context): Int = try {
        com.zmd.charge.widget.DeviceCapacity.lookup(ctx)
    } catch (t: Throwable) {
        5500
    }

    /** 读当前电量/插电类型（sticky 广播）。 */
    fun readSticky(ctx: Context): Pair<Int, Int> {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val raw = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, 50) ?: 50
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (raw >= 0 && scale > 0) raw * 100 / scale else 50
        val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return pct to plugged
    }
}
