package com.bossclaw.android.agent

import com.bossclaw.android.accessibility.AutomationGateway
import com.bossclaw.android.accessibility.model.CardAnchor
import com.bossclaw.android.accessibility.model.ChatBubble
import com.bossclaw.android.accessibility.model.InboxScan
import com.bossclaw.android.accessibility.model.ScrapedRawJob
import com.bossclaw.android.accessibility.model.UnreadChatSession

class ScoutAgent(private val automator: AutomationGateway) {

    suspend fun prepareFeed(): Boolean = automator.ensureFeed()

    suspend fun scanCards(): List<CardAnchor> = automator.scanCards()

    suspend fun inspect(card: CardAnchor): ScrapedRawJob? = automator.inspectJob(card)

    suspend fun nextPage(): Boolean = automator.scrollFeed()

    suspend fun returnToFeed() {
        automator.ensureFeed()
    }

    suspend fun openInbox(): InboxScan {
        val ready = automator.ensureMessageTab()
        if (!ready) return InboxScan(null, "无法切换到消息页")
        return automator.scanUnread()
    }

    suspend fun readConversation(): List<ChatBubble> = automator.readChatBubbles()

    suspend fun sendConversationReply(session: UnreadChatSession, text: String): Boolean =
        automator.sendReply(session, text)
}
