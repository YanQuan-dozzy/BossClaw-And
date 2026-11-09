package com.bossclaw.android.model

import com.bossclaw.android.data.db.JobEntity

enum class FitLevel(val raw: String, val label: String, val minScore: Int, val maxScore: Int) {
    STRONG("strong", "推荐", 81, 100),
    MATCH("match", "匹配", 65, 80),
    CAUTIOUS("cautious", "谨慎", 50, 64),
    UNFIT("unfit", "不推荐", 0, 49);

    companion object {
        fun from(raw: String?): FitLevel = entries.firstOrNull { it.raw == raw } ?: CAUTIOUS

        fun fromScore(score: Int): FitLevel = when {
            score >= STRONG.minScore -> STRONG
            score >= MATCH.minScore -> MATCH
            score >= CAUTIOUS.minScore -> CAUTIOUS
            else -> UNFIT
        }
    }
}

enum class RunMode(val raw: String, val label: String, val hint: String) {
    MANUAL("manual", "人工确认", "只评估不发送，开场白可复制到 BOSS 手动发送"),
    SEMI("semi", "半自动", "分数达到设定值自动发送，其余进入待确认队列"),
    AUTO("auto", "自动辅助", "分数达到设定值自动发送，触发风险立即暂停");

    companion object {
        fun from(raw: String?): RunMode = entries.firstOrNull { it.raw == raw } ?: SEMI
    }
}

data class LogLine(
    val id: Long = System.nanoTime(),
    val source: String,
    val message: String,
    val level: String = LEVEL_INFO,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val LEVEL_INFO = "info"
        const val LEVEL_SUCCESS = "success"
        const val LEVEL_WARN = "warn"
        const val LEVEL_ERROR = "error"
    }
}

data class DailyStats(
    val discovered: Int = 0,
    val filtered: Int = 0,
    val matched: Int = 0,
    val approved: Int = 0,
    val delivered: Int = 0,
    val replied: Int = 0,
    val interview: Int = 0,
    val tokensToday: Int = 0
)

fun JobEntity.highlights(): List<String> =
    com.bossclaw.android.core.JsonCodec.decode(highlightsJson, emptyList<String>())

fun JobEntity.risks(): List<String> =
    com.bossclaw.android.core.JsonCodec.decode(risksJson, emptyList<String>())

fun JobEntity.fit(): FitLevel = FitLevel.from(fitLevel)

fun JobEntity.statusLabel(): String = when (status) {
    JobEntity.STATUS_DISCOVERED -> "已发现"
    JobEntity.STATUS_FILTERED -> "已过滤"
    JobEntity.STATUS_MATCHED -> "待确认"
    JobEntity.STATUS_APPROVED -> "已批准"
    JobEntity.STATUS_DELIVERING -> "投递中"
    JobEntity.STATUS_DELIVERED -> "已投递"
    JobEntity.STATUS_REPLIED -> "已回复"
    JobEntity.STATUS_INTERVIEW -> "已约面"
    JobEntity.STATUS_REJECTED -> "已婉拒"
    JobEntity.STATUS_CLOSED -> "已结束"
    JobEntity.STATUS_FAILED -> "失败"
    else -> "未知"
}
