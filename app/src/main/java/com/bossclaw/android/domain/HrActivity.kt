package com.bossclaw.android.domain

/**
 * HR 活跃度分级（7 级口径）：
 * 在线(7) > 刚刚活跃(6) > 今日活跃(5) > 3日内活跃(4) > 本周活跃(3) > 本月活跃(2) > 半年前活跃(1)
 * rank=0 表示未识别，不参与过滤，避免误杀。
 */
object HrActivity {

    const val ANY = "any"
    const val MONTH = "month"
    const val WEEK = "week"
    const val THREE_DAYS = "3days"
    const val TODAY = "today"
    const val JUST_ACTIVE = "justActive"
    const val ONLINE = "online"

    val OPTIONS: List<Pair<String, String>> = listOf(
        ANY to "不限",
        MONTH to "本月活跃及以上",
        WEEK to "本周活跃及以上",
        THREE_DAYS to "3日内活跃及以上",
        TODAY to "今日活跃及以上",
        JUST_ACTIVE to "刚刚活跃及以上",
        ONLINE to "仅在线"
    )

    private val FILTER_RANK = mapOf(
        ANY to 0,
        MONTH to 2,
        WEEK to 3,
        THREE_DAYS to 4,
        TODAY to 5,
        JUST_ACTIVE to 6,
        ONLINE to 7
    )

    private val ONLINE_LABEL = Regex("""(?:^|[^\u4e00-\u9fa5A-Za-z0-9])在线(?:[^\u4e00-\u9fa5A-Za-z0-9]|$)""")
    private val ONLINE_PHRASE = Regex("""^(?:当前在线|正在在线|在线中|在线状态)$""")

    fun label(filter: String?): String =
        OPTIONS.firstOrNull { it.first == filter }?.second ?: "不限"

    fun rank(active: String?): Int {
        val text = active.orEmpty().trim()
        if (text.isBlank()) return 0
        if (ONLINE_LABEL.containsMatchIn(text) || ONLINE_PHRASE.matches(text)) return 7
        if (Regex("""刚刚活跃|刚活跃|几分钟前活跃""").containsMatchIn(text)) return 6
        if (Regex("""今日活跃|今天活跃""").containsMatchIn(text)) return 5
        Regex("""(\d+)\s*日内活跃""").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { days ->
            return when {
                days <= 3 -> 4
                days <= 7 -> 3
                days <= 30 -> 2
                else -> 1
            }
        }
        Regex("""([0-9]+|[一二两三四五六七八九十])\s*(?:个)?(?:周|星期)内活跃""").find(text)?.groupValues?.get(1)?.let { raw ->
            val weeks = cnNum(raw)
            return when {
                weeks <= 1 -> 3
                weeks <= 4 -> 2
                else -> 1
            }
        }
        Regex("""([0-9]+|[一二两三四五六七八九十半])\s*(?:个)?月内活跃""").find(text)?.groupValues?.get(1)?.let { raw ->
            return if (cnNum(raw) <= 1) 2 else 1
        }
        if (Regex("""本周活跃""").containsMatchIn(text)) return 3
        if (Regex("""本月活跃""").containsMatchIn(text)) return 2
        if (Regex("""半年前活跃|数月前活跃|很久前活跃|近半年活跃|半年内活跃|半年活跃""").containsMatchIn(text)) return 1
        return 0
    }

    fun meets(active: String?, filter: String?): Boolean {
        val target = filter ?: ANY
        if (target == ANY) return true
        val current = rank(active)
        if (current == 0) return true
        return current >= (FILTER_RANK[target] ?: 0)
    }

    private fun cnNum(text: String): Int {
        val map = mapOf(
            "半" to 0.5, "一" to 1.0, "二" to 2.0, "两" to 2.0, "三" to 3.0, "四" to 4.0,
            "五" to 5.0, "六" to 6.0, "七" to 7.0, "八" to 8.0, "九" to 9.0, "十" to 10.0
        )
        return if (Regex("""^[0-9]+$""").matches(text)) text.toInt() else (map[text] ?: Double.MAX_VALUE).toInt()
    }
}
