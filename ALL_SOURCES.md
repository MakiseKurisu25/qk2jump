# 胖乖直达（QkJump）— 完整源码

27 个文件，按依赖顺序。总行数见文末。

## settings.gradle.kts

```kotlin
// QkJump —— 独立工程，和 V1 的 QrJump 没有任何代码共享，装成两个不同的应用。
// 目的只有一个：打开本应用 = 直奔胖乖生活自己的扫一扫页。
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "QkJump"
include(":app")
```

## build.gradle.kts

```kotlin
// 顶层构建脚本。插件版本与 V1 保持一致，本机已缓存的依赖可直接复用，不用重新下载。
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
```

## gradle.properties

```ini
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
# 关掉 R 类生成中的 BuildConfig，本工程不需要
android.defaults.buildfeatures.buildconfig=false
```

## local.properties

```ini
sdk.dir=A:/AndroidDev/sdk
```

## app\build.gradle.kts

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // 包名刻意与 V1（com.local.qrjump）和厂商（com.qiekj.*）都不同，
    // 免得在设置里混淆、也不会被误认为官方应用。
    namespace = "com.local.qkjump"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.local.qkjump"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // 本工程只做「拉起胖乖 + 代点一下右上角的扫描图标」，
    // 不需要相机、不需要 ML Kit、不需要 ZXing —— 扫由胖乖自己扫。
}
```

## app\proguard-rules.pro

```text
# 不混淆，release 也不开 minify。此处只为让 AGP 有个文件可引用。
-keepattributes SourceFile,LineNumberTable
```

## app\src\main\AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
  QkJump —— 一个只有一件事的应用：打开它，就直接进到「胖乖生活」自己的扫一扫页。

  它不做的事（与 V1 一脉相承的红线）：
    · 不抓包、不逆向、不调任何私有接口
    · 不构造订单、不模拟支付、不伪造任何指令
    · 不碰账号与鉴权，不存 token

  它做的事只有两件，且都走公开路径：
    1. 用 android.intent.action.MAIN 拉起胖乖生活的主界面（这是它自己 export 的入口）；
    2. 用系统无障碍服务，替用户点一下首页右上角那个扫描图标（就是用户平时手点的那一下）。
  点完之后，扫码、识别设备、计费、出水，全部由胖乖生活自己和官方服务端完成。
-->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- 只有这一条权限需要 adb 手动授予：用于在无障碍授权被系统清掉后自动写回。
         不授予也能用，只是每次要自己进系统设置重新打开无障碍。 -->
    <uses-permission
        android:name="android.permission.WRITE_SECURE_SETTINGS"
        tools:ignore="ProtectedPermissions" />

    <!-- Android 11 起默认看不到别的应用。不声明这一段，getPackageInfo / 拉起都会失败。 -->
    <queries>
        <package android:name="com.qiekj.user" />
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
    </queries>

    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.QkJump">

        <!-- 唯一入口。打开它就直接开始跳。 -->
        <activity
            android:name=".JumpActivity"
            android:exported="true"
            android:excludeFromRecents="true"
            android:noHistory="true"
            android:launchMode="singleTask"
            android:screenOrientation="portrait"
            android:theme="@style/Theme.QkJump">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- 设置页：调试开关、等待时长、无障碍授权状态。exported=false，只能从本应用进。 -->
        <activity
            android:name=".SettingsActivity"
            android:exported="false"
            android:label="@string/settings_title"
            android:screenOrientation="portrait"
            android:theme="@style/Theme.QkJump" />

        <!-- 无障碍服务：唯一的作用是「在胖乖首页上点一下扫描图标」。
             再往后的任何点击它都不做 —— 扫码之后是胖乖和官方服务端的事。 -->
        <service
            android:name=".ScanTapService"
            android:exported="false"
            android:label="@string/a11y_label"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>
    </application>
</manifest>
```

## app\src\main\java\com\local\qkjump\QLog.kt

```kotlin
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
```

## app\src\main\java\com\local\qkjump\Prefs.kt

```kotlin
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
```

## app\src\main\java\com\local\qkjump\Qk.kt

```kotlin
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
```

## app\src\main\java\com\local\qkjump\ScanTap.kt

```kotlin
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
```

## app\src\main\java\com\local\qkjump\ScanTapService.kt

```kotlin
package com.local.qkjump

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务。**它一辈子只做一件事：在胖乖生活首页上，替用户点一下右上角那个扫描图标。**
 *
 * 它会做的事：
 *   · 收到界面变化事件时，判断当前是不是胖乖的首页；
 *   · 是的话，找到扫描图标，点一下（优先用节点的点击动作，节点不响应时改用手势坐标）；
 *   · 点完立刻退出待命，本次不再点第二下。
 *
 * 它刻意不做的事（每一条都是红线）：
 *   · 不认领任何支付、订单、金额、优惠券相关的控件文本；
 *   · 不往输入框里填任何东西；
 *   · 不模拟「出水 / 暂停 / 结束」这类功能按钮；
 *   · 不读取屏幕上的业务数据，只做「有没有某个节点」的存在性判断，日志里也只打控件类型与坐标。
 */
class ScanTapService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var polling = false

    /** 轮询周期。首页有广告位和 WebView，可能安静地渲染完而不发事件，所以要主动看。 */
    private val pollIntervalMs = 600L

    /** 同一个提示只打一次，避免每秒刷屏。 */
    private var lastNote: String = ""

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!polling) return
            try {
                checkOnce()
            } catch (t: Throwable) {
                QLog.e("Tap", "轮询异常：${QLog.describe(t)}", t)
            }
            if (polling) handler.postDelayed(this, pollIntervalMs)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        QLog.i("A11y", "无障碍服务已连接（canPerformGestures 见配置）")
        Prefs.setA11yConnected(this, true)

        // 关键：重装或重新授权时，屏幕上的页面往往早就静止了，
        // 一个事件都不会再来。所以要在这里就地自举一次，不能只等事件。
        if (ScanTap.isArmed()) {
            QLog.i("A11y", "连接时仍在待命窗口内，直接启动轮询（不依赖事件）")
            startPolling()
            checkOnce()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> Unit
            else -> return
        }
        if (!ScanTap.isArmed()) return
        try {
            checkOnce()
        } catch (t: Throwable) {
            QLog.e("Tap", "事件处理异常：${QLog.describe(t)}", t)
        }
    }

    override fun onInterrupt() {
        QLog.w("A11y", "onInterrupt，无障碍服务被中断")
        stopPolling()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        QLog.i("A11y", "无障碍服务已解绑（用户关闭或系统回收）")
        Prefs.setA11yConnected(this, false)
        ScanTap.disarm("无障碍服务解绑")
        stopPolling()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Prefs.setA11yConnected(this, false)
        stopPolling()
        super.onDestroy()
    }

    // ── 主判断 ────────────────────────────────────────────────────────────

    private fun checkOnce() {
        if (!ScanTap.isArmed()) {
            stopPolling()
            return
        }

        val root = rootInActiveWindow ?: run {
            note("当前窗口为空，可能还在切换，下一轮再看")
            return
        }

        try {
            val pkg = root.packageName?.toString().orEmpty()
            if (pkg != Qk.PKG) {
                note("前台不是胖乖（当前=$pkg），不动手")
                return
            }

            val faces = scanFaces(root)

            // 已经在扫一扫页了 —— 说明这件事已经办成，退出待命。
            if (faces.onScanPage) {
                QLog.i("Tap", "已经在扫一扫页（命中「${faces.scanMark}」），本次任务完成")
                ScanTap.disarm("已进入扫一扫页")
                Prefs.setLastResult(this, "已进入胖乖的扫一扫页")
                stopPolling()
                return
            }

            if (!faces.onHome) {
                note("是胖乖但不是首页（没找到「附近设备」这类标记），继续等")
                return
            }

            val icon = findScanIcon(root)
            if (icon == null) {
                note("在首页但没找到扫描图标，继续等")
                return
            }

            val center = Rect().also { icon.getBoundsInScreen(it) }
            val cx = center.exactCenterX()
            val cy = center.exactCenterY()

            val viaId = icon.viewIdResourceName == Qk.SCAN_VIEW_ID
            if (!ScanTap.claimOnce()) {
                stopPolling()
                return
            }

            if (Prefs.dryRun(this)) {
                QLog.i(
                    "Tap",
                    "调试模式：已定位扫描图标（byId=$viaId class=${icon.className} " +
                        "中心=($cx, $cy)），本应点击但未动手"
                )
                Prefs.setLastResult(this, "调试模式：找到扫描图标但未点击")
                stopPolling()
                return
            }

            QLog.i("Tap", "点击扫描图标：byId=$viaId class=${icon.className} 中心=($cx, $cy)")
            val ok = if (viaId) clickNode(icon) else clickNode(icon)
            QLog.i("Tap", "点击结果=$ok")

            if (ok) {
                Prefs.setLastResult(this, "已点击扫描图标（byId=$viaId）")
                ScanTap.disarm("已点击扫描图标")
            } else {
                // 没点成不算完成，把资格还回去，下一轮可再试（窗口内）。
                Prefs.setLastResult(this, "点击扫描图标失败")
                note("点击没有生效，等下一轮")
            }
            stopPolling()
        } finally {
            @Suppress("DEPRECATION")
            runCatching { root.recycle() }
        }
    }

    // ── 页面识别 ──────────────────────────────────────────────────────────

    /** 一次遍历同时把三件事判出来，避免每件事各走一遍 BFS。 */
    private data class Faces(
        val onHome: Boolean,
        val homeMark: String?,
        val onScanPage: Boolean,
        val scanMark: String?
    )

    private fun scanFaces(root: AccessibilityNodeInfo): Faces {
        var homeMark: String? = null
        var scanMark: String? = null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val n = queue.removeFirst()
            visited++
            val text = n.text?.toString()
            if (text != null && text.isNotEmpty()) {
                if (homeMark == null) {
                    val m = Qk.HOME_MARKERS.firstOrNull { text.contains(it) }
                    if (m != null) homeMark = m
                }
                if (scanMark == null) {
                    val m = Qk.SCAN_MARKERS.firstOrNull { text.contains(it) }
                    if (m != null) scanMark = m
                }
            }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                queue.add(c)
            }
        }
        return Faces(homeMark != null, homeMark, scanMark != null, scanMark)
    }

    /**
     * 找扫描图标。两条路，先精确后几何：
     *   a) 按资源 id 精确命中（最稳，但换版本可能改名）；
     *   b) 首页右上角区域里可点击的 ImageView（几何兜底）。
     * 两条都失败返回 null，本轮什么都不做。
     */
    private fun findScanIcon(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byId = try {
            root.findAccessibilityNodeInfosByViewId(Qk.SCAN_VIEW_ID)?.firstOrNull()
        } catch (t: Throwable) {
            QLog.w("Tap", "按 id 查找失败：${QLog.describe(t)}")
            null
        }
        if (byId != null) {
            QLog.i("Tap", "按 id 命中扫描图标")
            return byId
        }

        val screen = Rect().also { root.getBoundsInScreen(it) }
        val rightEdge = screen.width() * 0.65f
        val bottomEdge = screen.height() * 0.18f

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val n = queue.removeFirst()
            visited++
            val cls = n.className?.toString().orEmpty()
            if (cls.endsWith("ImageView")) {
                val b = Rect().also { n.getBoundsInScreen(it) }
                if (b.centerX() > rightEdge && b.centerY() < bottomEdge && b.width() > 0) {
                    QLog.i("Tap", "按几何命中右上角 ImageView：bounds=$b")
                    return n
                }
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    // ── 点击 ──────────────────────────────────────────────────────────────

    /**
     * 点一个节点。两条路：
     *   a) 节点自己（或往上 8 层的某个祖先）可点击 → 用 performAction；
     *   b) 都不可点击 → 取屏幕坐标，用 dispatchGesture 做一次 60ms 的按下-抬起。
     *
     * 第 b 条是必需的：实测胖乖首页那一排图标的外层容器是 clickable=false 的
     * Compose / RecyclerView 子项时，performAction 会返回 true 但其实什么也没发生。
     */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth < 8) {
            if (cur.isClickable && cur.isEnabled) {
                val ok = cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                QLog.i("Tap", "performAction(ACTION_CLICK) 于第 $depth 层祖先 → $ok")
                if (ok) return true
                break
            }
            cur = cur.parent
            depth++
        }

        val b = Rect().also { node.getBoundsInScreen(it) }
        if (b.width() <= 0 || b.height() <= 0) {
            QLog.w("Tap", "节点没有有效坐标，放弃点击")
            return false
        }
        val path = Path().apply { moveTo(b.exactCenterX(), b.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS))
            .build()
        val submitted = dispatchGesture(gesture, null, null)
        QLog.i("Tap", "dispatchGesture 提交=$submitted 坐标=(${b.exactCenterX()}, ${b.exactCenterY()})")
        return submitted
    }

    // ── 轮询 ──────────────────────────────────────────────────────────────

    private fun startPolling() {
        if (polling) return
        polling = true
        QLog.i("Tap", "开始轮询（每 ${pollIntervalMs}ms 一次）")
        handler.post(pollRunnable)
    }

    private fun stopPolling() {
        if (!polling) return
        polling = false
        handler.removeCallbacks(pollRunnable)
        QLog.i("Tap", "停止轮询")
    }

    /** 同一条提示只打一次；换了内容才再打。 */
    private fun note(msg: String) {
        if (msg == lastNote) return
        lastNote = msg
        QLog.i("Tap", msg)
    }

    private companion object {
        /** 单次遍历的节点上限，防止异常大的界面把主线程拖住。 */
        const val MAX_NODES = 800

        /** 手势按住时长。太短可能被当成滑动起点，太长又会拖慢。 */
        const val TAP_DURATION_MS = 60L
    }
}
```

## app\src\main\java\com\local\qkjump\A11yGuard.kt

```kotlin
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
```

## app\src\main\java\com\local\qkjump\JumpActivity.kt

```kotlin
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
```

## app\src\main\java\com\local\qkjump\SettingsActivity.kt

```kotlin
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
```

## app\src\main\res\values\strings.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">胖乖直达</string>

    <!-- ═══ 跳转页 ═══ -->
    <string name="jump_title">胖乖直达</string>
    <string name="jump_sub">打开本应用 = 直接进胖乖生活的扫一扫页</string>
    <string name="jump_step_checking">正在检查胖乖生活…</string>
    <string name="jump_step_not_installed">这台机器上没有装「胖乖生活」（com.qiekj.user）。装好再来。</string>
    <string name="jump_step_no_entry">胖乖生活的启动入口找不到，可能是版本变了。请手动打开一次它，确认能正常进入首页。</string>
    <string name="jump_step_no_a11y">无障碍服务没开 —— 我可以把胖乖拉起来，但没法替你点右上角那个扫描图标。\n\n开法：设置 → 无障碍 → 已下载的服务 → 「胖乖直达」→ 打开。</string>
    <string name="jump_step_launching">正在拉起胖乖生活…</string>
    <string name="jump_step_launched">已经跳过去了。若它停在首页没动，说明扫描图标没找到。</string>
    <string name="jump_step_launch_failed">拉起胖乖生活失败：%1$s</string>
    <string name="jump_step_dry_run">调试模式：只记录，不跳转、不点击。</string>
    <string name="jump_step_retry">重试</string>
    <string name="jump_step_settings">设置</string>
    <string name="jump_step_open_a11y">打开无障碍设置</string>
    <string name="jump_step_open_app">手动打开胖乖生活</string>

    <!-- ═══ 设置页 ═══ -->
    <string name="settings_title">设置</string>
    <string name="settings_section_behavior">行为</string>
    <string name="settings_dry_run">调试模式（只记录，不跳转、不点击）</string>
    <string name="settings_dry_run_note">打开后每次启动只打印「会拉起谁、会点哪里」，不进胖乖、不点任何东西。调试时用它，避免误操作。</string>
    <string name="settings_wait">进入首页后先等 %1$d 毫秒再找扫描图标</string>
    <string name="settings_wait_note">胖乖首页有广告位，渲染慢。等太短会找不到图标，等太长又慢。默认 2500 毫秒。</string>
    <string name="settings_window">待命窗口 %1$d 秒</string>
    <string name="settings_window_note">只有在这个时间窗内，无障碍服务才会去点那一下。窗口外它不会碰任何界面。默认 20 秒。</string>

    <string name="settings_section_a11y">无障碍</string>
    <string name="settings_a11y_status">无障碍状态：%1$s</string>
    <string name="settings_a11y_open">打开系统的无障碍设置</string>
    <string name="settings_a11y_heal">尝试自动恢复授权</string>
    <string name="settings_a11y_note">MIUI 把「从最近任务划掉卡片」当强制停止，会清空无障碍授权。拿到 WRITE_SECURE_SETTINGS 后本应用会自己写回来，该权限只能 adb 给：\nadb shell pm grant com.local.qkjump android.permission.WRITE_SECURE_SETTINGS</string>

    <string name="settings_section_about">关于</string>
    <string name="settings_about">本应用只做两件事：拉起胖乖生活的主界面（它自己 export 的入口），以及用无障碍替你在首页点一下右上角的扫描图标。\n\n扫码、识别设备、计费、出水，全部由胖乖生活与官方服务端完成。本应用不抓包、不逆向、不构造订单、不模拟支付、不碰账号与鉴权，也不保存任何 token。</string>
    <string name="settings_last">上次结果：%1$s</string>
    <string name="settings_reset">恢复默认</string>
    <string name="settings_reset_confirm">把等待时间恢复为 2500 毫秒、待命窗口恢复为 20 秒，并关掉调试模式？</string>

    <!-- ═══ 无障碍服务描述（系统设置里会显示） ═══ -->
    <string name="a11y_label">胖乖直达 · 代点扫描图标</string>
    <string name="a11y_desc">只在「胖乖生活」刚被本应用拉起后的 20 秒内工作，且只在它的首页上做一件事：点一下右上角的扫描图标。不读取、不修改任何业务数据，不涉及支付、订单、金额。</string>

    <!-- ═══ Toast ═══ -->
    <string name="toast_a11y_needed">还没开无障碍，只能拉起胖乖、没法代点扫描图标</string>
    <string name="toast_heal_ok">无障碍授权已写回，系统通常一秒内绑定</string>
    <string name="toast_heal_failed">写回失败，请手动到系统设置里打开</string>
    <string name="toast_heal_no_perm">没有 WRITE_SECURE_SETTINGS 权限，只能手动开</string>
</resources>
```

## app\src\main\res\values\themes.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <!--
      Material3 深色底。本工程是「开屏即跳」的过路页，不需要 DayNight 切换，
      固定深色可以保证跳转过程中不闪一下白底。
    -->
    <style name="Theme.QkJump" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="colorPrimary">#5B8CFF</item>
        <item name="colorSecondary">#8FD8FF</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:windowBackground">@drawable/bg_app</item>
        <item name="android:windowDrawsSystemBarBackgrounds" tools:targetApi="21">true</item>
    </style>
</resources>
```

## app\src\main\res\xml\accessibility_service_config.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
  无障碍服务配置。
  范围收得很窄：只监听窗口状态 / 窗口内容 / 滚动三种事件（本应用不需要别的），
  不要「触摸探索」、不要「检索交互窗口」以外的能力。
  canPerformGestures 是必需的：胖乖首页那一排图标外层不一定 clickable，
  必须能用手势坐标点下去（实测过）。
-->
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewScrolled"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagIncludeNotImportantViews"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:description="@string/a11y_desc"
    android:notificationTimeout="100"
    android:settingsActivity="com.local.qkjump.SettingsActivity" />
```

## app\src\main\res\layout\activity_jump.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
  跳转页。刻意做得极简：一个标题、一行状态、一行进度、一个按钮区。
  绝大多数情况下用户只会看到它 1~2 秒，然后画面就切到胖乖生活了。
  所以这里不放任何装饰性的东西，也不放解释性长文（解释都在设置页）。
-->
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true"
    android:scrollbars="none">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_horizontal"
        android:orientation="vertical"
        android:paddingStart="26dp"
        android:paddingTop="120dp"
        android:paddingEnd="26dp"
        android:paddingBottom="40dp">

        <TextView
            android:id="@+id/tvTitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:letterSpacing="0.04"
            android:text="@string/jump_title"
            android:textColor="#FFFFFF"
            android:textSize="30sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tvSub"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="10dp"
            android:gravity="center"
            android:text="@string/jump_sub"
            android:textColor="#99FFFFFF"
            android:textSize="13sp" />

        <!-- 状态卡片 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="48dp"
            android:background="@drawable/bg_glass"
            android:orientation="vertical"
            android:padding="22dp">

            <TextView
                android:id="@+id/tvStep"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:lineSpacingExtra="4dp"
                android:text="@string/jump_step_checking"
                android:textColor="#E6FFFFFF"
                android:textSize="15sp" />

            <ProgressBar
                android:id="@+id/progress"
                style="?android:attr/progressBarStyleHorizontal"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="18dp"
                android:indeterminate="true"
                android:indeterminateTint="#5B8CFF"
                android:max="100" />
        </LinearLayout>

        <!-- 按钮区：平时隐藏，出错时才出现 -->
        <LinearLayout
            android:id="@+id/actionBox"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="26dp"
            android:orientation="vertical"
            android:visibility="gone">

            <TextView
                android:id="@+id/btnPrimary"
                android:layout_width="match_parent"
                android:layout_height="54dp"
                android:background="@drawable/bg_button"
                android:gravity="center"
                android:text="@string/jump_step_retry"
                android:textColor="#FFFFFF"
                android:textSize="15sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/btnSecondary"
                android:layout_width="match_parent"
                android:layout_height="50dp"
                android:layout_marginTop="12dp"
                android:background="@drawable/bg_button_outline"
                android:gravity="center"
                android:text="@string/jump_step_open_a11y"
                android:textColor="#E6FFFFFF"
                android:textSize="14sp" />
        </LinearLayout>

        <TextView
            android:id="@+id/btnSettings"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="28dp"
            android:paddingStart="18dp"
            android:paddingTop="10dp"
            android:paddingEnd="18dp"
            android:paddingBottom="10dp"
            android:text="@string/jump_step_settings"
            android:textColor="#80FFFFFF"
            android:textSize="13sp" />
    </LinearLayout>
</ScrollView>
```

## app\src\main\res\layout\activity_settings.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:scrollbars="none">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingStart="22dp"
        android:paddingTop="44dp"
        android:paddingEnd="22dp"
        android:paddingBottom="40dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/settings_title"
            android:textColor="#FFFFFF"
            android:textSize="26sp"
            android:textStyle="bold" />

        <!-- ═══ 行为 ═══ -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="26dp"
            android:background="@drawable/bg_glass"
            android:orientation="vertical"
            android:padding="18dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/settings_section_behavior"
                android:textColor="#B3FFFFFF"
                android:textSize="12sp" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="14dp"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/settings_dry_run"
                    android:textColor="#FFFFFF"
                    android:textSize="15sp" />

                <Switch
                    android:id="@+id/cbDryRun"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:lineSpacingExtra="2dp"
                android:text="@string/settings_dry_run_note"
                android:textColor="#66FFFFFF"
                android:textSize="11sp" />

            <TextView
                android:id="@+id/tvWait"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="20dp"
                android:textColor="#FFFFFF"
                android:textSize="14sp" />

            <SeekBar
                android:id="@+id/sbWait"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:max="20" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:lineSpacingExtra="2dp"
                android:text="@string/settings_wait_note"
                android:textColor="#66FFFFFF"
                android:textSize="11sp" />

            <TextView
                android:id="@+id/tvWindow"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="18dp"
                android:textColor="#FFFFFF"
                android:textSize="14sp" />

            <SeekBar
                android:id="@+id/sbWindow"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:max="55" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:lineSpacingExtra="2dp"
                android:text="@string/settings_window_note"
                android:textColor="#66FFFFFF"
                android:textSize="11sp" />
        </LinearLayout>

        <!-- ═══ 无障碍 ═══ -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="18dp"
            android:background="@drawable/bg_glass"
            android:orientation="vertical"
            android:padding="18dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/settings_section_a11y"
                android:textColor="#B3FFFFFF"
                android:textSize="12sp" />

            <TextView
                android:id="@+id/tvA11yStatus"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:lineSpacingExtra="2dp"
                android:textColor="#E6FFFFFF"
                android:textSize="13sp" />

            <TextView
                android:id="@+id/btnOpenA11y"
                android:layout_width="match_parent"
                android:layout_height="50dp"
                android:layout_marginTop="14dp"
                android:background="@drawable/bg_button_outline"
                android:gravity="center"
                android:text="@string/settings_a11y_open"
                android:textColor="#E6FFFFFF"
                android:textSize="14sp" />

            <TextView
                android:id="@+id/btnHeal"
                android:layout_width="match_parent"
                android:layout_height="50dp"
                android:layout_marginTop="10dp"
                android:background="@drawable/bg_button_outline"
                android:gravity="center"
                android:text="@string/settings_a11y_heal"
                android:textColor="#E6FFFFFF"
                android:textSize="14sp" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="10dp"
                android:lineSpacingExtra="2dp"
                android:text="@string/settings_a11y_note"
                android:textColor="#66FFFFFF"
                android:textSize="11sp"
                android:typeface="monospace" />
        </LinearLayout>

        <!-- ═══ 关于 ═══ -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="18dp"
            android:background="@drawable/bg_glass"
            android:orientation="vertical"
            android:padding="18dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/settings_section_about"
                android:textColor="#B3FFFFFF"
                android:textSize="12sp" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:lineSpacingExtra="4dp"
                android:text="@string/settings_about"
                android:textColor="#CCFFFFFF"
                android:textSize="13sp" />

            <TextView
                android:id="@+id/tvLast"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="14dp"
                android:textColor="#80FFFFFF"
                android:textSize="12sp" />

            <TextView
                android:id="@+id/btnReset"
                android:layout_width="match_parent"
                android:layout_height="50dp"
                android:layout_marginTop="16dp"
                android:background="@drawable/bg_button_outline"
                android:gravity="center"
                android:text="@string/settings_reset"
                android:textColor="#E6FFFFFF"
                android:textSize="14sp" />
        </LinearLayout>
    </LinearLayout>
</ScrollView>
```

## app\src\main\res\drawable\bg_app.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 页面底色：深蓝到近黑的对角渐变。玻璃卡片压在这上面才有层次。 -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:angle="315"
        android:startColor="#0E1A33"
        android:centerColor="#12224A"
        android:endColor="#070B14"
        android:type="linear" />
</shape>
```

## app\src\main\res\drawable\bg_glass.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 玻璃卡片：半透明白填充 + 更亮的细描边，22dp 圆角。 -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#1AFFFFFF" />
    <corners android:radius="22dp" />
    <stroke
        android:width="1dp"
        android:color="#33FFFFFF" />
</shape>
```

## app\src\main\res\drawable\bg_button.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 主按钮：蓝紫渐变 + 28dp 圆角，按下去变暗。 -->
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape android:shape="rectangle">
            <solid android:color="#C04E7BE8" />
            <corners android:radius="28dp" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <gradient
                android:angle="0"
                android:startColor="#5B8CFF"
                android:endColor="#7B5BFF" />
            <corners android:radius="28dp" />
        </shape>
    </item>
</selector>
```

## app\src\main\res\drawable\bg_button_outline.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 描边按钮：透明底 + 半透明白边，按下去浮一层白。 -->
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape android:shape="rectangle">
            <solid android:color="#26FFFFFF" />
            <corners android:radius="24dp" />
            <stroke android:width="1dp" android:color="#66FFFFFF" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#0DFFFFFF" />
            <corners android:radius="24dp" />
            <stroke android:width="1dp" android:color="#3DFFFFFF" />
        </shape>
    </item>
</selector>
```

## app\src\main\res\drawable\ic_launcher_background.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 图标底色：与 QrJump 的蓝区分开，用稍微偏青的深蓝。 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#0B4F8A"
        android:pathData="M0,0h108v108h-108z" />
</vector>
```

## app\src\main\res\drawable\ic_launcher_foreground.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
  前景图标：一个「扫码取景框 + 中间一道闪电」的极简符号。
  取景框表示扫一扫，闪电表示「直达」。
  全部矢量，无位图，任何密度下都清晰。
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- 取景框四个角 -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M30,34h12v5h-7v7h-5z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M66,34h12v12h-5v-7h-7z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M30,62h5v7h7v5h-12z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M73,62h5v12h-12v-5h7z" />

    <!-- 中间闪电 -->
    <path
        android:fillColor="#8FD8FF"
        android:pathData="M58,42l-11,17h8l-3,15 12,-18h-8z" />
</vector>
```

## app\src\main\res\mipmap-anydpi-v26\ic_launcher.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

---

合计：27 个文件，1904 行。
