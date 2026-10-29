package com.bossclaw.android.accessibility.util

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import java.util.regex.Pattern

object AccessibilityNodeUtil {

    private val CLEAN_PATTERN: Pattern = Pattern.compile("[\\p{P}\\p{S}\\p{Z}\\s]+")

    fun cleanString(input: CharSequence?): String {
        if (input.isNullOrEmpty()) return ""
        return CLEAN_PATTERN.matcher(input).replaceAll("")
    }

    fun getScreenBounds(context: Context?): Rect {
        if (context == null) return Rect()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return Rect()
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        return Rect(0, 0, dm.widthPixels, dm.heightPixels)
    }

    fun getSafeScreenBounds(context: Context?): Rect {
        val bounds = getScreenBounds(context)
        if (bounds.isEmpty) return bounds
        return Rect(0, getStatusBarHeight(context), bounds.width(), bounds.height())
    }

    fun getStatusBarHeight(context: Context?): Int {
        if (context == null) return 60
        return try {
            val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 60
        } catch (e: Exception) {
            60
        }
    }

    fun isCenterInBounds(node: AccessibilityNodeInfo?, targetBounds: Rect): Boolean {
        if (node == null || targetBounds.isEmpty) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return targetBounds.contains(rect.centerX(), rect.centerY())
    }

    fun findNodeByText(service: AccessibilityService?, text: String, exactMatch: Boolean = false): AccessibilityNodeInfo? {
        if (service == null || text.isEmpty()) return null
        val root = service.rootInActiveWindow ?: return null
        val safeBounds = getSafeScreenBounds(service)
        val nodes = root.findAccessibilityNodeInfosByText(text)
        val result = findFirstInBoundsAndRecycleRest(nodes, safeBounds) { node ->
            val nodeText = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            if (exactMatch) nodeText == text || desc == text
            else nodeText.contains(text) || desc.contains(text)
        }
        if (root != result) root.recycle()
        return result
    }

    fun findNodeById(service: AccessibilityService?, viewId: String): AccessibilityNodeInfo? {
        if (service == null || viewId.isEmpty()) return null
        val root = service.rootInActiveWindow ?: return null
        val safeBounds = getSafeScreenBounds(service)
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        val result = findFirstInBoundsAndRecycleRest(nodes, safeBounds)
        if (root != result) root.recycle()
        return result
    }

    fun findNodeByDescription(service: AccessibilityService?, desc: String): AccessibilityNodeInfo? {
        if (service == null || desc.isEmpty()) return null
        val root = service.rootInActiveWindow ?: return null
        val safeBounds = getSafeScreenBounds(service)
        val result = dfsFindNode(root, safeBounds) { node ->
            node.contentDescription?.toString()?.trim().orEmpty().contains(desc)
        }
        if (root != result) root.recycle()
        return result
    }

    fun findNodeByClassAndText(
        service: AccessibilityService?,
        className: String,
        text: String,
        minRatioX: Float = 0f,
        maxRatioX: Float = 1f
    ): AccessibilityNodeInfo? {
        if (service == null || className.isEmpty() || text.isEmpty()) return null
        val root = service.rootInActiveWindow ?: return null
        val safeBounds = getSafeScreenBounds(service)
        val minX = (safeBounds.width() * minRatioX).toInt()
        val maxX = (safeBounds.width() * maxRatioX).toInt()
        val result = dfsFindNode(root, safeBounds) { node ->
            val classMatched = node.className?.toString() == className
            val textMatched = node.text?.toString()?.trim() == text
            if (classMatched && textMatched) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.left >= minX && bounds.right <= maxX
            } else false
        }
        if (root != result) root.recycle()
        return result
    }

    fun dfsFindNode(
        node: AccessibilityNodeInfo?,
        targetBounds: Rect,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (isCenterInBounds(node, targetBounds) && predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = dfsFindNode(child, targetBounds, predicate)
            if (result != null) {
                if (result != child) child.recycle()
                return result
            }
            child.recycle()
        }
        return null
    }

    private fun findFirstInBoundsAndRecycleRest(
        nodes: List<AccessibilityNodeInfo>?,
        screenBounds: Rect,
        extraFilter: ((AccessibilityNodeInfo) -> Boolean)? = null
    ): AccessibilityNodeInfo? {
        if (nodes.isNullOrEmpty() || screenBounds.isEmpty) return null
        var target: AccessibilityNodeInfo? = null
        for (node in nodes) {
            if (target == null && isCenterInBounds(node, screenBounds) && (extraFilter == null || extraFilter(node))) {
                target = node
                continue
            }
            node.recycle()
        }
        return target
    }
}
