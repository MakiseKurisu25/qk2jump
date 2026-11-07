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
