package com.local.qkjump

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * 无障碍授权自愈。
 *
 * 为什么需要：MIUI 把「从最近任务划掉卡片」当作强制停止，会清空
 * accessibility_enabled 与 enabled_accessibility_services 两个 secure setting，
 * 清完之后系统不会自动绑回来，用户必须重新进系统设置点一遍。
 *
 * 本对象拿到 WRITE_SECURE_SETTINGS 之后能把授权写回去，用户只要重新打开一次应用即可。
 * 没拿到权限时所有方法都是安全的空操作，只打一行日志。
 */
object A11yGuard {

    private fun component(context: Context): String =
        ComponentName(context, ScanTapService::class.java).flattenToString()

    /** 是否有权写 secure setting。没这个权限，自愈功能不可用（其余功能不受影响）。 */
    fun canWrite(context: Context): Boolean = try {
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /** 读当前是否已启用。有权限走 Secure 表，没权限退回 AccessibilityManager。 */
    fun isEnabled(context: Context): Boolean {
        if (canWrite(context)) {
            val raw = try {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            } catch (t: Throwable) {
                null
            }
            val mine = component(context)
            if (raw != null) {
                return raw.split(':').any { it.equals(mine, ignoreCase = true) }
            }
        }
        // 无权限时的退路：问系统当前绑了哪些服务。
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val list = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            list.any { info ->
                info.resolveInfo?.serviceInfo?.let { si ->
                    si.packageName == context.packageName && si.name.endsWith("ScanTapService")
                } == true
            }
        } catch (t: Throwable) {
            QLog.w("A11y", "问 AccessibilityManager 失败：${QLog.describe(t)}")
            false
        }
    }

    /**
     * 尝试把授权写回去。返回 true 表示本次确实写了（或本来就在位）。
     *
     * 写序不能反：先 accessibility_enabled=1，再 enabled_accessibility_services=...。
     * 实测反着写会把它重置回 0。
     */
    fun heal(context: Context): Boolean {
        if (isEnabled(context)) {
            QLog.i("A11y", "无障碍授权在位，无需自愈")
            return true
        }
        if (!canWrite(context)) {
            QLog.w(
                "A11y",
                "授权不在位，但没有 WRITE_SECURE_SETTINGS，无法自愈。需要时执行：\n" +
                    "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
            )
            return false
        }

        val cr = context.contentResolver
        val mine = component(context)
        val before = try {
            Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        } catch (t: Throwable) {
            ""
        }
        // 追加而不是覆盖：别的无障碍服务（例如系统自带的）不能被我抹掉。
        val after = if (before.isBlank()) mine else "$before:$mine"

        QLog.i("A11y", "检测到无障碍授权被清空（多半是从最近任务划掉了 App），开始写回")
        QLog.i("A11y", "  原有服务列表=$before")
        QLog.i("A11y", "  写回后=$after")

        val okInt = try {
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        } catch (t: Throwable) {
            QLog.w("A11y", "写 accessibility_enabled 失败：${QLog.describe(t)}")
            false
        }
        val okStr = try {
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, after)
        } catch (t: Throwable) {
            QLog.w("A11y", "写 enabled_accessibility_services 失败：${QLog.describe(t)}")
            false
        }
        QLog.i("A11y", "  putInt(accessibility_enabled)=$okInt  putString(services)=$okStr")

        val confirmed = isEnabled(context)
        if (confirmed) {
            QLog.i("A11y", "✓ 自愈成功，无障碍服务已重新写入（系统会在数秒内绑定）")
        } else {
            QLog.w("A11y", "写回后复核仍为未启用，可能需要重装或系统限制更严")
        }
        return confirmed
    }

    /** 给设置页显示的一行状态。 */
    fun statusLine(context: Context): String {
        val enabled = isEnabled(context)
        val writable = canWrite(context)
        return when {
            enabled && writable -> "已启用，且可自愈"
            enabled && !writable -> "已启用（无自愈权限，划卡后需手动重开）"
            !enabled && writable -> "未启用（有自愈权限，下次打开应用会自动写回）"
            else -> "未启用，且无自愈权限"
        }
    }
}
