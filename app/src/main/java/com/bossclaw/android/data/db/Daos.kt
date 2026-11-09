package com.bossclaw.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: JobEntity)

    @Query("SELECT * FROM jobs WHERE jobId = :jobId LIMIT 1")
    suspend fun find(jobId: String): JobEntity?

    @Query("SELECT * FROM jobs WHERE title = :title AND company = :company LIMIT 1")
    suspend fun findSimilar(title: String, company: String): JobEntity?

    @Query("SELECT * FROM jobs WHERE company = :company")
    suspend fun byCompany(company: String): List<JobEntity>

    @Query("SELECT * FROM jobs ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs ORDER BY updatedAt DESC")
    suspend fun getAll(): List<JobEntity>

    @Query("SELECT * FROM jobs WHERE status = :status ORDER BY score DESC, createdAt ASC")
    suspend fun byStatus(status: String): List<JobEntity>

    @Query("SELECT * FROM jobs WHERE status IN (:statuses) ORDER BY updatedAt DESC")
    fun observeByStatuses(statuses: List<String>): Flow<List<JobEntity>>

    @Query("UPDATE jobs SET status = :status, updatedAt = :now WHERE jobId = :jobId")
    suspend fun updateStatus(jobId: String, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE jobs SET status = :status, reason = :reason, updatedAt = :now WHERE jobId = :jobId")
    suspend fun updateStatusWithReason(jobId: String, status: String, reason: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE jobs SET fitLevel = :fitLevel, score = :score, highlightsJson = :highlights, risksJson = :risks, reason = :reason, greeting = :greeting, status = :status, updatedAt = :now WHERE jobId = :jobId")
    suspend fun updateEvaluation(
        jobId: String,
        fitLevel: String,
        score: Int,
        highlights: String,
        risks: String,
        reason: String,
        greeting: String,
        status: String,
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE jobs SET status = :status, greeting = :greeting, updatedAt = :now WHERE jobId = :jobId")
    suspend fun updateGreeting(jobId: String, status: String, greeting: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE jobs SET status = :status, deliveredAt = :now, updatedAt = :now WHERE jobId = :jobId")
    suspend fun markDelivered(jobId: String, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE jobs SET status = :status, replyCount = replyCount + 1, lastChatAt = :now, updatedAt = :now WHERE jobId = :jobId")
    suspend fun markReplied(jobId: String, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE jobs SET status = :status, interviewFlag = 1, lastChatAt = :now, updatedAt = :now WHERE jobId = :jobId")
    suspend fun markInterview(jobId: String, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE jobs SET isChatClosed = 1, closedReason = :reason, status = :status, updatedAt = :now WHERE jobId = :jobId")
    suspend fun closeChat(jobId: String, status: String, reason: String, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM jobs WHERE deliveredAt IS NOT NULL AND deliveredAt >= :start")
    suspend fun countDeliveredSince(start: Long): Int

    @Query("SELECT COUNT(*) FROM jobs WHERE deliveredAt IS NOT NULL AND deliveredAt >= :start AND deliveredAt < :end")
    suspend fun countDeliveredBetween(start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM jobs WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("DELETE FROM jobs")
    suspend fun clear()
}

@Dao
interface ChatDao {
    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE jobId = :jobId ORDER BY timestamp ASC")
    fun observeByJob(jobId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE jobId = :jobId ORDER BY timestamp ASC")
    suspend fun history(jobId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE jobId = :jobId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(jobId: String, limit: Int): List<ChatMessageEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM chat_messages WHERE jobId = :jobId AND role = :role AND text = :text)")
    suspend fun exists(jobId: String, role: String, text: String): Boolean

    @Query("DELETE FROM chat_messages WHERE jobId = :jobId")
    suspend fun clearByJob(jobId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}

@Dao
interface LogDao {
    @Insert
    suspend fun insert(log: LogEntity): Long

    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<LogEntity>>

    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<LogEntity>

    @Query("DELETE FROM logs")
    suspend fun clear()
}

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(audit: LlmAuditEntity): Long

    @Query("SELECT COALESCE(SUM(totalTokens), 0) FROM llm_audits WHERE createdAt >= :start")
    fun observeTokensSince(start: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(totalTokens), 0) FROM llm_audits WHERE createdAt >= :start")
    suspend fun tokensSince(start: Long): Int

    @Query("SELECT COALESCE(SUM(totalTokens), 0) FROM llm_audits WHERE createdAt >= :start AND createdAt < :end")
    suspend fun tokensBetween(start: Long, end: Long): Int
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1 LIMIT 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1 LIMIT 1")
    suspend fun get(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: ProfileEntity)

    @Query("DELETE FROM profile")
    suspend fun clear()
}

@Dao
interface DirectionDao {
    @Query("SELECT * FROM directions ORDER BY priority ASC, id ASC")
    fun observeAll(): Flow<List<DirectionEntity>>

    @Query("SELECT * FROM directions WHERE enabled = 1 ORDER BY priority ASC, id ASC")
    suspend fun enabled(): List<DirectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(direction: DirectionEntity): Long

    @Query("UPDATE directions SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM directions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM directions")
    suspend fun clear()
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY builtin DESC, scope ASC, name ASC")
    fun observeAll(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills WHERE id = :id")
    suspend fun find(id: String): SkillEntity?

    @Query("SELECT * FROM skills WHERE enabled = 1 AND scope = :scope ORDER BY builtin DESC, name ASC")
    suspend fun enabled(scope: String): List<SkillEntity>

    @Query("SELECT * FROM skills WHERE scope IN (:scopes) AND enabled = 1")
    suspend fun enabledForScopes(scopes: List<String>): List<SkillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(skill: SkillEntity)

    @Query("UPDATE skills SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM skills WHERE id = :id AND builtin = 0")
    suspend fun deleteCustom(id: String)

    @Query("DELETE FROM skills")
    suspend fun clear()
}

@Dao
interface CompanyDao {
    @Query("SELECT EXISTS(SELECT 1 FROM companies WHERE companyName = :name AND blacklisted = 1)")
    suspend fun isBlacklisted(name: String): Boolean

    @Query("SELECT companyName FROM companies WHERE blacklisted = 1")
    suspend fun blacklistedNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(company: CompanyEntity)

    @Query("SELECT * FROM companies WHERE blacklisted = 1 ORDER BY updatedAt DESC")
    fun observeBlacklisted(): Flow<List<CompanyEntity>>

    @Query("DELETE FROM companies WHERE companyName = :name")
    suspend fun remove(name: String)

    @Query("DELETE FROM companies")
    suspend fun clear()
}
