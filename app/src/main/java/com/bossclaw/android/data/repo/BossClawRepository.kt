package com.bossclaw.android.data.repo

import com.bossclaw.android.data.db.BossClawDatabase
import com.bossclaw.android.data.db.ChatMessageEntity
import com.bossclaw.android.data.db.CompanyEntity
import com.bossclaw.android.data.db.DirectionEntity
import com.bossclaw.android.data.db.JobEntity
import com.bossclaw.android.data.db.LlmAuditEntity
import com.bossclaw.android.data.db.LogEntity
import com.bossclaw.android.data.db.ProfileEntity
import com.bossclaw.android.core.JsonCodec
import kotlinx.coroutines.flow.Flow

class BossClawRepository(private val db: BossClawDatabase) {

    fun observeJobs(): Flow<List<JobEntity>> = db.jobDao().observeAll()
    fun observeChats(): Flow<List<ChatMessageEntity>> = db.chatDao().observeAll()
    fun observeLogs(): Flow<List<LogEntity>> = db.logDao().observeRecent(300)
    fun observeProfile(): Flow<ProfileEntity?> = db.profileDao().observe()
    fun observeDirections(): Flow<List<DirectionEntity>> = db.directionDao().observeAll()
    fun observeBlacklist(): Flow<List<CompanyEntity>> = db.companyDao().observeBlacklisted()
    fun observeTokensToday(start: Long): Flow<Int> = db.auditDao().observeTokensSince(start)

    suspend fun findJob(jobId: String): JobEntity? = db.jobDao().find(jobId)

    /**
     * 按公司 + 岗位名查重。列表卡片会把岗位名截断成“xxx…”，而库里存的是详情页全名，
     * 精确匹配不到时退回到同公司内做去符号后的前缀匹配，避免同一岗位被反复采集与投递。
     */
    suspend fun findSimilar(title: String, company: String): JobEntity? {
        db.jobDao().findSimilar(title, company)?.let { return it }
        val wantTitle = normalizeName(title)
        val wantCompany = normalizeName(company)
        if (wantTitle.isEmpty() || wantCompany.isEmpty()) return null
        return db.jobDao().byCompany(company).firstOrNull { job ->
            if (normalizeName(job.company) != wantCompany) return@firstOrNull false
            val storedTitle = normalizeName(job.title)
            if (storedTitle.isEmpty()) return@firstOrNull false
            val shorter = minOf(storedTitle.length, wantTitle.length)
            shorter >= 4 && (storedTitle.startsWith(wantTitle) || wantTitle.startsWith(storedTitle))
        }
    }

    private fun normalizeName(raw: String): String =
        raw.replace(Regex("""[\s…\.。,，、:：·\-—_/()（）\[\]【】]+"""), "").lowercase()

    suspend fun allJobs(): List<JobEntity> = db.jobDao().getAll()
    suspend fun jobsByStatus(status: String): List<JobEntity> = db.jobDao().byStatus(status)

    suspend fun saveJob(job: JobEntity) = db.jobDao().upsert(job)

    suspend fun saveEvaluation(
        jobId: String,
        fitLevel: String,
        score: Int,
        highlights: List<String>,
        risks: List<String>,
        reason: String,
        greeting: String,
        status: String
    ) = db.jobDao().updateEvaluation(
        jobId = jobId,
        fitLevel = fitLevel,
        score = score,
        highlights = JsonCodec.encode(highlights),
        risks = JsonCodec.encode(risks),
        reason = reason,
        greeting = greeting,
        status = status
    )

    suspend fun updateStatus(jobId: String, status: String, reason: String = "") =
        if (reason.isBlank()) db.jobDao().updateStatus(jobId, status)
        else db.jobDao().updateStatusWithReason(jobId, status, reason)

    suspend fun saveGreeting(jobId: String, greeting: String, status: String = JobEntity.STATUS_APPROVED) =
        db.jobDao().updateGreeting(jobId, status, greeting)

    suspend fun markDelivered(jobId: String) =
        db.jobDao().markDelivered(jobId, JobEntity.STATUS_DELIVERED)

    suspend fun markReplied(jobId: String, status: String = JobEntity.STATUS_REPLIED) =
        db.jobDao().markReplied(jobId, status)

    suspend fun markInterview(jobId: String) =
        db.jobDao().markInterview(jobId, JobEntity.STATUS_INTERVIEW)

    suspend fun closeChat(jobId: String, status: String, reason: String) =
        db.jobDao().closeChat(jobId, status, reason)

    suspend fun deliveredSince(start: Long): Int = db.jobDao().countDeliveredSince(start)

    suspend fun deliveredBetween(start: Long, end: Long): Int = db.jobDao().countDeliveredBetween(start, end)

    suspend fun countByStatus(status: String): Int = db.jobDao().countByStatus(status)

    suspend fun addMessage(
        jobId: String,
        role: String,
        text: String,
        timestamp: Long = System.currentTimeMillis(),
        delivered: Boolean = true
    ): Long {
        if (db.chatDao().exists(jobId, role, text)) return -1L
        val id = db.chatDao().insert(
            ChatMessageEntity(jobId = jobId, role = role, text = text, timestamp = timestamp, delivered = delivered)
        )
        return id
    }

    suspend fun chatHistory(jobId: String): List<ChatMessageEntity> = db.chatDao().history(jobId)
    suspend fun recentChat(jobId: String, limit: Int = 8): List<ChatMessageEntity> =
        db.chatDao().recent(jobId, limit).reversed()

    suspend fun recordLog(source: String, message: String, level: String = "info", jobId: String = "") {
        db.logDao().insert(LogEntity(source = source, message = message, level = level, jobId = jobId))
    }

    suspend fun recordAudit(audit: LlmAuditEntity) = db.auditDao().insert(audit)
    suspend fun tokensSince(start: Long): Int = db.auditDao().tokensSince(start)
    suspend fun tokensBetween(start: Long, end: Long): Int = db.auditDao().tokensBetween(start, end)

    suspend fun profile(): ProfileEntity? = db.profileDao().get()
    suspend fun saveProfile(profile: ProfileEntity) = db.profileDao().save(profile)

    suspend fun enabledDirections(): List<DirectionEntity> = db.directionDao().enabled()
    suspend fun saveDirection(direction: DirectionEntity): Long = db.directionDao().save(direction)
    suspend fun setDirectionEnabled(id: Long, enabled: Boolean) = db.directionDao().setEnabled(id, enabled)
    suspend fun deleteDirection(id: Long) = db.directionDao().delete(id)

    suspend fun isBlacklisted(company: String): Boolean = db.companyDao().isBlacklisted(company)
    suspend fun blacklistedCompanyNames(): List<String> = db.companyDao().blacklistedNames()
    suspend fun blacklist(company: String, reason: String = "") =
        db.companyDao().save(CompanyEntity(companyName = company, reason = reason))

    suspend fun removeBlacklist(company: String) = db.companyDao().remove(company)

    suspend fun clearBusinessData() {
        db.clearAllTables()
    }
}
