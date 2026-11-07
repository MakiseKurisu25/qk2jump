package com.local.qkjump

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 唯一的入口页。打开本应用 = 走完这里的三步：
 *
 *   1. 检查胖乖生活装没装、启动入口在不在、无障碍开没开；
 *   2. 三步都过 → 进入待命窗口（ScanTap.arm）→ 拉起胖乖生活的主界面；
 *   3. 剩下的交给无障碍服务：它在首页上替你点一下右上角的扫描图标。
 *
 * 任何一步走不通，就停在本页，把原因写清楚，并给出对应的按钮（重试 / 打开无障碍设置 / 手动打开胖乖）。
 * 绝不静默失败。
 */
class JumpActivity : AppCompatActivity() {

    private lateinit var tvStep: TextView
    private lateinit var progress: ProgressBar
    private lateinit var actionBox: View
    private lateinit var btnPrimary: TextView
    private lateinit var btnSecondary: TextView
    private lateinit var btnSettings: TextView

    private val handler = Handler(Looper.getMainLooper())

    /** 防重复执行：从桌面连点两下时，只跑第一轮。 */
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jump)

        tvStep = findViewById(R.id.tvStep)
        progress = findViewById(R.id.progress)
        actionBox = findViewById(R.id.actionBox)
        btnPrimary = findViewById(R.id.btnPrimary)
        btnSecondary = findViewById(R.id.btnSecondary)
        btnSettings = findViewById(R.id.btnSettings)

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        QLog.i("Life", "═══ JumpActivity 启动 ═══")
        QLog.i(
            "Life",
            "调试模式=${Prefs.dryRun(this)}  等待=${Prefs.waitMs(this)}ms  " +
                "待命窗口=${Prefs.windowMs(this)}ms"
        )
        QLog.i("Life", "胖乖版本=${Qk.versionName(this)}  已安装=${Qk.isInstalled(this)}")

        runJump()
    }

    override fun onResume() {
        super.onResume()
        // 从系统无障碍设置返回时，授权可能刚变。这里顺手自愈一次，并把状态刷新到界面上。
        if (!running) {
            if (A11yGuard.heal(this)) {
                QLog.i("Life", "onResume：无障碍授权在位")
            }
        }
    }

    // ── 主流程 ────────────────────────────────────────────────────────────

    private fun runJump() {
        if (running) {
            QLog.i("Jump", "已经在跑了，忽略重复触发")
            return
        }
        running = true

        showProgress(getString(R.string.jump_step_checking))
        hideActions()

        // 检查包是否安装
        if (!Qk.isInstalled(this)) {
            QLog.e("Jump", "胖乖生活未安装，停在本页")
            fail(
                getString(R.string.jump_step_not_installed),
                primaryText = getString(R.string.jump_step_retry),
                primaryAction = { runJump() },
                secondaryText = null,
                secondaryAction = null
            )
            return
        }

        // 检查启动入口
        val intent = Qk.buildLaunchIntent(this)
        if (intent == null) {
            QLog.e("Jump", "启动入口 resolve 为 null，停在本页")
            fail(
                getString(R.string.jump_step_no_entry),
                primaryText = getString(R.string.jump_step_retry),
                primaryAction = { runJump() },
                secondaryText = getString(R.string.jump_step_open_app),
                secondaryAction = { manualOpen() }
            )
            return
        }
        QLog.i("Jump", "启动 Intent = ${Qk.dumpIntent(intent)}")
        QLog.i(
            "Jump",
            "resolveActivity = ${resolveOf(intent)}"
        )

        val dry = Prefs.dryRun(this)

        // 冷启时 onResume 还没跑，而 MIUI 在 force-stop 时会把无障碍授权清空。
        // 如果等 onResume 再去自愈，这一轮就会误判成"服务没开"，
        // 把用户推到一个其实不用他处理的页面。所以判定之前先补一次。
        if (!ScanTap.isServiceConnected(this)) {
            QLog.i("Jump", "判定前发现授权不在位，先尝试自愈再判断")
            A11yGuard.heal(this)
        }
        val a11y = ScanTap.isServiceConnected(this)
        QLog.i("Jump", "调试模式=$dry  无障碍已连接=$a11y")

        if (dry) {
            // 调试模式：只把计划写出来，不跳、不点。
            QLog.i("Jump", "调试模式：不会执行跳转，也不会点击")
            showStep(getString(R.string.jump_step_dry_run) + "\n\n将要执行：\n" + Qk.dumpIntent(intent))
            hideProgress()
            showActions(
                primaryText = getString(R.string.jump_step_retry),
                primaryAction = { runJump() },
                secondaryText = getString(R.string.jump_step_settings),
                secondaryAction = { startActivity(Intent(this, SettingsActivity::class.java)) }
            )
            running = false
            return
        }

        if (!a11y) {
            // 没有无障碍也能拉起胖乖，只是没法代点。仍然跳，但先把话说清楚。
            QLog.w("Jump", "无障碍未连接：可以拉起胖乖，但无法代点扫描图标")
            showStep(getString(R.string.jump_step_no_a11y))
            hideProgress()
            showActions(
                primaryText = getString(R.string.jump_step_retry),
                primaryAction = { runJump() },
                secondaryText = getString(R.string.jump_step_open_a11y),
                secondaryAction = { openA11ySettings() }
            )
            running = false
            return
        }

        // 三步都过 → 待命 + 跳
        ScanTap.arm(this)

        showProgress(getString(R.string.jump_step_launching))
        handler.postDelayed({
            try {
                QLog.i("Jump", "执行 startActivity")
                startActivity(intent)
                QLog.i("Jump", "✓ 已拉起，等待无障碍在首页点扫描图标")
                Prefs.setLastResult(this, "已拉起胖乖生活")
                // 本页使命完成，退到后台，让胖乖占据前台。
                // 不退得太急：有些机型会在切换瞬间把刚起来的 Activity 一起带走。
                handler.postDelayed({
                    if (!isFinishing) {
                        QLog.i("Life", "跳转完成后把本页退到后台")
                        moveTaskToBack(true)
                    }
                }, 900L)
            } catch (t: Throwable) {
                val reason = when (t) {
                    is ActivityNotFoundException -> "ActivityNotFound：${QLog.describe(t)}"
                    is SecurityException -> "Security：${QLog.describe(t)}"
                    else -> QLog.describe(t)
                }
                QLog.e("Jump", "拉起失败：$reason", t)
                Prefs.setLastResult(this, "拉起失败：$reason")
                fail(
                    getString(R.string.jump_step_launch_failed, reason),
                    primaryText = getString(R.string.jump_step_retry),
                    primaryAction = { runJump() },
                    secondaryText = getString(R.string.jump_step_open_app),
                    secondaryAction = { manualOpen() }
                )
            }
        }, 260L)
    }

    // ── UI ────────────────────────────────────────────────────────────────

    private fun showStep(text: String) {
        tvStep.text = text
    }

    private fun showProgress(text: String) {
        tvStep.text = text
        progress.visibility = View.VISIBLE
    }

    private fun hideProgress() {
        progress.visibility = View.GONE
    }

    private fun hideActions() {
        actionBox.visibility = View.GONE
    }

    private fun showActions(
        primaryText: String,
        primaryAction: () -> Unit,
        secondaryText: String?,
        secondaryAction: (() -> Unit)?
    ) {
        btnPrimary.text = primaryText
        btnPrimary.setOnClickListener { primaryAction() }
        if (secondaryText != null && secondaryAction != null) {
            btnSecondary.text = secondaryText
            btnSecondary.visibility = View.VISIBLE
            btnSecondary.setOnClickListener { secondaryAction() }
        } else {
            btnSecondary.visibility = View.GONE
        }
        btnPrimary.setBackgroundResource(R.drawable.bg_button)
        btnSecondary.setBackgroundResource(R.drawable.bg_button_outline)
        actionBox.visibility = View.VISIBLE
    }

    private fun fail(
        message: String,
        primaryText: String,
        primaryAction: () -> Unit,
        secondaryText: String?,
        secondaryAction: (() -> Unit)?
    ) {
        QLog.e("Jump", "失败：$message")
        showStep(message)
        hideProgress()
        showActions(primaryText, primaryAction, secondaryText, secondaryAction)
        Prefs.setLastResult(this, message.lineSequence().first())
        running = false
    }

    // ── 辅助 ──────────────────────────────────────────────────────────────

    private fun resolveOf(intent: Intent): String = try {
        packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.let { "${it.packageName}/${it.name}" } ?: "null"
    } catch (t: Throwable) {
        "查 resolve 失败：${QLog.describe(t)}"
    }

    private fun manualOpen() {
        val i = Qk.buildLaunchIntent(this)
        if (i == null) {
            QLog.w("Jump", "手动打开也拿不到 Intent")
            return
        }
        try {
            startActivity(i)
        } catch (t: Throwable) {
            QLog.e("Jump", "手动打开失败：${QLog.describe(t)}", t)
        }
    }

    private fun openA11ySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (t: Throwable) {
            QLog.e("Jump", "打开无障碍设置失败：${QLog.describe(t)}", t)
        }
    }
}
