package com.local.qkjump

import android.content.Context
import android.os.SystemClock

/**
 * 待命窗口 —— 本工程唯一涉及「自动化点一下」的状态机。
 *
 * 为什么需要它：本应用跳走之后，无障碍服务是常驻的，它会看到屏幕上所有的界面变化。
 * 如果不设边界，那它在别的场景里也可能去点东西 —— 那不是这个应用该做的事。
 * 所以规则写死成四条，任何一条不满足就绝不动手：
 *
 *   1. 必须刚刚由本应用触发过一次跳转（窗口期内）；
 *   2. 当前界面必须属于 com.qiekj.user（服务里再校验一次）；
 *   3. 当前界面必须已经是胖乖的首页（出现「附近设备」之类的标记）；
 *   4. 一台设备只点一次：点完立刻退出待命，窗口内不会点第二下。
 *
 * 窗口默认 20 秒 —— 够冷启动一个大 App，又不至于挂着太久。
 */
object ScanTap {

    private var armedAt: Long = 0L
    private var fired: Boolean = false
    private var armedWindowMs: Int = Prefs.DEFAULT_WINDOW_MS

    /** 进入待命。由 JumpActivity / SettingsActivity 的测试入口调用。 */
    fun arm(context: Context) {
        armedWindowMs = Prefs.windowMs(context)
        armedAt = SystemClock.elapsedRealtime()
        fired = false
        QLog.i("Tap", "进入待命（窗口 ${armedWindowMs}ms），只在胖乖首页上点一下扫描图标")
    }

    /** 主动退出待命，附原因。 */
    fun disarm(reason: String) {
        if (armedAt == 0L) return
        armedAt = 0L
        fired = false
        QLog.i("Tap", "退出待命：$reason")
    }

    /** 现在是否在窗口内。超时会自动退出并打日志。 */
    fun isArmed(): Boolean {
        if (armedAt == 0L) return false
        val elapsed = SystemClock.elapsedRealtime() - armedAt
        if (elapsed > armedWindowMs) {
            QLog.i("Tap", "待命已超时（${elapsed}ms > ${armedWindowMs}ms），不再动手")
            armedAt = 0L
            fired = false
            return false
        }
        return true
    }

    fun isFired(): Boolean = fired

    /**
     * 抢占「点一次」的资格。返回 true 表示这一次归你，调用方去点；
     * 返回 false 表示已经点过或不在窗口内，调用方必须什么都不做。
     */
    fun claimOnce(): Boolean {
        if (!isArmed()) return false
        if (fired) {
            QLog.i("Tap", "本次待命已经点过一下，不再重复")
            return false
        }
        fired = true
        return true
    }

    /**
     * 无障碍服务是否已被系统绑定。
     *
     * 双路判断，因为单路都不可靠：
     *   a) 读 Secure.ENABLED_ACCESSIBILITY_SERVICES —— 需要 WRITE_SECURE_SETTINGS，没给就跳过；
     *   b) 读服务自己写的回执（带 pid）。
     */
    fun isServiceConnected(context: Context): Boolean {
        val secure = try {
            val raw = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            val me = android.content.ComponentName(context, ScanTapService::class.java).flattenToString()
            raw?.split(':')?.any { it.equals(me, ignoreCase = true) } == true
        } catch (t: Throwable) {
            QLog.w("A11y", "读 Secure 设置失败（缺 WRITE_SECURE_SETTINGS 时会这样）：${QLog.describe(t)}")
            null
        }
        if (secure == true) return true
        return Prefs.a11yConnected(context)
    }
}
