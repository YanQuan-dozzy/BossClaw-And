package com.bossclaw.android.accessibility.model

import android.graphics.Rect

data class UnreadChatSession(
    val hrName: String,
    val companyName: String,
    val jobTitle: String,
    val salaryText: String,
    val lastMessage: String,
    val unreadCount: Int,
    val clickBounds: Rect,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun resolveJobId(): String = ScrapedRawJob.fingerprint(jobTitle, companyName, salaryText)
}

data class CardAnchor(
    val bounds: Rect,
    val title: String,
    val company: String,
    val salaryText: String = ""
) {
    fun fingerprint(): String = ScrapedRawJob.fingerprint(title, company, salaryText)
}

data class ChatBubble(
    val role: String,
    val text: String,
    val leftRatio: Float = 0f
)

data class InboxScan(
    val session: UnreadChatSession?,
    val error: String? = null
)
