package com.bossclaw.android.accessibility.task

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.bossclaw.android.accessibility.util.AccessibilityNodeUtil
import com.bossclaw.android.accessibility.util.GestureEngine
import java.util.concurrent.ThreadLocalRandom

class ScrollFeedTask(
    private val allowTabSwitch: Boolean = true,
    private val onCompleted: (() -> Unit)? = null,
    private val onFailed: ((String) -> Unit)? = null
) : BaseAutomationTask() {

    companion object {
        private const val STEP_CHECK_AND_SWITCH_TAB = 1
        private const val STEP_PERFORM_SWIPE = 2
        private const val STEP_SETTLE_AND_FINISH = 3
        private const val TAB_RECOMMEND = "推荐"
        private const val TAB_LATEST = "最新"
        private const val ID_RECYCLER_VIEW = "com.hpbr.bosszhipin:id/rv_list"
        private const val ID_TAB_LABEL = "com.hpbr.bosszhipin:id/tv_tab_label"
    }

    override fun onStep(step: Int) {
        when (step) {
            STEP_CHECK_AND_SWITCH_TAB -> handleTabCheckAndSwitch()
            STEP_PERFORM_SWIPE -> handlePerformSwipe()
            STEP_SETTLE_AND_FINISH -> {
                sendTaskMessage("翻页完成，已加载新一屏岗位")
                finishTask()
            }
        }
    }

    private fun handleTabCheckAndSwitch() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "无法获取窗口根节点")
            return
        }
        val safeBounds = AccessibilityNodeUtil.getSafeScreenBounds(service)
        val rvNode = AccessibilityNodeUtil.findNodeById(service, ID_RECYCLER_VIEW)
        if (rvNode == null) {
            root.recycle()
            retryCurrentStep(600L, "未找到职位列表容器")
            return
        }
        rvNode.recycle()

        if (!allowTabSwitch || ThreadLocalRandom.current().nextInt(100) >= 20) {
            root.recycle()
            sendStep(STEP_PERFORM_SWIPE, 150L)
            return
        }

        val recommendNode = findTabNode(root, safeBounds, TAB_RECOMMEND)
        val latestNode = findTabNode(root, safeBounds, TAB_LATEST)
        if (recommendNode != null && latestNode != null) {
            val targetNode = when {
                recommendNode.isSelected -> latestNode
                latestNode.isSelected -> recommendNode
                else -> null
            }
            if (targetNode != null) {
                val targetName = targetNode.text?.toString() ?: "目标流"
                sendTaskMessage("切换职位流到【$targetName】")
                val clicked = GestureEngine.performClick(service, targetNode)
                recommendNode.recycle()
                latestNode.recycle()
                root.recycle()
                if (clicked) {
                    nextStep(1100L)
                    return
                }
            } else {
                recommendNode.recycle()
                latestNode.recycle()
            }
        } else {
            recommendNode?.recycle()
            latestNode?.recycle()
        }
        root.recycle()
        sendStep(STEP_PERFORM_SWIPE, 150L)
    }

    private fun handlePerformSwipe() {
        val duration = ThreadLocalRandom.current().nextLong(650, 950)
        val queued = GestureEngine.swipeUp(
            service,
            duration,
            onSuccess = { sendStep(STEP_SETTLE_AND_FINISH, 500L) },
            onFail = { retryCurrentStep(400L, "滑动手势失败") }
        )
        if (!queued) retryCurrentStep(500L, "滑动手势无法派发")
    }

    private fun findTabNode(root: AccessibilityNodeInfo, screenBounds: Rect, tabText: String): AccessibilityNodeInfo? {
        val tabNodes = root.findAccessibilityNodeInfosByViewId(ID_TAB_LABEL) ?: return null
        var target: AccessibilityNodeInfo? = null
        for (node in tabNodes) {
            if (target == null && node.text?.toString() == tabText && AccessibilityNodeUtil.isCenterInBounds(node, screenBounds)) {
                target = node
                continue
            }
            node.recycle()
        }
        return target
    }

    override fun finishTask() {
        super.finishTask()
        onCompleted?.invoke()
    }

    override fun failedTask(reason: String) {
        super.failedTask(reason)
        onFailed?.invoke(reason)
    }

    override fun onForceStopped() {
        onFailed?.invoke("滑动任务被强制终止")
    }
}
