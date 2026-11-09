package com.bossclaw.android.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.bossclaw.android.accessibility.model.PageScene

object SceneDetector {

    private const val ID_CHAT_EDIT_TEXT = "com.hpbr.bosszhipin:id/editText_with_scrollbar"
    private const val ID_DETAIL_CHAT_BTN = "com.hpbr.bosszhipin:id/btn_chat"
    private const val ID_DETAIL_JOB_NAME = "com.hpbr.bosszhipin:id/tv_job_name"
    private const val ID_TAB_3_TEXT = "com.hpbr.bosszhipin:id/tv_tab_3"
    private const val ID_CONTACT_VP = "com.hpbr.bosszhipin:id/contact_vp"
    private const val ID_FEED_LIST = "com.hpbr.bosszhipin:id/rv_list"

    private val RISK_TEXTS = listOf(
        "访问过于频繁", "操作过于频繁", "操作频繁", "请求过于频繁",
        "账号异常", "账户异常", "账号冻结", "账户冻结", "账号封禁", "账户封禁", "账号受限",
        "暂停打招呼功能", "已达今日沟通上限",
        "安全验证", "滑动验证", "滑块验证", "点选验证", "图形验证", "行为验证", "拖动滑块", "人脸识别",
        "环境异常", "检测到异常环境", "非浏览器环境",
        "请先登录", "登录已过期", "重新登录", "登录失效", "会话失效"
    )

    /** 详情页底部的平台安全提示属于固定文案，其中包含“违法违规行为”等词，不能当成风控信号 */
    private val DISCLAIMER_TEXTS = listOf(
        "求职谨防诈骗", "严厉打击虚假招聘", "如遇招聘方以任何名义索要财物", "请立即举报"
    )

    fun detect(root: AccessibilityNodeInfo?): PageScene {
        if (root == null) return PageScene.UNKNOWN
        if (RISK_TEXTS.any { hasText(root, it) }) {
            return PageScene.CAPTCHA_RISK
        }
        if (hasId(root, ID_CHAT_EDIT_TEXT)) return PageScene.CHAT_WINDOW
        if (hasId(root, ID_DETAIL_CHAT_BTN) || hasId(root, ID_DETAIL_JOB_NAME)) return PageScene.JOB_DETAIL

        val tab3 = root.findAccessibilityNodeInfosByViewId(ID_TAB_3_TEXT)
        val selected = tab3?.firstOrNull()?.isSelected == true
        tab3?.forEach { it.recycle() }
        if (selected || hasId(root, ID_CONTACT_VP)) return PageScene.MESSAGE_LIST
        if (hasId(root, ID_FEED_LIST)) return PageScene.RECOMMEND_LIST
        return PageScene.UNKNOWN
    }

    private fun hasId(root: AccessibilityNodeInfo, id: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        val exists = !nodes.isNullOrEmpty()
        nodes?.forEach { it.recycle() }
        return exists
    }

    private fun hasText(root: AccessibilityNodeInfo, text: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText(text) ?: return false
        var matched = false
        for (node in nodes) {
            val content = node.text?.toString().orEmpty()
            // 平台安全提示整段跳过，避免固定文案触发风控暂停
            if (DISCLAIMER_TEXTS.none { content.contains(it) }) matched = true
            node.recycle()
        }
        return matched
    }
}
