package com.bossclaw.android.accessibility.task

import android.accessibilityservice.AccessibilityService
import android.graphics.Point
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

    /** 是否确认回复已真正写进输入框；为空时不能把“输入框变空”当成送达 */
    private var inputFilled = false

    /** 是否已经点过输入框；部分版本必须先点一下唤起键盘，ACTION_SET_TEXT 才会生效 */
    private var inputTapped = false

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
        if (!inputTapped) {
            // 先像用户一样点一下输入框，唤起键盘和真实编辑态，避免 SET_TEXT 被忽略
            inputTapped = true
            var settled = false
            fun resume() {
                if (settled) return
                settled = true
                sendStep(STEP_INPUT_REPLY, 350L)
            }
            val queued = GestureEngine.performClick(
                service,
                editNode,
                onSuccess = { resume() },
                onFail = { resume() }
            )
            editNodes.forEach { it.recycle() }
            root.recycle()
            if (!queued) resume()
            return
        }
        val fingerprint = replyFingerprint()
        // 部分聊天页不聚焦时 ACTION_SET_TEXT 会被忽略，先让输入框获得焦点
        editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, replyText)
        }
        val success = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        editNode.refresh()
        val typed = editNode.text?.toString()?.trim().orEmpty()
        inputFilled = success && typed.contains(fingerprint)
        editNodes.forEach { it.recycle() }
        root.recycle()
        if (inputFilled) {
            nextStep(800L)
        } else {
            sendTaskMessage("回复未进入输入框，重新填入")
            retryCurrentStep(450L, "输入框填词未生效")
        }
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
        if (editNode == null) {
            editNodes?.forEach { it.recycle() }
            root.recycle()
            retryCurrentStep(400L, "丢失输入框锚点")
            return
        }
        val editBounds = Rect()
        editNode.getBoundsInScreen(editBounds)
        // 发送前必须确认输入框里确实是待发送的回复，避免空输入框被误判成“已送出”
        val currentInput = editNode.text?.toString()?.trim().orEmpty()
        if (!currentInput.contains(replyFingerprint())) {
            editNodes.forEach { it.recycle() }
            root.recycle()
            inputFilled = false
            sendTaskMessage("输入框里没有待发送的回复，退回填写步骤")
            sendStep(STEP_INPUT_REPLY, 300L)
            return
        }

        val safeBounds = AccessibilityNodeUtil.getSafeScreenBounds(service)
        // 发送按钮可能是“发送”文字，也可能是没有文字的纸飞机图标，统一返回点击坐标
        val sendPoint = findSendButtonPoint(root, editBounds, safeBounds)
        editNodes.forEach { it.recycle() }
        root.recycle()

        if (sendPoint != null) {
            sendTaskMessage("点击发送按钮（${sendPoint.x}, ${sendPoint.y}）")
            val clicked = GestureEngine.clickAt(
                service, sendPoint.x, sendPoint.y,
                onSuccess = { nextStep(650L) },
                onFail = { retryCurrentStep(400L, "点击发送失败") }
            )
            if (!clicked) retryCurrentStep(400L, "无法派发发送手势")
            return
        }

        // 兜底：按输入行右侧估算坐标
        val screenBounds = AccessibilityNodeUtil.getScreenBounds(service)
        val screenWidth = if (screenBounds.width() > 0) screenBounds.width() else 1220
        val screenHeight = if (screenBounds.height() > 0) screenBounds.height() else 2712
        val offsetX = ((screenWidth.toFloat() / BASE_SCREEN_WIDTH) * BASE_OFFSET_X).toInt()
        val offsetY = ((screenHeight.toFloat() / BASE_SCREEN_HEIGHT) * BASE_OFFSET_Y).toInt()
        val targetX = screenWidth - offsetX
        val targetY = editBounds.bottom - offsetY

        sendTaskMessage("按坐标点击发送（$targetX, $targetY）")
        val sent = GestureEngine.clickAt(
            service, targetX, targetY,
            onSuccess = { nextStep(650L) },
            onFail = { retryCurrentStep(400L, "点击发送失败") }
        )
        if (!sent) retryCurrentStep(400L, "无法派发发送手势")
    }

    /**
     * 返回发送按钮的点击坐标。
     * 新版聊天页的发送按钮是一个没有文字、也没有描述的纸飞机图标，不能只按“发送”文案查找；
     * 这里先找文字按钮，再取输入行里最右侧的可交互图标，并排除表情、加号等附件入口。
     */
    private fun findSendButtonPoint(
        root: AccessibilityNodeInfo,
        editBounds: Rect,
        screen: Rect
    ): Point? {
        val labeled = root.findAccessibilityNodeInfosByText("发送")
        if (labeled != null) {
            for (node in labeled) {
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val matched = (text == "发送" || desc == "发送") &&
                    bounds.centerX() > editBounds.centerX() &&
                    AccessibilityNodeUtil.isCenterInBounds(node, screen) &&
                    bounds.height() <= screen.height() / 3
                if (matched) {
                    labeled.forEach { if (it !== node) it.recycle() }
                    val point = Point(bounds.centerX(), bounds.centerY())
                    node.recycle()
                    return point
                }
                node.recycle()
            }
        }

        var rowCenterY = editBounds.centerY()
        var rowLeft = editBounds.centerX()
        val emotionNodes = root.findAccessibilityNodeInfosByViewId(ID_EMOTION_VIEW)
        val emotionNode = emotionNodes?.firstOrNull()
        if (emotionNode != null) {
            val emotionBounds = Rect()
            emotionNode.getBoundsInScreen(emotionBounds)
            if (emotionBounds.width() > 0 && emotionBounds.height() > 0) {
                rowCenterY = emotionBounds.centerY()
                rowLeft = emotionBounds.right
            }
        }
        emotionNodes?.forEach { it.recycle() }

        var best: Point? = null
        val rowTolerance = maxOf(60, editBounds.height())
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val label = (node.text?.toString().orEmpty() + node.contentDescription?.toString().orEmpty()).trim()
            val attachment = label == "+" || label.contains("表情") || label.contains("图片") ||
                label.contains("拍摄") || label.contains("文件") || label.contains("语音") ||
                label.contains("更多") || label.contains("键盘")
            val interactive = node.isClickable ||
                node.className?.contains("ImageView") == true ||
                node.className?.contains("ImageButton") == true
            val sameRow = Math.abs(bounds.centerY() - rowCenterY) <= rowTolerance
            val rightSide = bounds.centerX() > rowLeft
            val sizeOk = bounds.width() in 24..(screen.width() * 2 / 3) &&
                bounds.height() in 24..(screen.height() / 6)
            if (sameRow && rightSide && sizeOk && interactive && !attachment &&
                AccessibilityNodeUtil.isCenterInBounds(node, screen)
            ) {
                val current = best
                if (current == null || bounds.centerX() > current.x) {
                    best = Point(bounds.centerX(), bounds.centerY())
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                visit(child)
                child.recycle()
            }
        }
        visit(root)
        return best
    }

    private fun replyFingerprint(): String {
        val text = replyText.trim()
        return if (text.length > 6) text.substring(0, 6) else text
    }

    private fun handleVerifyDelivery() {
        val root = service.rootInActiveWindow
        val editNodes = root?.findAccessibilityNodeInfosByViewId(ID_EDIT_TEXT)
        val editNode = editNodes?.firstOrNull()
        editNode?.refresh()
        val currentInput = editNode?.text?.toString()?.trim() ?: ""
        val fingerprint = replyFingerprint()
        val isCleared = currentInput.isEmpty() || !currentInput.contains(fingerprint)
        val bubbleNodes = root?.findAccessibilityNodeInfosByText(fingerprint)
        val isSentBubble = bubbleNodes?.any { it != editNode } == true
        bubbleNodes?.forEach { it.recycle() }
        editNodes?.forEach { it.recycle() }
        root?.recycle()

        // 只有确认过回复真的写进输入框，输入框被清空才算送达；否则空输入框会被误判成发送成功
        if ((inputFilled && isCleared) || isSentBubble) {
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
