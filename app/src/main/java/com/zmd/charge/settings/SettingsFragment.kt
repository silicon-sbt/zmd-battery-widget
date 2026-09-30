package com.zmd.charge.settings

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.zmd.charge.LiveService
import com.zmd.charge.R
import com.zmd.charge.log.LogActivity
import com.zmd.charge.log.LogFile
import com.zmd.charge.widget.BatteryWidgetProvider
import com.zmd.charge.widget.CapacityStore
import com.zmd.charge.widget.DeviceCapacity
import com.zmd.charge.widget.WidgetMetrics

class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val handler = Handler(Looper.getMainLooper())
    private var checking = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs, rootKey)

        findPreference<Preference>("check_update")?.setOnPreferenceClickListener {
            runUpdateCheck(manual = true)
            true
        }
        findPreference<Preference>("battery_opt")?.setOnPreferenceClickListener {
            requestBatteryOptExemption()
            true
        }
        findPreference<Preference>("autostart")?.setOnPreferenceClickListener {
            openAppDetails()
            true
        }
        findPreference<SwitchPreference>("live_service")?.setOnPreferenceChangeListener { _, newValue ->
            setLiveService(newValue as Boolean)
            true
        }

        // 用户一旦手填标称容量，就自动切到"手填优先"——不用再自己去开开关
        findPreference<Preference>("capacity_fallback")?.setOnPreferenceChangeListener { _, newValue ->
            val v = (newValue as? String)?.trim().orEmpty().toIntOrNull()
            if (v != null && v in 500..30000) {
                try {
                    preferenceManager.sharedPreferences
                        ?.edit()?.putBoolean("force_capacity", true)?.apply()
                    (findPreference<Preference>("force_capacity") as? SwitchPreference)?.isChecked = true
                    LogFile.i("Settings", "手填容量 " + v + " mAh，已自动切换为手填优先")
                    Toast.makeText(requireContext(), "已改用你填的 " + v + " mAh", Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) {
                    LogFile.e("Settings", "切换手填优先失败", t)
                }
            }
            true
        }

        // 手动更新远程容量表
        findPreference<Preference>("capacity_update")?.setOnPreferenceClickListener {
            try {
                CapacityStore.refreshAsync(requireContext(), force = true)
                Toast.makeText(requireContext(), "正在更新容量表…", Toast.LENGTH_SHORT).show()
                handler.postDelayed({
                    try {
                        findPreference<Preference>("capacity_update")?.summary =
                            CapacityStore.describe(requireContext())
                    } catch (_: Throwable) {}
                }, 5000)
            } catch (t: Throwable) {
                LogFile.e("Settings", "更新容量表失败", t)
            }
            true
        }

        findPreference<Preference>("open_log")?.setOnPreferenceClickListener {
            try {
                startActivity(Intent(requireContext(), LogActivity::class.java))
            } catch (_: Throwable) {}
            true
        }

        runUpdateCheck(manual = false)
        updateRuntimeSummary()
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        updateRuntimeSummary()
        // 摘要只是锦上添花，任何异常都不该让设置页崩掉
        try {
            findPreference<Preference>("open_log")?.summary = LogFile.summary()
        } catch (t: Throwable) {
            LogFile.e("Settings", "更新日志摘要失败", t)
        }
        try {
            findPreference<Preference>("widget_columns")?.summary =
                WidgetMetrics.describe(requireContext()) + " · 桌面实际宽度由桌面决定，可长按组件拖动边缘调整"
        } catch (t: Throwable) {
            LogFile.e("Settings", "更新宽度摘要失败", t)
        }
        updateCapacitySummary()
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        LogFile.i("Settings", "设置变更 key=" + key)
        updateCapacitySummary()
        BatteryWidgetProvider.refresh(requireContext())
    }

    // ---------- 后台运行（实时刷新） ----------

    private fun updateRuntimeSummary() {
        val ctx = context ?: return
        val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        val ignoring = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        findPreference<Preference>("battery_opt")?.summary =
            if (ignoring) "已允许：电量/插拔电可实时刷新"
            else "未允许：点此授权，电量/插拔电即可实时刷新"
    }

    private fun requestBatteryOptExemption() {
        val ctx = context ?: return
        val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        val pkg = ctx.packageName
        if (pm.isIgnoringBatteryOptimizations(pkg)) {
            Toast.makeText(ctx, "已允许后台运行", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + pkg))
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {}
        }
    }

    /** 容量摘要：直接告诉用户"当前生效多少 mAh、依据是什么"，避免机型被识别错时一头雾水。 */
    private fun updateCapacitySummary() {
        val ctx = context ?: return
        try {
            val eff = DeviceCapacity.lookup(ctx)
            findPreference<Preference>("capacity_fallback")?.summary =
                "当前生效 " + eff + " mAh · 依据：" + DeviceCapacity.source(ctx)
            findPreference<Preference>("capacity_update")?.summary = CapacityStore.describe(ctx)
        } catch (t: Throwable) {
            LogFile.e("Settings", "更新容量摘要失败", t)
        }
    }

    private fun openAppDetails() {
        val ctx = context ?: return
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
            )
        } catch (_: Exception) {}
    }

    // ---------- 实时刷新（前台服务） ----------

    private fun setLiveService(on: Boolean) {
        val ctx = context ?: return
        if (on) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, LiveService::class.java))
            } catch (_: Throwable) {}
            Toast.makeText(ctx, "已开启实时刷新（静默常驻通知）", Toast.LENGTH_SHORT).show()
        } else {
            try { ctx.stopService(Intent(ctx, LiveService::class.java)) } catch (_: Throwable) {}
            Toast.makeText(ctx, "已关闭实时刷新", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- 检查更新 ----------

    private fun runUpdateCheck(manual: Boolean) {
        if (checking) return
        checking = true
        val ctx = context ?: run { checking = false; return }
        Thread {
            val info = UpdateChecker.check()
            val current = UpdateChecker.currentVersion(ctx)
            handler.post {
                checking = false
                val pref = findPreference<Preference>("check_update") ?: return@post
                when {
                    info == null ->
                        pref.summary = if (manual) "检查失败，请检查网络后重试" else "点此检查是否有新版本"
                    UpdateChecker.isNewer(info.latest, current) -> {
                        pref.summary = "发现新版本 v" + info.latest + "，点此更新"
                        if (manual) showUpdateDialog(info)
                    }
                    else -> pref.summary = "已是最新版本 v" + current
                }
            }
        }.start()
    }

    private fun showUpdateDialog(info: UpdateInfo) {
        AlertDialog.Builder(requireContext())
            .setTitle("发现新版本 v" + info.latest)
            .setMessage(info.notes.ifBlank { "有新版本可用" })
            .setPositiveButton("去下载") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
            }
            .setNegativeButton("稍后", null)
            .show()
    }
}