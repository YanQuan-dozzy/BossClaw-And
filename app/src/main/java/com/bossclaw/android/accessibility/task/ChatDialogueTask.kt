package com.bossclaw.android.accessibility.task

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.bossclaw.android.accessibility.model.UnreadChatSession
import com.bossclaw.android.accessibility.util.AccessibilityNodeUtil
import com.bossclaw.android.accessibility.util.GestureEngine

class ChatDialogueTask(
    private val session: UnreadChatSession? = null,
    private val replyText: String,
    private val onMessageSent: (() -> Unit)? = null,
    private val onBackCompleted: (() -> Unit)? = null,
    private val onError: ((String) -> Unit)? = null
) : BaseAutomationTask() {

    private var sendClickAttempts = 0

    companion object {
        private const val STEP_WAIT_CHAT_INPUT = 1
        private const val STEP_INPUT_REPLY = 2
        private const val STEP_CLICK_SEND = 3
        private const val STEP_VERIFY_DELIVERY = 4
        private const val STEP_NAVIGATE_BACK = 5

        private const val BASE_SCREEN_WIDTH = 1220f
        private const val BASE_SCREEN_HEIGHT = 2712f
        private const val BASE_OFFSET_X = 85f
        private const val BASE_OFFSET_Y = 42f

        private const val ID_EDIT_TEXT = "com.hpbr.bosszhipin:id/editText_with_scrollbar"
        private const val ID_EMOTION_VIEW = "com.hpbr.bosszhipin:id/mEmotionView"
        private const val ID_BACK_BUTTON = "com.hpbr.bosszhipin:id/iv_back"
    }

    override fun onStep(step: Int) {
        when (step) {
            STEP_WAIT_CHAT_INPUT -> handleWaitChatInput()
            STEP_INPUT_REPLY -> handleInputReply()
            STEP_CLICK_SEND -> handleClickSend()
            STEP_VERIFY_DELIVERY -> handleVerifyDelivery()
            STEP_NAVIGATE_BACK -> handleNavigateBack()
        }
    }

    private fun handleWaitChatInput() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "等待单聊窗口渲染")
            return
        }
        val editNodes = root.findAccessibilityNodeInfosByViewId(ID_EDIT_TEXT)
        val hasEdit = !editNodes.isNullOrEmpty()
        editNodes?.forEach { it.recycle() }
        root.recycle()
        if (hasEdit) {
            sendClickAttempts = 0
            sendStep(STEP_INPUT_REPLY, 150L)
        } else {
            retryCurrentStep(400L, "等待输入框就位")
        }
    }

    private fun handleInputReply() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "获取输入框根节点失败")
            return
        }
        val editNodes = root.findAccessibilityNodeInfosByViewId(ID_EDIT_TEXT)
        val editNode = editNodes?.firstOrNull()
        if (editNode == null) {
            editNodes?.forEach { it.recycle() }
            root.recycle()
            retryCurrentStep(400L, "未找到聊天输入框")
            return
        }
        sendTaskMessage("回复 HR【${session?.hrName ?: "招聘者"}】")
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, replyText)
        }
        val success = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        editNodes.forEach { it.recycle() }
        root.recycle()
        if (success) nextStep(800L) else retryCurrentStep(300L, "输入框填词失败")
    }

    private fun handleClickSend() {
        sendClickAttempts++
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "根节点丢失")
            return
        }
        val editNodes = root.findAccessibilityNodeInfosByViewId(ID_EDIT_TEXT)
        val editNode = editNodes?.firstOrNull()
        val editBounds = Rect()
        editNode?.getBoundsInScreen(editBounds)
        val emotionNodes = root.findAccessibilityNodeInfosByViewId(ID_EMOTION_VIEW)
        val emotionNode = emotionNodes?.firstOrNull()
        val emotionBounds = Rect()
        val hasEmotion = emotionNode != null
        emotionNode?.getBoundsInScreen(emotionBounds)

        var clickedNode = false
        if (hasEmotion) {
            val safeBounds = AccessibilityNodeUtil.getSafeScreenBounds(service)
            val sendNode = AccessibilityNodeUtil.dfsFindNode(root, safeBounds) { node ->
                val r = Rect()
                node.getBoundsInScreen(r)
                r.left >= emotionBounds.right &&
                    Math.abs(r.centerY() - emotionBounds.centerY()) < 50 &&
                    (node.isClickable || node.className?.contains("ImageView") == true)
            }
            if (sendNode != null) {
                clickedNode = sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                sendNode.recycle()
            }
        }
        emotionNodes?.forEach { it.recycle() }
        editNodes?.forEach { it.recycle() }
        root.recycle()

        if (clickedNode) {
            nextStep(650L)
            return
        }

        val screenBounds = AccessibilityNodeUtil.getScreenBounds(service)
        val screenWidth = if (screenBounds.width() > 0) screenBounds.width() else 1220
        val screenHeight = if (screenBounds.height() > 0) screenBounds.height() else 2712
        val offsetX = ((screenWidth.toFloat() / BASE_SCREEN_WIDTH) * BASE_OFFSET_X).toInt()
        val offsetY = ((screenHeight.toFloat() / BASE_SCREEN_HEIGHT) * BASE_OFFSET_Y).toInt()
        val targetX = if (hasEmotion && emotionBounds.right > 0) {
            emotionBounds.right + (screenWidth - emotionBounds.right) / 2
        } else screenWidth - offsetX
        val targetY = if (hasEmotion && emotionBounds.centerY() > 0) {
            emotionBounds.centerY()
        } else editBounds.bottom - offsetY

        val sent = GestureEngine.clickAt(
            service, targetX, targetY,
            onSuccess = { nextStep(650L) },
            onFail = { retryCurrentStep(400L, "点击发送失败") }
        )
        if (!sent) retryCurrentStep(400L, "无法派发发送手势")
    }

    private fun handleVerifyDelivery() {
        val root = service.rootInActiveWindow
        val editNodes = root?.findAccessibilityNodeInfosByViewId(ID_EDIT_TEXT)
        val editNode = editNodes?.firstOrNull()
        editNode?.refresh()
        val currentInput = editNode?.text?.toString()?.trim() ?: ""
        val fingerprint = if (replyText.length > 6) replyText.substring(0, 6) else replyText
        val isCleared = currentInput.isEmpty() || !currentInput.contains(fingerprint)
        val bubbleNodes = root?.findAccessibilityNodeInfosByText(fingerprint)
        val isSentBubble = bubbleNodes?.any { it != editNode } == true
        bubbleNodes?.forEach { it.recycle() }
        editNodes?.forEach { it.recycle() }
        root?.recycle()

        if (isCleared || isSentBubble) {
            sendTaskMessage("回复已送达")
            onMessageSent?.invoke()
            nextStep(450L)
        } else if (sendClickAttempts < 3) {
            sendStep(STEP_CLICK_SEND, 350L)
        } else {
            failedTask("回复消息发送未生效")
        }
    }

    private fun handleNavigateBack() {
        val root = service.rootInActiveWindow
        val backNodes = root?.findAccessibilityNodeInfosByViewId(ID_BACK_BUTTON)
        val backBtn = backNodes?.firstOrNull()
        if (backBtn != null) {
            GestureEngine.performClick(
                service, backBtn,
                onSuccess = {
                    backNodes.forEach { it.recycle() }
                    root.recycle()
                    onBackCompleted?.invoke()
                    finishTask()
                },
                onFail = {
                    backNodes.forEach { it.recycle() }
                    root.recycle()
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    onBackCompleted?.invoke()
                    finishTask()
                }
            )
        } else {
            root?.recycle()
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            onBackCompleted?.invoke()
            finishTask()
        }
    }

    override fun failedTask(reason: String) {
        super.failedTask(reason)
        onError?.invoke(reason)
    }

    override fun onForceStopped() {
        onError?.invoke("对话回复任务被强制终止")
    }
}
