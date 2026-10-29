package com.bossclaw.android.accessibility.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.ThreadLocalRandom
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

object GestureEngine {

    private const val TAG = "GestureEngine"
    private val mainHandler = Handler(Looper.getMainLooper())

    var windowTouchListener: ((isTouchable: Boolean) -> Unit)? = null

    fun performClick(
        service: AccessibilityService,
        node: AccessibilityNodeInfo?,
        onSuccess: (() -> Unit)? = null,
        onFail: (() -> Unit)? = null
    ): Boolean {
        if (node == null) {
            onFail?.invoke()
            return false
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val width = bounds.width()
        val height = bounds.height()
        if (width <= 0 || height <= 0 || bounds.centerX() <= 0 || bounds.centerY() <= 0) {
            val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (result) onSuccess?.invoke() else onFail?.invoke()
            return result
        }
        val safePaddingX = maxOf(2, (width * 0.25).toInt())
        val safePaddingY = maxOf(2, (height * 0.25).toInt())
        val minX = bounds.left + safePaddingX
        val maxX = bounds.right - safePaddingX
        val minY = bounds.top + safePaddingY
        val maxY = bounds.bottom - safePaddingY
        val x = if (minX < maxX) ThreadLocalRandom.current().nextInt(minX, maxX + 1) else bounds.centerX()
        val y = if (minY < maxY) ThreadLocalRandom.current().nextInt(minY, maxY + 1) else bounds.centerY()
        return clickAt(service, x, y, onSuccess, onFail)
    }

    fun clickAt(
        service: AccessibilityService,
        x: Int,
        y: Int,
        onSuccess: (() -> Unit)? = null,
        onFail: (() -> Unit)? = null
    ): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val duration = ThreadLocalRandom.current().nextLong(65, 110)
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGestureInternal(service, gesture, onSuccess, onFail)
        return true
    }

    fun swipeUp(
        service: AccessibilityService,
        durationMs: Long = 0,
        onSuccess: (() -> Unit)? = null,
        onFail: (() -> Unit)? = null
    ): Boolean {
        val bounds = AccessibilityNodeUtil.getSafeScreenBounds(service)
        val screenW = bounds.width()
        val screenH = bounds.height()
        val startX = (screenW * ThreadLocalRandom.current().nextDouble(0.45, 0.55)).toInt()
        val startY = (screenH * ThreadLocalRandom.current().nextDouble(0.72, 0.78)).toInt()
        val endX = (screenW * ThreadLocalRandom.current().nextDouble(0.42, 0.58)).toInt()
        val endY = (screenH * ThreadLocalRandom.current().nextDouble(0.20, 0.28)).toInt()
        val duration = if (durationMs > 0) durationMs else ThreadLocalRandom.current().nextLong(650, 920)
        return swipeBezier(service, startX, startY, endX, endY, duration, onSuccess, onFail)
    }

    fun swipeBezier(
        service: AccessibilityService,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long,
        onSuccess: (() -> Unit)? = null,
        onFail: (() -> Unit)? = null
    ): Boolean {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            val deltaX = (endX - startX).toFloat()
            val deltaY = (endY - startY).toFloat()
            val jitter = ThreadLocalRandom.current().nextInt(-50, 50).toFloat()
            val control1X = startX + (deltaX * 0.25f) + jitter
            val control1Y = startY + (deltaY * 0.35f)
            val control2X = startX + (deltaX * 0.75f) + (jitter * 0.6f)
            val control2Y = startY + (deltaY * 0.85f)
            cubicTo(control1X, control1Y, control2X, control2Y, endX.toFloat(), endY.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGestureInternal(service, gesture, onSuccess, onFail)
        return true
    }

    suspend fun clickSuspend(service: AccessibilityService, node: AccessibilityNodeInfo?): Boolean =
        suspendCancellableCoroutine { continuation ->
            performClick(
                service, node,
                onSuccess = { if (continuation.isActive) continuation.resume(true) },
                onFail = { if (continuation.isActive) continuation.resume(false) }
            )
        }

    suspend fun swipeUpSuspend(service: AccessibilityService, durationMs: Long = 0): Boolean =
        suspendCancellableCoroutine { continuation ->
            swipeUp(
                service, durationMs,
                onSuccess = { if (continuation.isActive) continuation.resume(true) },
                onFail = { if (continuation.isActive) continuation.resume(false) }
            )
        }

    private fun dispatchGestureInternal(
        service: AccessibilityService,
        gesture: GestureDescription,
        onSuccess: (() -> Unit)?,
        onFail: (() -> Unit)?
    ) {
        windowTouchListener?.invoke(false)
        try {
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    restoreWindowTouchable()
                    onSuccess?.invoke()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    restoreWindowTouchable()
                    Log.w(TAG, "手势下发被系统取消")
                    onFail?.invoke()
                }
            }, null)
        } catch (error: Exception) {
            Log.e(TAG, "手势派发异常: ${error.message}")
            restoreWindowTouchable()
            onFail?.invoke()
        }
    }

    private fun restoreWindowTouchable() {
        mainHandler.postDelayed({ windowTouchListener?.invoke(true) }, 150L)
    }
}
