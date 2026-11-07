package com.local.qkjump

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * 关于「胖乖生活」这个 App 的全部已知事实都集中在这里，方便日后一处改动。
 *
 * 下面每一条都是 2026-11-07 在这台机器（Redmi K40S / Android 14 / 胖乖 1.145.0）上
 * 用只读手段量出来的，不是猜的：
 *
 *   1. 它的包名是 com.qiekj.user。
 *   2. 主界面 MainActivity 是 exported=true 并且声明了 MAIN/LAUNCHER，所以可以正当地拉起。
 *   3. 首页右上角那个扫描图标，资源 id 是 com.qiekj.user:id/iv_scan，
 *      当天实测 bounds=[777,127][1071,254]，点它会进 HomeScanCodeAct。
 *   4. HomeScanCodeAct（扫一扫页）是 exported=false —— 外部无法直接跳，只能从 App 内部点进去。
 *      这正是本应用必须借助无障碍「代点一下」的原因。
 *   5. 它没有声明任何 http/https 的 host，所以系统不会把机身二维码里的
 *      https://h5.qiekj.com/skip?SN=... 交给它；那条链接只会落到浏览器。
 *      因此本应用不去「喂」URL，只是把用户送到它自己的扫一扫页，让它自己扫。
 */
object Qk {

    /** 胖乖生活的包名。 */
    const val PKG = "com.qiekj.user"

    /** 首页右上角扫描图标的资源 id（实测值）。 */
    const val SCAN_VIEW_ID = "com.qiekj.user:id/iv_scan"

    /**
     * 判定「已经在胖乖首页」的文本标记。
     * 用两个而不是一个：不同版本首页可能改动，命中任意一个即可。
     * 「附近设备」是首页顶部横向栏里固定存在的一项。
     */
    val HOME_MARKERS = listOf("附近设备", "饮水")

    /**
     * 判定「已经进到扫一扫页」的文本标记。
     * 实测该页有「将二维码/条形码放入框内，即可自动扫描」「轻点照亮」「相册」。
     * 只取最长的那句，误判概率最低。
     */
    val SCAN_MARKERS = listOf("将二维码/条形码放入框内", "轻点照亮")

    /** 装没装。查不到就是没装。 */
    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PKG, 0)
        true
    } catch (t: Throwable) {
        QLog.w("Jump", "查 $PKG 失败：${QLog.describe(t)}")
        false
    }

    /** 安装包版本号，纯日志用。取不到返回 "?"。 */
    fun versionName(context: Context): String = try {
        val info = context.packageManager.getPackageInfo(PKG, 0)
        info.versionName ?: "?"
    } catch (t: Throwable) {
        "?"
    }

    /**
     * 拉起胖乖生活主界面的 Intent。
     *
     * 两种写法按序尝试，都属于公开入口：
     *   a) 按 LAUNCHER intent 查（等价于用户点桌面图标）；
     *   b) 直接点名 MainActivity（manifest 里 exported=true）。
     * 任一能 resolve 就用它。返回 null 表示这台机器上拉不起来。
     */
    fun buildLaunchIntent(context: Context): Intent? {
        val pm = context.packageManager

        val byLauncher = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(PKG)
        }
        val resolved = pm.resolveActivity(byLauncher, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolved != null) {
            QLog.i("Jump", "a) LAUNCHER 入口可用：${resolved.activityInfo.packageName}/${resolved.activityInfo.name}")
            return byLauncher.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
        QLog.w("Jump", "a) LAUNCHER 入口 resolve 为 null，改试点名 MainActivity")

        val byComponent = Intent().apply {
            setClassName(PKG, "$PKG.MainActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolved2 = pm.resolveActivity(byComponent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolved2 != null) {
            QLog.i("Jump", "b) 点名入口可用：${resolved2.activityInfo.name}")
            return byComponent
        }
        QLog.e("Jump", "b) 两条入口都 resolve 为 null，判定无法拉起")
        return null
    }

    /** 把 Intent 拍平成一行，写进日志。 */
    fun dumpIntent(i: Intent): String {
        val comp = i.component?.let { "${it.packageName}/${it.className}" } ?: "null"
        return "action=${i.action} data=${i.data} pkg=${i.`package`} component=$comp " +
            "categories=${i.categories} flags=0x${Integer.toHexString(i.flags)}"
    }
}
