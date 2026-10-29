package com.bossclaw.android.accessibility.task

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.bossclaw.android.accessibility.model.UnreadChatSession
import com.bossclaw.android.accessibility.util.GestureEngine

class InspectMessageListTask(
    private val onSessionSelected: ((UnreadChatSession) -> Unit)? = null,
    private val onAllCleared: (() -> Unit)? = null,
    private val onError: ((String) -> Unit)? = null,
    /** 指定目标会话时按公司与岗位名匹配（不要求未读），用于人工回复前打开对应会话 */
    private val targetCompany: String? = null,
    private val targetTitle: String? = null,
    /** 未在当前屏找到目标时向下滑动的最大次数 */
    private val maxScrollRounds: Int = 0,
    private val onTargetMissing: ((String) -> Unit)? = null
) : BaseAutomationTask() {

    private var scrollRounds = 0

    companion object {
        private const val STEP_SCAN_UNREAD_CARDS = 1
        private const val ID_RECYCLER_VIEW = "com.hpbr.bosszhipin:id/recyclerView"
        private const val ID_BADGE_VIEW = "com.hpbr.bosszhipin:id/badge_view"
        private const val ID_NAME = "com.hpbr.bosszhipin:id/tv_name"
        private const val ID_POSITION = "com.hpbr.bosszhipin:id/tv_position"
        private const val ID_SALARY = "com.hpbr.bosszhipin:id/tv_time"
        private const val ID_MESSAGE = "com.hpbr.bosszhipin:id/tv_msg"
    }

    override fun onStep(step: Int) {
        if (step == STEP_SCAN_UNREAD_CARDS) handleScanUnreadCards()
    }

    private fun handleScanUnreadCards() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "消息列表根节点为空")
            return
        }
        val rvNodes = root.findAccessibilityNodeInfosByViewId(ID_RECYCLER_VIEW)
        val rvNode = rvNodes?.firstOrNull()
        if (rvNode == null) {
            rvNodes?.forEach { it.recycle() }
            root.recycle()
            retryCurrentStep(500L, "未定位到消息列表 RecyclerView")
            return
        }

        val hasTarget = !targetCompany.isNullOrBlank() || !targetTitle.isNullOrBlank()
        var targetSession: UnreadChatSession? = null
        for (i in 0 until rvNode.childCount) {
            val item = rvNode.getChild(i) ?: continue
            val posNodes = item.findAccessibilityNodeInfosByViewId(ID_POSITION)
            if (posNodes.isNullOrEmpty()) {
                posNodes?.forEach { it.recycle() }
                item.recycle()
                continue
            }
            val rawPosition = posNodes[0].text?.toString()?.trim() ?: ""
            posNodes.forEach { it.recycle() }

            val badgeNodes = item.findAccessibilityNodeInfosByViewId(ID_BADGE_VIEW)
            val unread = parseUnreadCount(badgeNodes?.firstOrNull())
            badgeNodes?.forEach { it.recycle() }
            if (!hasTarget && unread <= 0) {
                item.recycle()
                continue
            }

            val name = extractTextById(item, ID_NAME)
            val salary = extractTextById(item, ID_SALARY)
            val lastMsg = extractTextById(item, ID_MESSAGE)
            val bounds = Rect()
            item.getBoundsInScreen(bounds)
            val parts = rawPosition.split("|", "｜")
            val company = parts.firstOrNull()?.trim().orEmpty()
            val title = if (parts.size > 1) parts[1].trim() else rawPosition

            // 指定目标时同时匹配公司与岗位名；两项都填时任一为空都视为不命中
            if (hasTarget && !matchesTarget(company, title)) {
                item.recycle()
                continue
            }

            targetSession = UnreadChatSession(
                hrName = name,
                companyName = company,
                jobTitle = title,
                salaryText = salary,
                lastMessage = lastMsg,
                unreadCount = unread,
                clickBounds = bounds
            )
            item.recycle()
            break
        }

        rvNodes.forEach { it.recycle() }
        root.recycle()

        if (targetSession == null) {
            if (hasTarget) {
                if (scrollRounds < maxScrollRounds) {
                    scrollRounds++
                    sendTaskMessage("目标会话不在当前屏，向下翻找第 $scrollRounds 次")
                    val queued = GestureEngine.swipeUp(
                        service,
                        onSuccess = { sendStep(STEP_SCAN_UNREAD_CARDS, 500L) },
                        onFail = { retryCurrentStep(400L, "会话列表滑动失败") }
                    )
                    if (!queued) retryCurrentStep(400L, "会话列表滑动无法派发")
                    return
                }
                sendTaskMessage("未找到目标会话")
                onTargetMissing?.invoke("消息列表中没有找到该会话")
                finishTask()
                return
            }
            sendTaskMessage("消息信箱已无未读")
            onAllCleared?.invoke()
            finishTask()
            return
        }

        val session = targetSession
        sendTaskMessage(
            if (hasTarget) "定位到会话：${session.companyName} · ${session.jobTitle}"
            else "发现未读：${session.hrName}（${session.companyName}）"
        )
        val clicked = GestureEngine.clickAt(
            service, session.clickBounds.centerX(), session.clickBounds.centerY(),
            onSuccess = {
                onSessionSelected?.invoke(session)
                finishTask()
            },
            onFail = { retryCurrentStep(400L, "点击未读条目失败") }
        )
        if (!clicked) retryCurrentStep(400L, "无法派发会话点击")
    }

    /** 目标匹配：公司与岗位名各自做双向子串匹配，忽略空白；未填的一项视为通配。 */
    private fun matchesTarget(company: String, title: String): Boolean {
        val wantCompany = normalize(targetCompany.orEmpty())
        val wantTitle = normalize(targetTitle.orEmpty())
        val actualCompany = normalize(company)
        val actualTitle = normalize(title)
        val companyOk = wantCompany.isBlank() ||
            (actualCompany.isNotBlank() && (actualCompany.contains(wantCompany) || wantCompany.contains(actualCompany)))
        val titleOk = wantTitle.isBlank() ||
            (actualTitle.isNotBlank() && (actualTitle.contains(wantTitle) || wantTitle.contains(actualTitle)))
        return companyOk && titleOk
    }

    private fun normalize(value: String): String = value.replace(Regex("""\s+"""), "")

    private fun parseUnreadCount(container: AccessibilityNodeInfo?): Int {
        if (container == null) return 0
        if (container.childCount == 0) {
            return container.text?.toString()?.trim()?.toIntOrNull() ?: 0
        }
        for (i in 0 until container.childCount) {
            val child = container.getChild(i) ?: continue
            val num = child.text?.toString()?.trim()?.toIntOrNull()
            child.recycle()
            if (num != null && num > 0) return num
        }
        return 0
    }

    override fun failedTask(reason: String) {
        super.failedTask(reason)
        onError?.invoke(reason)
    }

    override fun onForceStopped() {
        onError?.invoke("未读扫描任务被强制终止")
    }
}
