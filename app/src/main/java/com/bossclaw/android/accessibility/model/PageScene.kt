package com.bossclaw.android.accessibility.model

enum class PageScene(val description: String, val isRisk: Boolean = false) {
    RECOMMEND_LIST("职位列表流"),
    MESSAGE_LIST("消息会话列表"),
    JOB_DETAIL("职位详情页"),
    CHAT_WINDOW("沟通聊天窗口"),
    CAPTCHA_RISK("风控验证拦截", true),
    UNKNOWN("未知/过渡场景");

    fun isRecommendList(): Boolean = this == RECOMMEND_LIST
    fun isMessageList(): Boolean = this == MESSAGE_LIST
    fun isDetail(): Boolean = this == JOB_DETAIL
    fun isChat(): Boolean = this == CHAT_WINDOW
}
