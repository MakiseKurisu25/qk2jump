package com.local.qkjump

import android.util.Log

/**
 * 统一日志。
 *
 * 全工程只有这一个 TAG：`QkJump`。这样 `adb logcat -s QkJump:V` 就能看到全链路，
 * 不用记多个标签。
 *
 * area 是区段名，约定：
 *   Life  —— 生命周期
 *   Jump  —— 拉起胖乖的过程
 *   Tap   —— 无障碍找控件、点下去
 *   A11y  —— 无障碍授权本身
 *   Set   —— 设置页
 */
object QLog {

    const val TAG = "QkJump"

    fun i(area: String, msg: String) {
        Log.i(TAG, "[$area] $msg")
    }

    fun w(area: String, msg: String) {
        Log.w(TAG, "[$area] $msg")
    }

    fun e(area: String, msg: String, t: Throwable? = null) {
        if (t == null) Log.e(TAG, "[$area] $msg") else Log.e(TAG, "[$area] $msg", t)
    }

    /** 把异常压缩成一行，避免 logcat 被栈刷爆。要栈就传 Throwable 给 e()。 */
    fun describe(t: Throwable): String {
        val frame = t.stackTrace.firstOrNull()
        val where = if (frame == null) "?" else "${frame.className}.${frame.methodName}:${frame.lineNumber}"
        return "${t.javaClass.simpleName}: ${t.message} @$where"
    }
}
