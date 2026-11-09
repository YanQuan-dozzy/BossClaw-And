package com.bossclaw.android.accessibility.task

import android.accessibilityservice.AccessibilityService
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.bossclaw.android.accessibility.util.AccessibilityNodeUtil
import com.bossclaw.android.accessibility.util.GestureEngine

class SendGreetingTask(
    private val greetingText: String,
    /** true 填入自定义开场白；false 直接发送 BOSS 自带的官方招呼语，不覆写输入框 */
    private val useCustomGreeting: Boolean = true,
    private val autoNavigateBack: Boolean = true,
    private val onComplete: (() -> Unit)? = null,
    private val onError: ((String) -> Unit)? = null
) : BaseAutomationTask() {

    private var sendClickAttempts = 0

    /** 是否确认开场白已真正写进输入框；为空时不能把“输入框变空”当成送达 */
    private var inputFilled = false

    /** 等待聊天窗口渲染的轮次，单独计数，避免受通用重试上限约束过早放弃 */
    private var windowWaitRounds = 0

    /** 是否已经点过输入框；部分版本必须先点一下唤起键盘，ACTION_SET_TEXT 才会生效 */
    private var inputTapped = false

    // 确认弹窗/聊天窗口转场需要更宽裕的重试窗口
    override val maxRetryCount: Int = 5

    companion object {
        private const val STEP_INIT_AND_CLICK_CHAT = 1
        private const val STEP_WAIT_WINDOW_OR_POPUP = 2
        private const val STEP_INPUT_GREETING_TEXT = 3
        private const val STEP_CLICK_SEND_BUTTON = 4
        private const val STEP_VERIFY_AND_FINISH = 5
        private const val STEP_HANDLE_NAVIGATE_BACK = 6
        private const val STEP_HANDLE_OFFICIAL_GREETING = 7

        private const val BASE_SCREEN_WIDTH = 1220f
        private const val BASE_SCREEN_HEIGHT = 2712f
        private const val BASE_OFFSET_X = 85f
        private const val BASE_OFFSET_Y = 42f

        /** 聊天页转场的最长等待轮次，每轮 600ms */
        private const val MAX_WINDOW_WAIT_ROUNDS = 10

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
            STEP_HANDLE_OFFICIAL_GREETING -> handleOfficialGreeting()
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
            sendStep(if (useCustomGreeting) STEP_INPUT_GREETING_TEXT else STEP_HANDLE_OFFICIAL_GREETING, 150L)
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
            windowWaitRounds = 0
            sendStep(if (useCustomGreeting) STEP_INPUT_GREETING_TEXT else STEP_HANDLE_OFFICIAL_GREETING, 150L)
            return
        }
        val safeBounds = AccessibilityNodeUtil.getSafeScreenBounds(service)
        val confirmPopupBtn = AccessibilityNodeUtil.dfsFindNode(root, safeBounds) { node ->
            val txt = node.text?.toString() ?: ""
            txt == "发招呼" || txt == "打招呼" || txt == "确定" || txt == "确认"
        }
        if (confirmPopupBtn != null) {
            sendTaskMessage("检测到确认弹窗，点击推进")
            GestureEngine.performClick(
                service, confirmPopupBtn,
                onSuccess = {
                    confirmPopupBtn.recycle()
                    root.recycle()
                    // 官方招呼由平台随弹窗一并送出，自定义开场白则继续进入填写步骤
                    if (useCustomGreeting) nextStep(850L) else sendStep(STEP_HANDLE_OFFICIAL_GREETING, 850L)
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
        // 聊天页转场在低端机或网络慢时可能超过通用重试窗口，这里单独多等几轮
        if (windowWaitRounds < MAX_WINDOW_WAIT_ROUNDS) {
            windowWaitRounds++
            sendStep(STEP_WAIT_WINDOW_OR_POPUP, 600L)
        } else {
            failedTask("等待聊天窗口超时，未找到输入框")
        }
    }

    /**
     * 官方招呼模式：不生成也不覆写任何文本。
     * BOSS 通常会在进入会话时自动送出官方招呼语；若平台只是把官方招呼预填进输入框，
     * 则保持原文点击发送，确保官方招呼真正送达。
     */
    private fun handleOfficialGreeting() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "等待聊天窗口渲染")
            return
        }
        val editNodes = root.findAccessibilityNodeInfosByViewId(ID_EDIT_TEXT)
        val editNode = editNodes?.firstOrNull()
        if (editNode == null) {
            editNodes?.forEach { it.recycle() }
            root.recycle()
            if (windowWaitRounds < MAX_WINDOW_WAIT_ROUNDS) {
                windowWaitRounds++
                sendStep(STEP_HANDLE_OFFICIAL_GREETING, 600L)
            } else {
                failedTask("等待聊天窗口超时，未找到输入框")
            }
            return
        }
        val prefill = editNode.text?.toString()?.trim().orEmpty()
        editNodes.forEach { it.recycle() }
        root.recycle()
        if (prefill.isEmpty()) {
            sendTaskMessage("官方招呼语已由 BOSS 发送")
            sendStep(STEP_HANDLE_NAVIGATE_BACK, 500L)
            return
        }
        sendTaskMessage("发送 BOSS 官方招呼语")
        inputFilled = true
        sendClickAttempts = 0
        sendStep(STEP_CLICK_SEND_BUTTON, 300L)
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
        if (!inputTapped) {
            // 先像用户一样点一下输入框，唤起键盘和真实编辑态，避免 SET_TEXT 被忽略
            inputTapped = true
            sendTaskMessage("点击输入框准备填写")
            var settled = false
            fun resume() {
                if (settled) return
                settled = true
                sendStep(STEP_INPUT_GREETING_TEXT, 350L)
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
        val fingerprint = greetingFingerprint()
        // 部分聊天页不聚焦时 ACTION_SET_TEXT 会被忽略，先让输入框获得焦点
        editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, greetingText)
        }
        val success = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        editNode.refresh()
        val typed = editNode.text?.toString()?.trim().orEmpty()
        inputFilled = success && typed.contains(fingerprint)
        editNodes.forEach { it.recycle() }
        root.recycle()
        if (inputFilled) {
            nextStep(850L)
        } else {
            sendTaskMessage("开场白未进入输入框，重新填入")
            retryCurrentStep(450L, "输入框填词未生效")
        }
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
        // 发送前必须确认输入框里确实是待发送的内容，避免空输入框被误判成“已送出”
        val currentInput = editNode.text?.toString()?.trim().orEmpty()
        if (useCustomGreeting) {
            if (!currentInput.contains(greetingFingerprint())) {
                editNodes.forEach { it.recycle() }
                root.recycle()
                inputFilled = false
                sendTaskMessage("输入框里没有待发送的开场白，退回填写步骤")
                sendStep(STEP_INPUT_GREETING_TEXT, 300L)
                return
            }
        } else if (currentInput.isEmpty()) {
            // 官方招呼语已不在输入框里，说明平台已经送出，无需再点发送
            editNodes.forEach { it.recycle() }
            root.recycle()
            sendStep(STEP_HANDLE_NAVIGATE_BACK, 300L)
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
        val clicked = GestureEngine.clickAt(
            service, targetX, targetY,
            onSuccess = { nextStep(650L) },
            onFail = { retryCurrentStep(400L, "点击发送失败") }
        )
        if (!clicked) retryCurrentStep(400L, "无法派发发送手势")
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

    private fun greetingFingerprint(): String {
        val text = greetingText.trim()
        return if (text.length > 8) text.substring(0, 8) else text
    }

    private fun handleVerifyAndFinish() {
        val root = service.rootInActiveWindow
        val editNodes = root?.findAccessibilityNodeInfosByViewId(ID_EDIT_TEXT)
        val editNode = editNodes?.firstOrNull()
        editNode?.refresh()
        val currentInput = editNode?.text?.toString()?.trim() ?: ""
        if (useCustomGreeting) {
            val fingerprint = greetingFingerprint()
            val isCleared = currentInput.isEmpty() || !currentInput.contains(fingerprint)
            val bubbleNodes = root?.findAccessibilityNodeInfosByText(fingerprint)
            val isSentBubble = bubbleNodes?.any { it != editNode } == true
            bubbleNodes?.forEach { it.recycle() }
            editNodes?.forEach { it.recycle() }
            root?.recycle()

            // 只有确认过开场白真的写进输入框，输入框被清空才算送达；否则空输入框会被误判成发送成功
            if ((inputFilled && isCleared) || isSentBubble) {
                sendTaskMessage("开场白已送达")
                nextStep(500L)
            } else if (sendClickAttempts < 3) {
                sendStep(STEP_CLICK_SEND_BUTTON, 350L)
            } else {
                failedTask("点击发送未生效，消息未送出")
            }
            return
        }
        val isCleared = currentInput.isEmpty()
        editNodes?.forEach { it.recycle() }
        root?.recycle()
        if (inputFilled && isCleared) {
            sendTaskMessage("官方招呼语已送达")
            nextStep(500L)
        } else if (sendClickAttempts < 3) {
            sendStep(STEP_CLICK_SEND_BUTTON, 350L)
        } else {
            failedTask("点击发送未生效，官方招呼语未送出")
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
