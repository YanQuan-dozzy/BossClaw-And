package com.bossclaw.android.accessibility.task

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.bossclaw.android.accessibility.model.ScrapedRawJob
import com.bossclaw.android.accessibility.util.AccessibilityNodeUtil
import com.bossclaw.android.accessibility.util.GestureEngine
import kotlin.math.abs

class InspectDetailTask(
    private val cardClickBounds: Rect? = null,
    private val onJobScraped: ((ScrapedRawJob) -> Unit)? = null,
    private val onError: ((String) -> Unit)? = null
) : BaseAutomationTask() {

    companion object {
        private const val STEP_CLICK_CARD = 1
        private const val STEP_VERIFY_DETAIL_PAGE = 2
        private const val STEP_EXPAND_DESCRIPTION = 3
        private const val STEP_VERIFY_EXPANDED = 4
        private const val STEP_EXTRACT_DATA = 5

        /** JD 文本可能晚于标题渲染，允许再等几轮，避免采到空 JD */
        private const val MAX_JD_WAIT_ROUNDS = 2

        /** 展开入口在首屏下方时只允许小幅下滑一次，入口找不到就直接提取，避免无展开入口的岗位被滑到底 */
        private const val MAX_REVEAL_SCROLLS = 1

        /** 点击展开入口后仍无变化时，允许用坐标补点的次数 */
        private const val MAX_EXPAND_CLICKS = 2

        /** JD 折叠时平台提供的展开入口文案 */
        private val EXPAND_LABELS = listOf("查看更多", "查看全部", "展开全部", "展开", "显示详细")

        /** 详情页底部的平台提示，出现即代表页面已经滑到底部 */
        private val BOTTOM_HINTS = listOf("求职谨防诈骗", "严厉打击虚假招聘", "如遇招聘方以任何名义索要财物", "防骗指南")

        /** 匹配入口文案前先去掉这些符号，兼容“查看更多…”“查看更多 >”等写法 */
        private val LABEL_NOISE = Regex("""[\s…\.。,，、:：>›»]+""")

        private const val ID_JOB_NAME = "com.hpbr.bosszhipin:id/tv_job_name"
        private const val ID_JOB_SALARY = "com.hpbr.bosszhipin:id/tv_job_salary"
        private const val ID_LOCATION = "com.hpbr.bosszhipin:id/tv_required_location"
        private const val ID_BOSS_NAME = "com.hpbr.bosszhipin:id/tv_boss_name"
        private const val ID_BOSS_TITLE = "com.hpbr.bosszhipin:id/tv_boss_title"
        private const val ID_BOSS_LABEL = "com.hpbr.bosszhipin:id/boss_label_tv"
        private const val ID_DESCRIPTION = "com.hpbr.bosszhipin:id/tv_description"
        private const val ID_COMPANY_NAME = "com.hpbr.bosszhipin:id/tv_com_name"
        private const val ID_CHAT_BUTTON = "com.hpbr.bosszhipin:id/btn_chat"
        private const val ID_CHAT_INPUT = "com.hpbr.bosszhipin:id/editText_with_scrollbar"
    }

    private var jdWaitRounds = 0
    private var revealScrolls = 0
    private var expandClicks = 0
    private var descriptionBeforeExpand = ""
    private var lastExpandBounds: Rect? = null

    private var title = ""
    private var company = ""
    private var salary = ""
    private var location = ""
    private var hrName = ""
    private var hrTitle = ""
    private var hrActive = ""

    override fun onStep(step: Int) {
        when (step) {
            STEP_CLICK_CARD -> handleClickCard()
            STEP_VERIFY_DETAIL_PAGE -> handleVerifyDetailPage()
            STEP_EXPAND_DESCRIPTION -> handleExpandDescription()
            STEP_VERIFY_EXPANDED -> handleVerifyExpanded()
            STEP_EXTRACT_DATA -> handleExtractData()
        }
    }

    private fun handleClickCard() {
        val bounds = cardClickBounds
        if (bounds == null) {
            sendStep(STEP_VERIFY_DETAIL_PAGE, 100L)
            return
        }
        sendTaskMessage("打开目标岗位详情")
        val clicked = GestureEngine.clickAt(
            service,
            bounds.centerX(),
            bounds.centerY(),
            onSuccess = { nextStep(900L) },
            onFail = { retryCurrentStep(400L, "点击卡片失败") }
        )
        if (!clicked) retryCurrentStep(500L, "无法派发卡片点击")
    }

    private fun handleVerifyDetailPage() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "等待详情页渲染")
            return
        }
        // 卡片点击若落到「立即沟通」，页面会直接进入会话；这里立即退出，避免停留并误触发送
        val chatInputs = root.findAccessibilityNodeInfosByViewId(ID_CHAT_INPUT).orEmpty()
        val enteredChat = chatInputs.isNotEmpty()
        chatInputs.forEach { it.recycle() }
        if (enteredChat) {
            root.recycle()
            service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            failedTask("点击卡片进入会话页，已退出")
            return
        }
        val hasChatBtn = root.findAccessibilityNodeInfosByViewId(ID_CHAT_BUTTON).isNotEmpty()
        val hasJobName = root.findAccessibilityNodeInfosByViewId(ID_JOB_NAME).isNotEmpty()
        if (hasChatBtn || hasJobName) {
            // 展开 JD 时页面会下滑，标题与公司可能滚出可视区或节点被回收，先在这里把头部字段抓稳
            captureHeader(root)
            root.recycle()
            nextStep(200L)
        } else {
            root.recycle()
            retryCurrentStep(500L, "尚未检测到详情页特征元素")
        }
    }

    /**
     * 详情页的 JD 通常是折叠展示的，只靠滚动无法展开正文，还会一路滑到页面底部的安全提示。
     * 这里先找 JD 区域的“查看更多/展开”入口点击；入口在首屏下方时向下滑动把它带进可视区，
     * 点击后由 [handleVerifyExpanded] 校验正文是否真的变长，未生效时补点一次再提取。
     */
    private fun handleExpandDescription() {
        val root = service.rootInActiveWindow
        if (root == null) {
            sendStep(STEP_EXTRACT_DATA, 400L)
            return
        }
        val expandNode = findExpandNode(root)
        if (expandNode != null) {
            descriptionBeforeExpand = extractDescription(root)
            expandClicks++
            val bounds = Rect()
            expandNode.getBoundsInScreen(bounds)
            lastExpandBounds = if (bounds.width() > 0 && bounds.height() > 0) Rect(bounds) else null
            val label = expandNode.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }
                ?: expandNode.contentDescription?.toString()?.trim().orEmpty()
            root.recycle()
            sendTaskMessage("点击职位详情展开入口【$label】")
            var settled = false
            fun resume(delay: Long) {
                if (settled) return
                settled = true
                sendStep(STEP_VERIFY_EXPANDED, delay)
            }
            val clicked = GestureEngine.performClick(
                service,
                expandNode,
                onSuccess = { resume(900L) },
                onFail = { resume(400L) }
            )
            expandNode.recycle()
            if (!clicked) resume(400L)
            return
        }
        // 并非每个岗位都有「查看更多」：JD 已完整展示、页面已到底或没有 JD 时都直接提取，不盲目下滑
        if (descriptionBeforeExpand.isEmpty()) {
            descriptionBeforeExpand = extractDescription(root)
        }
        val canReveal = shouldRevealByScroll(root)
        root.recycle()
        if (!canReveal || revealScrolls >= MAX_REVEAL_SCROLLS) {
            sendStep(STEP_EXTRACT_DATA, 100L)
            return
        }
        revealScrolls++
        sendTaskMessage("详情展开入口不在可视区，小幅下滑第 $revealScrolls 次")
        val screen = AccessibilityNodeUtil.getSafeScreenBounds(service)
        val x = (screen.width() * 0.5f).toInt()
        // 每次只滑动约 30% 屏高，避免一路滑到页面底部的安全提示
        val startY = (screen.top + screen.height() * 0.70f).toInt()
        val endY = (screen.top + screen.height() * 0.40f).toInt()
        var scrolled = false
        fun resumeNext() {
            if (scrolled) return
            scrolled = true
            sendStep(STEP_EXPAND_DESCRIPTION, 700L)
        }
        val queued = GestureEngine.swipeBezier(
            service,
            x,
            startY,
            x,
            endY,
            480L,
            onSuccess = { resumeNext() },
            onFail = { resumeNext() }
        )
        if (!queued) resumeNext()
    }

    /**
     * 是否值得继续下滑找展开入口。
     * 页面出现底部平台提示、JD 已完整落在屏幕内，或根本没有 JD 节点时都返回 false，
     * 避免没有「查看更多」的岗位被一路滑到底部。
     */
    private fun shouldRevealByScroll(root: AccessibilityNodeInfo): Boolean {
        val screen = AccessibilityNodeUtil.getSafeScreenBounds(service)
        for (hint in BOTTOM_HINTS) {
            val nodes = root.findAccessibilityNodeInfosByText(hint)
            val found = !nodes.isNullOrEmpty()
            nodes?.forEach { it.recycle() }
            if (found) return false
        }
        val nodes = root.findAccessibilityNodeInfosByViewId(ID_DESCRIPTION) ?: return false
        var extendsBelowScreen = false
        var hasText = false
        for (node in nodes) {
            val text = node.text?.toString()?.trim().orEmpty()
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            node.recycle()
            if (text.isEmpty()) continue
            hasText = true
            if (bounds.bottom > screen.bottom + 20) extendsBelowScreen = true
        }
        return hasText && extendsBelowScreen
    }

    /**
     * 校验展开是否生效：正文变长说明展开成功；入口消失说明页面已切到全文页，同样可以提取。
     * 入口还在且正文没变时，用上一次记录的坐标补点一次，避免无障碍节点点击未落到入口上。
     */
    private fun handleVerifyExpanded() {
        val root = service.rootInActiveWindow
        if (root == null) {
            sendStep(STEP_EXTRACT_DATA, 400L)
            return
        }
        val afterLength = extractDescription(root).length
        val expandEntry = findExpandNode(root)
        val stillHasEntry = expandEntry != null
        expandEntry?.recycle()
        root.recycle()

        if (afterLength > descriptionBeforeExpand.length + 20 || !stillHasEntry) {
            sendTaskMessage(
                if (afterLength > descriptionBeforeExpand.length) {
                    "JD 已展开，正文 ${descriptionBeforeExpand.length}→$afterLength 字"
                } else {
                    "展开入口已消失，按全文页提取"
                }
            )
            sendStep(STEP_EXTRACT_DATA, 200L)
            return
        }

        val bounds = lastExpandBounds
        if (expandClicks < MAX_EXPAND_CLICKS && bounds != null) {
            expandClicks++
            sendTaskMessage("展开点击未生效，按入口坐标补点一次")
            var settled = false
            fun resume(delay: Long) {
                if (settled) return
                settled = true
                sendStep(STEP_VERIFY_EXPANDED, delay)
            }
            val queued = GestureEngine.clickAt(
                service,
                bounds.centerX(),
                bounds.centerY(),
                onSuccess = { resume(900L) },
                onFail = { resume(400L) }
            )
            if (!queued) resume(400L)
            return
        }

        sendTaskMessage("未找到可用的展开入口，按当前可见正文提取")
        sendStep(STEP_EXTRACT_DATA, 100L)
    }

    /**
     * 优先选离 JD 区域最近的展开入口，避免误点公司介绍等其他模块的“展开”。
     * 同时兼容 text 与 contentDescription，并忽略“查看更多…”“查看更多 >”这类尾缀符号。
     */
    private fun findExpandNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val screen = AccessibilityNodeUtil.getSafeScreenBounds(service)
        val anchor = descriptionBottom(root, screen)
        var best: AccessibilityNodeInfo? = null
        var bestDistance = Int.MAX_VALUE
        for (label in EXPAND_LABELS) {
            val nodes = root.findAccessibilityNodeInfosByText(label) ?: continue
            for (node in nodes) {
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                // 入口至少露出一小块，且中心点在屏幕内，保证坐标点击能落到它身上
                val visibleRect = Rect(bounds)
                val visible = visibleRect.intersect(screen) &&
                    visibleRect.width() >= 40 && visibleRect.height() >= 24 &&
                    screen.contains(bounds.centerX(), bounds.centerY())
                // 入口通常是靠近 JD 底部的小按钮，排除整块 JD 文本或半屏模块
                val smallEnough = bounds.height() <= screen.height() * 0.3f
                if (visible && smallEnough && (matchesExpandLabel(text) || matchesExpandLabel(desc))) {
                    val distance = abs(bounds.centerY() - anchor)
                    if (distance < bestDistance) {
                        best?.recycle()
                        best = node
                        bestDistance = distance
                        continue
                    }
                }
                node.recycle()
            }
        }
        return best
    }

    private fun matchesExpandLabel(raw: String): Boolean {
        val normalized = raw.replace(LABEL_NOISE, "")
        if (normalized.isEmpty() || normalized.length > 12) return false
        return EXPAND_LABELS.any { it == normalized }
    }

    /** 取可见 JD 节点的底边作为锚点；没有 JD 节点时退回到屏幕中部 */
    private fun descriptionBottom(root: AccessibilityNodeInfo, screen: Rect): Int {
        val nodes = root.findAccessibilityNodeInfosByViewId(ID_DESCRIPTION) ?: return screen.centerY()
        var anchor = screen.centerY()
        var top = Int.MIN_VALUE
        for (node in nodes) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.bottom > screen.top && bounds.top < screen.bottom && bounds.top > top) {
                top = bounds.top
                anchor = bounds.bottom
            }
            node.recycle()
        }
        return anchor
    }

    private fun handleExtractData() {
        val root = service.rootInActiveWindow
        if (root == null) {
            retryCurrentStep(400L, "提取数据时根节点为空")
            return
        }
        var description = ""
        try {
            captureHeader(root)
            description = extractDescription(root)
        } finally {
            root.recycle()
        }

        // 展开前已抓到更完整的正文时沿用旧文本，避免下滑后 JD 节点移出可视区导致正文反而变短
        if (description.length < descriptionBeforeExpand.length) {
            description = descriptionBeforeExpand
        }

        if (title.isBlank() && description.isBlank()) {
            retryCurrentStep(500L, "岗位名与 JD 均为空")
            return
        }

        if (description.isBlank() && jdWaitRounds < MAX_JD_WAIT_ROUNDS) {
            jdWaitRounds++
            retryCurrentStep(600L, "等待 JD 文本渲染")
            return
        }

        publishJob(description)
    }

    /**
     * 详情页可能把 JD 拆成多个同 ID 文本节点。
     * 这里拼接所有与屏幕有交集的节点，按纵坐标排序，避免只取第一个节点导致截断。
     */
    private fun extractDescription(root: AccessibilityNodeInfo): String {
        val nodes = root.findAccessibilityNodeInfosByViewId(ID_DESCRIPTION) ?: return ""
        val screen = AccessibilityNodeUtil.getSafeScreenBounds(service)
        val parts = mutableListOf<Pair<Int, String>>()
        for (node in nodes) {
            val text = node.text?.toString()?.trim().orEmpty()
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            node.recycle()
            // 画面内节点直接取；滚动后 JD 节点可能移出可视区，长文本仍视为正文保留
            val inScreen = bounds.bottom > screen.top && bounds.top < screen.bottom
            if (text.isNotEmpty() && (inScreen || text.length >= 200)) {
                parts += bounds.top to text
            }
        }
        return parts.sortedBy { it.first }.map { it.second }.distinct().joinToString("\n")
    }

    /** 头部字段只在缺失时补齐，避免滚动或展开后读到空值覆盖已采集内容 */
    private fun captureHeader(root: AccessibilityNodeInfo) {
        if (title.isNotBlank() && company.isNotBlank()) return
        title = safeExtractText(root, ID_JOB_NAME).ifBlank { title }
        company = safeExtractText(root, ID_COMPANY_NAME).ifBlank { company }
        salary = safeExtractText(root, ID_JOB_SALARY).ifBlank { salary }
        location = safeExtractText(root, ID_LOCATION).ifBlank { location }
        hrName = safeExtractText(root, ID_BOSS_NAME).ifBlank { hrName }
        hrTitle = safeExtractText(root, ID_BOSS_TITLE).ifBlank { hrTitle }
        hrActive = safeExtractText(root, ID_BOSS_LABEL).ifBlank { hrActive }
    }

    private fun publishJob(description: String) {
        val probe = "$location ${description.take(300)}"
        val experience = Regex("""\d{1,2}\s*[-~]\s*\d{1,2}\s*年|经验不限|应届|在校""")
            .find(probe)?.value.orEmpty()
        val degree = Regex("""本科|大专|硕士|博士|学历不限|中专|高中""").find(probe)?.value.orEmpty()

        val job = ScrapedRawJob(
            title = title,
            companyName = company,
            salaryText = salary,
            city = location,
            experience = experience,
            degree = degree,
            hrName = hrName,
            hrTitle = hrTitle,
            hrActiveStatus = hrActive,
            jobDescription = description,
            timestamp = System.currentTimeMillis()
        )
        if (!job.isValid()) {
            failedTask("采集信息不完整：${job.title} / ${job.companyName}")
            return
        }
        sendTaskMessage("已采集【${job.companyName} · ${job.title}】，JD ${description.length} 字")
        onJobScraped?.invoke(job)
        finishTask()
    }

    private fun safeExtractText(root: AccessibilityNodeInfo, viewId: String): String {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId) ?: return ""
        val text = nodes.firstOrNull()?.text?.toString()?.trim() ?: ""
        nodes.forEach { it.recycle() }
        return text
    }

    override fun failedTask(reason: String) {
        super.failedTask(reason)
        onError?.invoke(reason)
    }

    override fun onForceStopped() {
        onError?.invoke("岗位详情任务被强制终止")
    }
}
