package com.bossclaw.android.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "jobs", indices = [Index("status"), Index("updatedAt")])
data class JobEntity(
    @PrimaryKey val jobId: String,
    val title: String,
    val company: String,
    val salaryText: String = "",
    val city: String = "",
    val experience: String = "",
    val degree: String = "",
    val hrName: String = "",
    val hrActive: String = "",
    val jd: String = "",
    val platform: String = "BOSS",
    val source: String = SOURCE_FEED,
    val fitLevel: String = "cautious",
    val score: Int = 0,
    val highlightsJson: String = "[]",
    val risksJson: String = "[]",
    val reason: String = "",
    val greeting: String = "",
    val status: String = STATUS_DISCOVERED,
    val isChatClosed: Boolean = false,
    val closedReason: String = "",
    val replyCount: Int = 0,
    val interviewFlag: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deliveredAt: Long? = null,
    val lastChatAt: Long? = null
) {
    companion object {
        const val STATUS_DISCOVERED = "discovered"
        const val STATUS_FILTERED = "filtered"
        const val STATUS_MATCHED = "matched"
        const val STATUS_APPROVED = "approved"
        const val STATUS_DELIVERING = "delivering"
        const val STATUS_DELIVERED = "delivered"
        const val STATUS_REPLIED = "replied"
        const val STATUS_INTERVIEW = "interview"
        const val STATUS_REJECTED = "rejected"
        const val STATUS_CLOSED = "closed"
        const val STATUS_FAILED = "failed"

        const val SOURCE_FEED = "feed"
        const val SOURCE_MESSAGE = "message"
        const val SOURCE_MANUAL = "manual"
    }
}

@Entity(tableName = "chat_messages", indices = [Index("jobId"), Index("timestamp")])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: String,
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val delivered: Boolean = true
) {
    companion object {
        const val ROLE_ME = "me"
        const val ROLE_HR = "hr"
        const val ROLE_SYSTEM = "system"
    }
}

@Entity(tableName = "logs", indices = [Index("timestamp")])
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val message: String,
    val level: String = "info",
    val jobId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "llm_audits", indices = [Index("createdAt")])
data class LlmAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scene: String,
    val jobId: String,
    val model: String,
    val prompt: String,
    val response: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val durationMs: Long,
    val success: Boolean,
    val error: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val years: Int = 0,
    val education: String = "",
    val skillsJson: String = "[]",
    val strengthsJson: String = "[]",
    val summary: String = "",
    val targetRolesJson: String = "[]",
    val targetCitiesJson: String = "[]",
    val minSalaryK: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "directions", indices = [Index("enabled")])
data class DirectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val query: String,
    val keywordsJson: String = "[]",
    val enabled: Boolean = true,
    val priority: Int = 1,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "companies")
data class CompanyEntity(
    @PrimaryKey val companyName: String,
    val blacklisted: Boolean = true,
    val reason: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "skills", indices = [Index("scope")])
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    /** profile / job-analysis / greetings / assistant */
    val scope: String = "assistant",
    val instructions: String,
    val enabled: Boolean = true,
    /** 出厂内置技能只能停用，不能删除 */
    val builtin: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
