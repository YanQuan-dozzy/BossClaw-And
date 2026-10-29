package com.bossclaw.android.accessibility.util

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object TabNavHelper {

    private const val ID_TAB_1_CONTAINER = "com.hpbr.bosszhipin:id/cl_tab_1"
    private const val ID_TAB_1_TEXT = "com.hpbr.bosszhipin:id/tv_tab_1"
    private const val ID_TAB_3_CONTAINER = "com.hpbr.bosszhipin:id/cl_tab_3"
    private const val ID_TAB_3_TEXT = "com.hpbr.bosszhipin:id/tv_tab_3"

    fun isJobTabSelected(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByViewId(ID_TAB_1_TEXT)
        val selected = nodes?.firstOrNull()?.isSelected == true
        nodes?.forEach { it.recycle() }
        root.recycle()
        return selected
    }

    fun isMessageTabSelected(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByViewId(ID_TAB_3_TEXT)
        val selected = nodes?.firstOrNull()?.isSelected == true
        nodes?.forEach { it.recycle() }
        root.recycle()
        return selected
    }

    fun switchToJobTab(
        service: AccessibilityService,
        onSuccess: (() -> Unit)? = null,
        onFail: (() -> Unit)? = null
    ): Boolean {
        if (isJobTabSelected(service)) {
            onSuccess?.invoke()
            return true
        }
        val root = service.rootInActiveWindow
        val nodes = root?.findAccessibilityNodeInfosByViewId(ID_TAB_1_CONTAINER)
        val tabNode = nodes?.firstOrNull()
        if (tabNode != null) {
            val clicked = GestureEngine.performClick(service, tabNode, onSuccess, onFail)
            nodes.forEach { it.recycle() }
            root.recycle()
            return clicked
        }
        root?.recycle()
        val bounds = AccessibilityNodeUtil.getScreenBounds(service)
        val x = (bounds.width() * 0.125f).toInt()
        val y = (bounds.height() * 0.94f).toInt()
        Log.d("TabNavHelper", "坐标兜底点击【职位】: ($x, $y)")
        return GestureEngine.clickAt(service, x, y, onSuccess, onFail)
    }

    fun switchToMessageTab(
        service: AccessibilityService,
        onSuccess: (() -> Unit)? = null,
        onFail: (() -> Unit)? = null
    ): Boolean {
        if (isMessageTabSelected(service)) {
            onSuccess?.invoke()
            return true
        }
        val root = service.rootInActiveWindow
        val nodes = root?.findAccessibilityNodeInfosByViewId(ID_TAB_3_CONTAINER)
        val tabNode = nodes?.firstOrNull()
        if (tabNode != null) {
            val clicked = GestureEngine.performClick(service, tabNode, onSuccess, onFail)
            nodes.forEach { it.recycle() }
            root.recycle()
            return clicked
        }
        root?.recycle()
        val bounds = AccessibilityNodeUtil.getScreenBounds(service)
        val x = (bounds.width() * 0.625f).toInt()
        val y = (bounds.height() * 0.94f).toInt()
        Log.d("TabNavHelper", "坐标兜底点击【消息】: ($x, $y)")
        return GestureEngine.clickAt(service, x, y, onSuccess, onFail)
    }

    fun findNode(root: AccessibilityNodeInfo, viewId: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId) ?: return null
        val first = nodes.firstOrNull()
        nodes.forEach { if (it != first) it.recycle() }
        return first
    }
}
