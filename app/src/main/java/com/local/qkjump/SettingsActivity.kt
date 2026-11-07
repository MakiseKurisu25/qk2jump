package com.local.qkjump

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 设置页。四块：行为 / 无障碍 / 关于 / 恢复默认。
 * 所有开关都是即时落盘的，没有「保存」按钮 —— 免得用户改完忘了点。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var cbDryRun: Switch
    private lateinit var tvWait: TextView
    private lateinit var sbWait: SeekBar
    private lateinit var tvWindow: TextView
    private lateinit var sbWindow: SeekBar
    private lateinit var tvA11yStatus: TextView
    private lateinit var tvLast: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        cbDryRun = findViewById(R.id.cbDryRun)
        tvWait = findViewById(R.id.tvWait)
        sbWait = findViewById(R.id.sbWait)
        tvWindow = findViewById(R.id.tvWindow)
        sbWindow = findViewById(R.id.sbWindow)
        tvA11yStatus = findViewById(R.id.tvA11yStatus)
        tvLast = findViewById(R.id.tvLast)

        QLog.i("Set", "═══ 设置页打开 ═══")

        // 调试模式
        cbDryRun.isChecked = Prefs.dryRun(this)
        cbDryRun.setOnCheckedChangeListener { _, v ->
            Prefs.setDryRun(this, v)
            QLog.i("Set", "设置：调试模式=$v")
        }

        // 等待时长：每格 500ms，0~10000ms
        sbWait.progress = Prefs.waitMs(this) / WAIT_STEP_MS
        refreshWaitText(Prefs.waitMs(this))
        sbWait.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val ms = progress * WAIT_STEP_MS
                refreshWaitText(ms)
                if (fromUser) {
                    Prefs.setWaitMs(this@SettingsActivity, ms)
                    QLog.i("Set", "设置：等待=${ms}ms")
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        })

        // 待命窗口：每格 1 秒，1~60 秒
        sbWindow.max = (Prefs.MAX_WINDOW_MS / 1000) - 1
        sbWindow.progress = (Prefs.windowMs(this) / 1000) - 1
        refreshWindowText(Prefs.windowMs(this))
        sbWindow.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val sec = progress + 1
                sb?.let { refreshWindowText(sec * 1000) }
                if (fromUser) {
                    Prefs.setWindowMs(this@SettingsActivity, sec * 1000)
                    QLog.i("Set", "设置：待命窗口=${sec}s")
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        })

        findViewById<TextView>(R.id.btnOpenA11y).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (t: Throwable) {
                QLog.e("Set", "打开无障碍设置失败：${QLog.describe(t)}", t)
            }
        }

        findViewById<TextView>(R.id.btnHeal).setOnClickListener {
            val ok = A11yGuard.heal(this)
            refreshA11yStatus()
            val msg = when {
                ok -> getString(R.string.toast_heal_ok)
                !A11yGuard.canWrite(this) -> getString(R.string.toast_heal_no_perm)
                else -> getString(R.string.toast_heal_failed)
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }

        findViewById<TextView>(R.id.btnReset).setOnClickListener { confirmReset() }

        refreshA11yStatus()
        refreshLast()
    }

    override fun onResume() {
        super.onResume()
        refreshA11yStatus()
        refreshLast()
    }

    private fun refreshWaitText(ms: Int) {
        tvWait.text = getString(R.string.settings_wait, ms)
    }

    private fun refreshWindowText(ms: Int) {
        tvWindow.text = getString(R.string.settings_window, ms / 1000)
    }

    private fun refreshA11yStatus() {
        val line = A11yGuard.statusLine(this)
        tvA11yStatus.text = getString(R.string.settings_a11y_status, line)
        QLog.i("Set", "无障碍状态：$line")
    }

    private fun refreshLast() {
        val last = Prefs.lastResult(this)
        tvLast.text = if (last.isBlank()) {
            getString(R.string.settings_last, "（还没跑过）")
        } else {
            getString(R.string.settings_last, last)
        }
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setMessage(R.string.settings_reset_confirm)
            .setNegativeButton("取消", null)
            .setPositiveButton("恢复") { _, _ ->
                Prefs.setWaitMs(this, Prefs.DEFAULT_WAIT_MS)
                Prefs.setWindowMs(this, Prefs.DEFAULT_WINDOW_MS)
                Prefs.setDryRun(this, false)

                sbWait.progress = Prefs.DEFAULT_WAIT_MS / WAIT_STEP_MS
                sbWindow.progress = (Prefs.DEFAULT_WINDOW_MS / 1000) - 1
                refreshWaitText(Prefs.DEFAULT_WAIT_MS)
                refreshWindowText(Prefs.DEFAULT_WINDOW_MS)
                cbDryRun.isChecked = false

                QLog.i("Set", "已恢复默认：等待=${Prefs.DEFAULT_WAIT_MS}ms 窗口=${Prefs.DEFAULT_WINDOW_MS}ms 调试=false")
            }
            .show()
    }

    private companion object {
        /** 等待滑杆每格 500 毫秒。 */
        const val WAIT_STEP_MS = 500
    }
}
