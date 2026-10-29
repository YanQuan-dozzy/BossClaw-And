package com.bossclaw.android.accessibility.task

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.bossclaw.android.accessibility.util.AccessibilityNodeUtil
import com.bossclaw.android.accessibility.util.GestureEngine

class SendGreetingTask(
    private val greetingText: String,
    private val autoNavigateBack: Boolean = true,
    private val onComplete: (() -> Unit)? = null,
    private val onError: ((String) -> Unit)? = null
) : BaseAutomationTask() {

    private var sendClickAttempts = 0

    // 确认弹窗/聊天窗口转场需要更宽裕的重试窗口
    override val maxRetryCount: Int = 5

    companion object {
        private const val STEP_INIT_AND_CLICK_CHAT = 1
        private const val STEP_WAIT_WINDOW_OR_POPUP = 2
        private const val STEP_INPUT_GREETING_TEXT = 3
        private const val STEP_CLICK_SEND_BUTTON = 4
        private const val STEP_VERIFY_AND_FINISH = 5
        private const val STEP_HANDLE_NAVIGATE_BACK = 6

        private const val BASE_SCREEN_WIDTH = 1220f
        private const val BASE_SCREEN_HEIGHT = 2712f
        private const val BASE_OFFSET_X = 85f
        private const val BASE_OFFSET_Y = 42f

        private const val ID_BTN_CHAT = "com.hpbr.bosszhipin:id/btn_chat"
        private const val ID_EDIT_TEXT = "com.hpbr.bosszhipin:id/editText_with_scrollbar"
        private const val ID_EMOTION_VIEW = "com.hpbr.bosszhipin:id/mEmotionView"
        private const val ID_BACK_BUTTON = "com.hpbr.bosszhipin:id/iv_back"
    }

    override fun onStep(step: Int) {
        when (step) {
            STEP_INIT_AND_CLICK_CHAT -> handleInitAndClickChat()
            STEP_WAIT_WINDOW_OR_POPUP -> handleWaitWindowOrPopup()
            STEP_INPUT_GREETING_TEXT -> handleInputGreetingText()
            STEP_CLICK_SEND_BUTTON -> handleClickSendButton()
            STEP_VERIFY_AND_FINISH -> handleVerifyAndFinish()
            STEP_HANDLE_NAVIGATE_BACK -> handleNavigateBack()
        }
    }

    private fun handleInitAndClickChat() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "根节点未就绪")
            return
        }
        val editNodes = root.findAccessibilityNodeInfosByViewId(ID_EDIT_TEXT)
        if (!editNodes.isNullOrEmpty()) {
            editNodes.forEach { it.recycle() }
            root.recycle()
            sendStep(STEP_INPUT_GREETING_TEXT, 150L)
            return
        }
        val chatNodes = root.findAccessibilityNodeInfosByViewId(ID_BTN_CHAT)
        val chatBtn = chatNodes?.firstOrNull()
        if (chatBtn == null) {
            chatNodes?.forEach { it.recycle() }
            root.recycle()
            retryCurrentStep(500L, "未检测到聊天输入框或立即沟通按钮")
            return
        }
        sendTaskMessage("点击【立即沟通】")
        val clicked = GestureEngine.performClick(
            service, chatBtn,
            onSuccess = {
                chatNodes.forEach { it.recycle() }
                root.recycle()
                nextStep(850L)
            },
            onFail = {
                chatNodes.forEach { it.recycle() }
                root.recycle()
                retryCurrentStep(400L, "点击沟通按钮失败")
            }
        )
        if (!clicked) {
            chatNodes.forEach { it.recycle() }
            root.recycle()
            retryCurrentStep(500L, "无法派发立即沟通点击")
        }
    }

    private fun handleWaitWindowOrPopup() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "等待界面渲染")
            return
        }
        val editNodes = root.findAccessibilityNodeInfosByViewId(ID_EDIT_TEXT)
        if (!editNodes.isNullOrEmpty()) {
            editNodes.forEach { it.recycle() }
            root.recycle()
            sendStep(STEP_INPUT_GREETING_TEXT, 150L)
            return
        }
        val safeBounds = AccessibilityNodeUtil.getSafeScreenBounds(service)
        val confirmPopupBtn = AccessibilityNodeUtil.dfsFindNode(root, safeBounds) { node ->
            val txt = node.text?.toString() ?: ""
            txt == "发招呼" || txt == "确定" || txt == "确认"
        }
        if (confirmPopupBtn != null) {
            sendTaskMessage("检测到确认弹窗，点击推进")
            GestureEngine.performClick(
                service, confirmPopupBtn,
                onSuccess = {
                    confirmPopupBtn.recycle()
                    root.recycle()
                    nextStep(850L)
                },
                onFail = {
                    confirmPopupBtn.recycle()
                    root.recycle()
                    retryCurrentStep(400L, "点击弹窗确认失败")
                }
            )
            return
        }
        root.recycle()
        retryCurrentStep(500L, "等待进入聊天窗口")
    }

    private fun handleInputGreetingText() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "窗口未就绪")
            return
        }
        val editNodes = root.findAccessibilityNodeInfosByViewId(ID_EDIT_TEXT)
        val editNode = editNodes?.firstOrNull()
        if (editNode == null) {
            editNodes?.forEach { it.recycle() }
            root.recycle()
            retryCurrentStep(400L, "未找到输入框")
            return
        }
        sendTaskMessage("填入定制开场白")
        sendClickAttempts = 0
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, greetingText)
        }
        val success = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        editNodes.forEach { it.recycle() }
        root.recycle()
        if (success) nextStep(850L) else retryCurrentStep(300L, "输入框填词失败")
    }

    private fun handleClickSendButton() {
        sendClickAttempts++
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "无法获取发送界面根节点")
            return
        }
        val editNodes = root.findAccessibilityNodeInfosByViewId(ID_EDIT_TEXT)
        val editNode = editNodes?.firstOrNull()
        if (editNode == null) {
            editNodes?.forEach { it.recycle() }
            root.recycle()
            retryCurrentStep(400L, "丢失输入框锚点")
            return
        }
        val editBounds = Rect()
        editNode.getBoundsInScreen(editBounds)
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
        editNodes.forEach { it.recycle() }
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

        val clicked = GestureEngine.clickAt(
            service, targetX, targetY,
            onSuccess = { nextStep(650L) },
            onFail = { retryCurrentStep(400L, "点击发送失败") }
        )
        if (!clicked) retryCurrentStep(400L, "无法派发发送手势")
    }

    private fun handleVerifyAndFinish() {
        val root = service.rootInActiveWindow
        val editNodes = root?.findAccessibilityNodeInfosByViewId(ID_EDIT_TEXT)
        val editNode = editNodes?.firstOrNull()
        editNode?.refresh()
        val currentInput = editNode?.text?.toString()?.trim() ?: ""
        val fingerprint = if (greetingText.length > 8) greetingText.substring(0, 8) else greetingText
        val isCleared = currentInput.isEmpty() || !currentInput.contains(fingerprint)
        val bubbleNodes = root?.findAccessibilityNodeInfosByText(fingerprint)
        val isSentBubble = bubbleNodes?.any { it != editNode } == true
        bubbleNodes?.forEach { it.recycle() }
        editNodes?.forEach { it.recycle() }
        root?.recycle()

        if (isCleared || isSentBubble) {
            sendTaskMessage("开场白已送达")
            nextStep(500L)
        } else if (sendClickAttempts < 3) {
            sendStep(STEP_CLICK_SEND_BUTTON, 350L)
        } else {
            failedTask("点击发送未生效，消息未送出")
        }
    }

    private fun handleNavigateBack() {
        if (!autoNavigateBack) {
            finishTask()
            return
        }
        val root = service.rootInActiveWindow
        val backNodes = root?.findAccessibilityNodeInfosByViewId(ID_BACK_BUTTON)
        val backBtn = backNodes?.firstOrNull()
        if (backBtn != null) {
            GestureEngine.performClick(
                service, backBtn,
                onSuccess = {
                    backNodes.forEach { it.recycle() }
                    root.recycle()
                    finishTask()
                },
                onFail = {
                    backNodes.forEach { it.recycle() }
                    root.recycle()
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    finishTask()
                }
            )
        } else {
            root?.recycle()
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            finishTask()
        }
    }

    override fun finishTask() {
        super.finishTask()
        onComplete?.invoke()
    }

    override fun failedTask(reason: String) {
        super.failedTask(reason)
        onError?.invoke(reason)
    }

    override fun onForceStopped() {
        onError?.invoke("打招呼任务被强制终止")
    }
}
