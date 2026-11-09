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
    val salaryText: String = "",
    /** 卡片上读到的短标签（如「4天/周」「4个月」），用于对齐 BOSS 筛选面板的时长与出勤档位 */
    val tags: List<String> = emptyList(),
    /** 职位名节点的屏幕区域：点击这里进入详情，避开卡片上的「立即沟通」按钮 */
    val titleBounds: Rect? = null
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
