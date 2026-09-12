package com.zmd.charge.settings

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.zmd.charge.R
import com.zmd.charge.widget.BatteryWidgetProvider

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

        runUpdateCheck(manual = false)
        updateRuntimeSummary()
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        updateRuntimeSummary()
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
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

    private fun openAppDetails() {
        val ctx = context ?: return
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
            )
        } catch (_: Exception) {}
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
