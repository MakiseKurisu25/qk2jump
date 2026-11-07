package com.local.qkjump

import android.content.Context
import android.content.SharedPreferences

/**
 * 设置项。只有五项，全部与「怎么把用户送到扫码页」有关，没有一条跟账号、订单、金额沾边。
 */
object Prefs {

    const val FILE = "qkjump_prefs"

    /** 调试模式：只打日志，不真的拉起胖乖、不真的点。默认关。 */
    private const val KEY_DRY_RUN = "dry_run"

    /** 进入首页后，等多久才开始找扫描图标（毫秒）。首页有广告位和 WebView，慢。 */
    private const val KEY_WAIT_MS = "wait_ms"

    /** 待命窗口：从本应用跳过去之后，多久之内允许点一下（毫秒）。窗口外不动作。 */
    private const val KEY_WINDOW_MS = "window_ms"

    /** 无障碍连接回执（由服务自己写，带 pid 防过期）。 */
    private const val KEY_A11Y_CONNECTED = "a11y_connected"
    private const val KEY_A11Y_PID = "a11y_pid"

    /** 最近一次执行结果，回到设置页能看见。 */
    private const val KEY_LAST_RESULT = "last_result"

    const val DEFAULT_WAIT_MS = 2500
    const val MAX_WAIT_MS = 10_000
    const val DEFAULT_WINDOW_MS = 20_000
    const val MAX_WINDOW_MS = 60_000

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun dryRun(c: Context): Boolean = sp(c).getBoolean(KEY_DRY_RUN, false)
    fun setDryRun(c: Context, v: Boolean) = sp(c).edit().putBoolean(KEY_DRY_RUN, v).apply()

    fun waitMs(c: Context): Int = sp(c).getInt(KEY_WAIT_MS, DEFAULT_WAIT_MS).coerceIn(0, MAX_WAIT_MS)
    fun setWaitMs(c: Context, v: Int) =
        sp(c).edit().putInt(KEY_WAIT_MS, v.coerceIn(0, MAX_WAIT_MS)).apply()

    fun windowMs(c: Context): Int =
        sp(c).getInt(KEY_WINDOW_MS, DEFAULT_WINDOW_MS).coerceIn(1_000, MAX_WINDOW_MS)

    fun setWindowMs(c: Context, v: Int) =
        sp(c).edit().putInt(KEY_WINDOW_MS, v.coerceIn(1_000, MAX_WINDOW_MS)).apply()

    /**
     * 无障碍连接回执。
     *
     * 必须连 pid 一起存：`am force-stop` 杀进程时系统不会回调 onUnbind，
     * 旧的 `true` 会留在盘上，下次冷启就会误报「已连接」。带上自己的 pid 之后，
     * 进程一换，pid 对不上，回执自动作废。
     */
    fun a11yConnected(c: Context): Boolean =
        sp(c).getBoolean(KEY_A11Y_CONNECTED, false) &&
            sp(c).getInt(KEY_A11Y_PID, -1) == android.os.Process.myPid()

    fun setA11yConnected(c: Context, v: Boolean) =
        sp(c).edit()
            .putBoolean(KEY_A11Y_CONNECTED, v)
            .putInt(KEY_A11Y_PID, if (v) android.os.Process.myPid() else -1)
            .apply()

    fun lastResult(c: Context): String = sp(c).getString(KEY_LAST_RESULT, "") ?: ""
    fun setLastResult(c: Context, v: String) = sp(c).edit().putString(KEY_LAST_RESULT, v).apply()
}
