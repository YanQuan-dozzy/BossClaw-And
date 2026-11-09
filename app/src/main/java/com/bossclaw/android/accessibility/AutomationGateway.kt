package com.bossclaw.android.accessibility

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.bossclaw.android.agent.AgentBus
import com.bossclaw.android.accessibility.model.CardAnchor
import com.bossclaw.android.accessibility.model.ChatBubble
import com.bossclaw.android.accessibility.model.InboxScan
import com.bossclaw.android.accessibility.model.PageScene
import com.bossclaw.android.accessibility.model.ScrapedRawJob
import com.bossclaw.android.accessibility.model.UnreadChatSession
import com.bossclaw.android.accessibility.task.BaseAutomationTask
import com.bossclaw.android.accessibility.task.ChatDialogueTask
import com.bossclaw.android.accessibility.task.InspectDetailTask
import com.bossclaw.android.accessibility.task.InspectMessageListTask
import com.bossclaw.android.accessibility.task.ScrollFeedTask
import com.bossclaw.android.accessibility.task.SendGreetingTask
import com.bossclaw.android.accessibility.util.AccessibilityNodeUtil
import com.bossclaw.android.accessibility.util.TabNavHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class ManualSendOutcome(val ok: Boolean, val note: String = "")

class AutomationGateway(private val context: Context) {

    val service: BossClawAccessibilityService?
        get() = BossClawAccessibilityService.instance

    val isConnected: Boolean
        get() = service != null

    fun scene(): PageScene {
        val root = bossActiveRoot() ?: return PageScene.UNKNOWN
        val scene = SceneDetector.detect(root)
        root.recycle()
        return scene
    }

    /** 当前前台窗口是否真的是 BOSS；手势只会落到前台应用，后台的 BOSS 一律不可操作。 */
    fun isBossActive(): Boolean {
        val root = bossActiveRoot() ?: return false
        root.recycle()
        return true
    }

    suspend fun openBoss(): Boolean = withContext(Dispatchers.Main) {
        val active = service ?: return@withContext false
        val intent = runCatching { resolveBossLaunchIntent(active) }.getOrNull()
            ?: return@withContext false
        val launched = runCatching { context.startActivity(intent) }.isSuccess
        if (launched) delay(1200)
        awaitBossForeground(3000)
    }

    /** 唤醒 BOSS 主界面，并确认它已成为前台活动窗口。 */
    private suspend fun ensureBossActive(): Boolean {
        if (isBossActive()) return true
        repeat(3) {
            if (openBoss()) return true
            delay(800)
        }
        return isBossActive()
    }

    private fun resolveBossLaunchIntent(active: BossClawAccessibilityService): Intent? {
        val explicit = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = android.content.ComponentName(BOSS_PACKAGE, BOSS_WELCOME_ACTIVITY)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        if (active.packageManager.resolveActivity(explicit, 0) != null) return explicit
        return active.packageManager.getLaunchIntentForPackage(BOSS_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
    }

    private suspend fun awaitBossForeground(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isBossActive()) return true
            delay(400)
        }
        return isBossActive()
    }

    /** 只取 BOSS 的前台窗口根节点，避免拿后台窗口做判断。 */
    private fun bossActiveRoot(): AccessibilityNodeInfo? {
        val active = service ?: return null
        val root = active.rootInActiveWindow ?: return null
        if (root.packageName != BOSS_PACKAGE) {
            root.recycle()
            return null
        }
        return root
    }

    suspend fun ensureFeed(): Boolean {
        val active = service ?: return false
        if (!ensureBossActive()) return false
        // 退出详情/会话页失败时直接放弃本轮，绝不盲目点底部区域，避免误触“继续沟通”又回到会话页
        if (!leaveDetailOrChat(active)) return false
        if (!TabNavHelper.isJobTabSelected(active)) {
            switchToJobTab()
            delay(800)
        }
        repeat(3) {
            if (scene() == PageScene.RECOMMEND_LIST) return true
            delay(500)
        }
        return scene() == PageScene.RECOMMEND_LIST
    }

    suspend fun ensureMessageTab(): Boolean {
        val active = service ?: return false
        if (!ensureBossActive()) return false
        if (!leaveDetailOrChat(active)) return false
        if (!TabNavHelper.isMessageTabSelected(active)) {
            switchToMessageTab()
            delay(800)
        }
        repeat(3) {
            if (scene() == PageScene.MESSAGE_LIST) return true
            delay(500)
        }
        return scene() == PageScene.MESSAGE_LIST
    }

    /**
     * 从岗位详情或单聊页返回到带底部 Tab 的页面。
     * 连续返回后仍停留在详情/会话页时返回 false：此时页面底部是“立即沟通/发送”等按钮，
     * 继续点击 Tab 坐标会再次进入会话，形成详情页与会话页来回跳。
     */
    private suspend fun leaveDetailOrChat(active: BossClawAccessibilityService): Boolean {
        var attempts = 0
        while (attempts < 3) {
            val root = bossActiveRoot() ?: break
            val stuck = hasDetailOrChat(root)
            root.recycle()
            if (!stuck) return true
            back()
            delay(900)
            attempts++
        }
        val root = bossActiveRoot() ?: return true
        val stuck = hasDetailOrChat(root)
        root.recycle()
        return !stuck
    }

    private fun hasDetailOrChat(root: AccessibilityNodeInfo): Boolean {
        val detailNodes = root.findAccessibilityNodeInfosByViewId(ID_DETAIL_CHAT_BTN)
        val chatNodes = root.findAccessibilityNodeInfosByViewId(ID_CHAT_EDIT_TEXT)
        val present = !detailNodes.isNullOrEmpty() || !chatNodes.isNullOrEmpty()
        detailNodes?.forEach { it.recycle() }
        chatNodes?.forEach { it.recycle() }
        return present
    }

    suspend fun switchToJobTab(): Boolean = suspendTab { success, fail -> TabNavHelper.switchToJobTab(service ?: return@suspendTab false, success, fail) }

    suspend fun switchToMessageTab(): Boolean = suspendTab { success, fail -> TabNavHelper.switchToMessageTab(service ?: return@suspendTab false, success, fail) }

    private suspend fun suspendTab(block: (() -> Unit, () -> Unit) -> Boolean): Boolean {
        val active = service ?: return false
        val deferred = CompletableDeferred<Boolean>()
        var completed = false
        val finish: (Boolean) -> Unit = { value ->
            if (!completed) {
                completed = true
                deferred.complete(value)
            }
        }
        val queued = withContext(Dispatchers.Main) {
            block({ finish(true) }, { finish(false) })
        }
        if (!queued) finish(false)
        return withTimeoutOrNull(10_000) { deferred.await() } ?: false
    }

    suspend fun scanCards(): List<CardAnchor> = withContext(Dispatchers.Main) {
        val active = service ?: return@withContext emptyList()
        scanCardsInternal(active)
    }

    private fun scanCardsInternal(active: BossClawAccessibilityService): List<CardAnchor> {
        val root = bossRoot(active) ?: return emptyList()
        val safeBounds = AccessibilityNodeUtil.getSafeScreenBounds(active)
        val anchors = mutableListOf<CardAnchor>()
        try {
            val cards = root.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/cl_card_container")
            if (cards.isNullOrEmpty()) return emptyList()
            for (card in cards) {
                val bounds = Rect()
                card.getBoundsInScreen(bounds)
                if (bounds.height() < 120 || !AccessibilityNodeUtil.isCenterInBounds(card, safeBounds)) {
                    card.recycle()
                    continue
                }
                val leafs = mutableListOf<Leaf>()
                collectLeafs(card, leafs)
                val salary = leafs.firstOrNull { SALARY_REGEX.containsMatchIn(it.text) }?.text.orEmpty()
                val (title, company) = resolveTitleAndCompany(leafs, bounds)
                if (title.isNotBlank() && company.isNotBlank()) {
                    val tags = leafs.map { it.text }
                        .filter { CARD_TAG_REGEX.containsMatchIn(it) }
                        .distinct()
                        .take(4)
                    // 卡片中心可能压在「立即沟通」按钮上，记录职位名区域作为打开详情的点击锚点
                    val titleBounds = leafs.firstOrNull { it.text == title }?.bounds?.let { Rect(it) }
                    anchors.add(CardAnchor(Rect(bounds), title, company, salary, tags, titleBounds))
                }
                card.recycle()
            }
            return anchors
        } finally {
            root.recycle()
        }
    }

    suspend fun inspectJob(card: CardAnchor): ScrapedRawJob? {
        val active = service ?: return null
        // 点卡片之前必须先回到推荐流，否则点击坐标会落在别的页面。
        if (!ensureFeed()) return null
        val deferred = CompletableDeferred<ScrapedRawJob?>()
        // 点击锚点优先取职位名区域，兜底取卡片上半区：卡片下半区可能是「立即沟通」按钮
        val clickBounds = card.titleBounds ?: Rect(
            card.bounds.left,
            card.bounds.top,
            card.bounds.right,
            card.bounds.top + card.bounds.height() / 2
        )
        val task = InspectDetailTask(
            cardClickBounds = clickBounds,
            onJobScraped = { if (!deferred.isCompleted) deferred.complete(it) },
            onError = { if (!deferred.isCompleted) deferred.complete(null) }
        )
        execute(active, task)
        return withTimeoutOrNull(150_000) { deferred.await() } ?: run {
            // 超时后立即停掉残留任务，避免旧任务继续点击页面与后续流程抢页面
            active.stopCurrentTask()
            null
        }
    }

    suspend fun scrollFeed(allowTabSwitch: Boolean = true): Boolean {
        val active = service ?: return false
        if (!ensureFeed()) return false
        val deferred = CompletableDeferred<Boolean>()
        val task = ScrollFeedTask(
            allowTabSwitch = allowTabSwitch,
            onCompleted = { if (!deferred.isCompleted) deferred.complete(true) },
            onFailed = { if (!deferred.isCompleted) deferred.complete(false) }
        )
        execute(active, task)
        return withTimeoutOrNull(150_000) { deferred.await() } ?: false
    }

    suspend fun sendGreeting(text: String, useCustomGreeting: Boolean = true): Boolean {
        val active = service ?: return false
        if (!ensureBossActive()) return false
        val deferred = CompletableDeferred<Boolean>()
        val task = SendGreetingTask(
            greetingText = text,
            useCustomGreeting = useCustomGreeting,
            autoNavigateBack = true,
            onComplete = { if (!deferred.isCompleted) deferred.complete(true) },
            onError = { if (!deferred.isCompleted) deferred.complete(false) }
        )
        execute(active, task)
        return withTimeoutOrNull(150_000) { deferred.await() } ?: run {
            active.stopCurrentTask()
            false
        }
    }

    suspend fun scanUnread(): InboxScan {
        val active = service ?: return InboxScan(null, "无障碍服务未连接")
        // 消息巡检前先确保停在【消息】Tab，否则 RecyclerView 必然定位失败。
        if (!ensureMessageTab()) return InboxScan(null, "未能切换到 BOSS 消息页，请确认 BOSS 已在前台")
        val deferred = CompletableDeferred<InboxScan>()
        val task = InspectMessageListTask(
            onSessionSelected = { if (!deferred.isCompleted) deferred.complete(InboxScan(it)) },
            onAllCleared = { if (!deferred.isCompleted) deferred.complete(InboxScan(null)) },
            onError = { if (!deferred.isCompleted) deferred.complete(InboxScan(null, it)) }
        )
        execute(active, task)
        return withTimeoutOrNull(120_000) { deferred.await() } ?: run {
            active.stopCurrentTask()
            InboxScan(null, "扫描未读超时")
        }
    }

    /**
     * 手动回复：先在消息列表定位到指定公司的目标岗位会话，再在当前单聊窗口填入并发送。
     * 找不到会话一律中止，绝不把消息发到错误的 HR 会话里。
     */
    suspend fun sendManualReply(company: String, title: String, text: String): ManualSendOutcome {
        val active = service ?: return ManualSendOutcome(false, "无障碍服务未连接")
        if (!openConversation(company, title)) {
            return ManualSendOutcome(false, "消息列表中没有找到该会话，请先在 BOSS 中打开")
        }
        delay(700)
        val sent = sendReply(null, text)
        return if (sent) ManualSendOutcome(true) else ManualSendOutcome(false, "回复发送失败")
    }

    /** 在消息列表按「公司 + 岗位名」打开会话，找不到时返回 false。 */
    suspend fun openConversation(company: String, title: String): Boolean {
        val active = service ?: return false
        if (scene() != PageScene.MESSAGE_LIST) {
            openBoss()
            delay(1000)
        }
        if (!ensureMessageTab()) return false
        val deferred = CompletableDeferred<Boolean>()
        val task = InspectMessageListTask(
            targetCompany = company,
            targetTitle = title,
            maxScrollRounds = 4,
            onSessionSelected = { if (!deferred.isCompleted) deferred.complete(true) },
            onTargetMissing = { if (!deferred.isCompleted) deferred.complete(false) },
            onError = { if (!deferred.isCompleted) deferred.complete(false) }
        )
        execute(active, task)
        val opened = withTimeoutOrNull(90_000) { deferred.await() } ?: run {
            active.stopCurrentTask()
            false
        }
        if (opened) delay(900)
        return opened
    }

    suspend fun sendReply(session: UnreadChatSession?, text: String): Boolean {
        val active = service ?: return false
        val deferred = CompletableDeferred<Boolean>()
        val task = ChatDialogueTask(
            session = session,
            replyText = text,
            onMessageSent = {},
            onBackCompleted = { if (!deferred.isCompleted) deferred.complete(true) },
            onError = { if (!deferred.isCompleted) deferred.complete(false) }
        )
        execute(active, task)
        return withTimeoutOrNull(150_000) { deferred.await() } ?: run {
            active.stopCurrentTask()
            false
        }
    }

    suspend fun readChatBubbles(): List<ChatBubble> = withContext(Dispatchers.Main) {
        val active = service ?: return@withContext emptyList()
        readChatInternal(active)
    }

    private fun readChatInternal(active: BossClawAccessibilityService): List<ChatBubble> {
        val root = bossRoot(active) ?: return emptyList()
        val screen = AccessibilityNodeUtil.getScreenBounds(active)
        val listNodes = root.findAccessibilityNodeInfosByViewId("com.hpbr.bosszhipin:id/recyclerView")
        val container = listNodes?.maxByOrNull { node ->
            val rect = Rect(); node.getBoundsInScreen(rect); rect.width() * rect.height()
        }
        val bubbles = mutableListOf<Pair<Int, ChatBubble>>()
        try {
            val scanRoot = container ?: root
            collectBubbles(scanRoot, Rect(0, 0, screen.width(), screen.height()), bubbles)
        } finally {
            if (container != null && container != root) root.recycle()
            else root.recycle()
        }
        if (bubbles.isEmpty()) return emptyList()
        val ordered = bubbles.sortedBy { it.first }.map { it.second }
        val deduped = ordered.fold(mutableListOf<ChatBubble>()) { acc, item ->
            val last = acc.lastOrNull()
            if (last?.role != item.role || last.text != item.text) acc.add(item)
            acc
        }
        return deduped.takeLast(20)
    }

    private fun collectBubbles(node: AccessibilityNodeInfo?, screen: Rect, out: MutableList<Pair<Int, ChatBubble>>) {
        if (node == null) return
        val text = node.text?.toString()?.trim().orEmpty()
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (text.length in 1..600 && bounds.width() > 0 && screen.height() * 0.12f < bounds.centerY() &&
            bounds.centerY() < screen.height() * 0.88f &&
            !isNoise(text)
        ) {
            val ratio = bounds.centerX().toFloat() / screen.width().toFloat()
            if (ratio < 0.30f || ratio > 0.70f) {
                val role = if (ratio <= 0.5f) "hr" else "me"
                out.add(bounds.top to ChatBubble(role = role, text = text, leftRatio = ratio))
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectBubbles(child, screen, out)
            child.recycle()
        }
    }

    private fun isNoise(text: String): Boolean {
        if (Regex("""^\d{1,2}:\d{2}$""").matches(text)) return true
        if (Regex("""^(今天|昨天|周一|周二|周三|周四|周五|周六|周日)$""").matches(text)) return true
        val noise = listOf("发送", "按住 说话", "表情", "图片", "职位", "在线", "已读", "未读")
        return noise.any { text == it } || text.contains("已与对方建立联系") ||
            text.contains("安全提示") || text.contains("求职助手") || text.contains("交换电话")
    }

    suspend fun back() {
        val active = service ?: return
        // 只有 BOSS 在前台时才返回，避免把返回键打到 BossClaw 自己或其他应用上。
        if (!isBossActive()) return
        withContext(Dispatchers.Main) { active.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) }
        delay(500)
    }

    fun stopCurrentTask() {
        BossClawAccessibilityService.instance?.stopCurrentTask()
    }

    private suspend fun execute(active: BossClawAccessibilityService, task: BaseAutomationTask) {
        withContext(Dispatchers.Main) { active.executeTask(task) }
    }

    private fun bossRoot(active: BossClawAccessibilityService): AccessibilityNodeInfo? {
        val defaultRoot = active.rootInActiveWindow
        if (defaultRoot != null && defaultRoot.packageName == BOSS_PACKAGE) return defaultRoot
        defaultRoot?.recycle()
        for (window in active.windows) {
            if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                val root = window.root
                if (root != null && root.packageName == BOSS_PACKAGE) return root
                root?.recycle()
            }
        }
        return null
    }

    private data class Leaf(val viewId: String, val text: String, val bounds: Rect)

    private fun collectLeafs(node: AccessibilityNodeInfo?, out: MutableList<Leaf>) {
        if (node == null) return
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val actual = if (text.isNotEmpty()) text else desc
        if (actual.isNotEmpty() && actual.length < 60) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            out.add(Leaf(node.viewIdResourceName.orEmpty(), actual, bounds))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectLeafs(child, out)
            child.recycle()
        }
    }

    private fun resolveTitleAndCompany(leafs: List<Leaf>, cardBounds: Rect): Pair<String, String> {
        var title = ""
        var company = ""
        for (leaf in leafs) {
            val id = leaf.viewId.lowercase()
            if (title.isEmpty() && (id.contains("position") || id.contains("job_name") || id.contains("title"))) title = leaf.text
            if (company.isEmpty() && (id.contains("company") || id.contains("brand") || id.contains("com_name"))) company = leaf.text
        }
        if (title.isEmpty() || company.isEmpty()) {
            val sorted = leafs.sortedBy { it.bounds.top }
            if (title.isEmpty()) {
                val cutoff = cardBounds.top + (cardBounds.height() * 0.42f).toInt()
                title = sorted.firstOrNull { it.bounds.top <= cutoff && !SALARY_REGEX.containsMatchIn(it.text) }?.text.orEmpty()
            }
            if (company.isEmpty()) {
                val cutoff = cardBounds.top + (cardBounds.height() * 0.35f).toInt()
                company = sorted.firstOrNull {
                    it.bounds.top > cutoff && it.text != title &&
                        !SALARY_REGEX.containsMatchIn(it.text) &&
                        !TAG_REGEX.containsMatchIn(it.text) && it.text.length >= 2
                }?.text.orEmpty()
            }
        }
        return title to company
    }

    companion object {
        const val BOSS_PACKAGE = "com.hpbr.bosszhipin"
        private const val BOSS_WELCOME_ACTIVITY = "com.hpbr.bosszhipin.module.launcher.WelcomeActivity"
        private const val ID_DETAIL_CHAT_BTN = "com.hpbr.bosszhipin:id/btn_chat"
        private const val ID_CHAT_EDIT_TEXT = "com.hpbr.bosszhipin:id/editText_with_scrollbar"
        private val SALARY_REGEX = Regex("""(\d+[-~至]\d+[Kk万·元]|面议|\d+元/[天月小时])""")
        private val TAG_REGEX = Regex("""(经验|年|应届|本科|大专|硕士|博士|学历|不限|在校|先生|女士|HR|招聘|活跃|刚刚|今日)""")
        /** 职位列表卡片上的实习相关短标签，用于本地对齐 BOSS 筛选面板的时长 / 出勤档位。 */
        private val CARD_TAG_REGEX = Regex("""(^\d{1,2}\s*天\s*/\s*周$|^\d{1,2}\s*个月$)""")
    }
}
