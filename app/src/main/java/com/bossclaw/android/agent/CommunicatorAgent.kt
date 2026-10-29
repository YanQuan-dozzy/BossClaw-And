package com.bossclaw.android.agent

import com.bossclaw.android.BossClawApp
import com.bossclaw.android.accessibility.AutomationGateway
import com.bossclaw.android.accessibility.model.ChatBubble
import com.bossclaw.android.accessibility.model.UnreadChatSession
import com.bossclaw.android.data.db.ChatMessageEntity
import com.bossclaw.android.data.db.JobEntity
import com.bossclaw.android.data.db.ProfileEntity
import com.bossclaw.android.data.repo.BossClawRepository
import com.bossclaw.android.data.repo.ConfigRepository
import com.bossclaw.android.domain.SafetyGuard
import com.bossclaw.android.llm.DialogueDecision
import com.bossclaw.android.llm.LlmGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class SessionResult(
    val handled: Boolean,
    val decision: String = "",
    val interview: Boolean = false,
    val rejected: Boolean = false,
    val note: String = ""
)

class CommunicatorAgent(
    private val repository: BossClawRepository,
    private val configRepository: ConfigRepository,
    private val gateway: LlmGateway,
    private val automator: AutomationGateway
) {

    suspend fun prepareGreeting(
        job: JobEntity,
        points: List<String>,
        profile: ProfileEntity?,
        resume: String
    ): String? = withContext(Dispatchers.IO) {
        val raw = runCatching { gateway.generateGreeting(job, profile, resume, points) }
            .onFailure { AgentBus.log("沟通", "开场白生成失败：${it.message}", "warn") }
            .getOrNull() ?: return@withContext null
        val greeting = SafetyGuard.ensureOpeningAnchor(SafetyGuard.sanitizeGreeting(raw), job.title)
        SafetyGuard.greetingWarning(greeting)?.let { AgentBus.log("沟通", it, "warn") }
        val problem = SafetyGuard.greetingProblem(greeting, resume)
        if (problem != null) {
            AgentBus.log("沟通", "开场白未通过校验：$problem", "warn")
            return@withContext null
        }
        repository.saveGreeting(job.jobId, greeting, JobEntity.STATUS_MATCHED)
        greeting
    }

    suspend fun deliver(job: JobEntity, greeting: String): Boolean = withContext(Dispatchers.Main) {
        val sent = automator.sendGreeting(greeting)
        if (!sent) {
            AgentBus.log("沟通", "打招呼发送失败：${job.company} · ${job.title}", "error")
            repository.recordLog("沟通", "打招呼发送失败：${job.company} · ${job.title}", "error", job.jobId)
            return@withContext false
        }
        repository.markDelivered(job.jobId)
        repository.addMessage(job.jobId, ChatMessageEntity.ROLE_ME, greeting)
        AgentBus.log("沟通", "已向 ${job.company} · ${job.title} 发出开场白", "success")
        repository.recordLog("沟通", "已发送开场白：${job.title} @ ${job.company}", "success", job.jobId)
        true
    }

    suspend fun processOneSession(): SessionResult = withContext(Dispatchers.Main) {
        val scan = automator.scanUnread()
        if (scan.error != null) {
            AgentBus.log("沟通", "扫描未读失败：${scan.error}", "warn")
            return@withContext SessionResult(false, note = scan.error)
        }
        val session = scan.session ?: return@withContext SessionResult(false)

        delay(1200)
        val bubbles = automator.readChatBubbles()
        if (bubbles.isEmpty()) {
            AgentBus.log("沟通", "未读取到会话内容，跳过 ${session.hrName}", "warn")
            automator.back()
            return@withContext SessionResult(true, note = "无法读取会话")
        }

        val latestFromHr = bubbles.lastOrNull { it.role == ChatMessageEntity.ROLE_HR }
        val job = resolveJob(session)
        if (job.isChatClosed) {
            AgentBus.log("沟通", "该会话此前已熔断关闭，跳过回复：${session.hrName}", "info")
            automator.back()
            return@withContext SessionResult(true, decision = "skip", note = "会话已熔断")
        }
        bubbles.filter { it.role == ChatMessageEntity.ROLE_HR }.takeLast(6).forEach { bubble ->
            repository.addMessage(job.jobId, ChatMessageEntity.ROLE_HR, bubble.text)
        }

        if (latestFromHr == null) {
            AgentBus.log("沟通", "${session.hrName} 的未读不含文本消息，转人工", "warn")
            automator.back()
            return@withContext SessionResult(true, decision = "handoff", interview = true, note = "非文本消息")
        }

        val history = repository.recentChat(job.jobId, 8)
        val lastMessage = history.lastOrNull()
        if (lastMessage?.role == ChatMessageEntity.ROLE_ME) {
            AgentBus.log("沟通", "会话已回复，跳过重复处理", "info")
            automator.back()
            return@withContext SessionResult(true, decision = "skip")
        }

        if (!configRepository.settings.value.autoReply) {
            repository.updateStatus(job.jobId, JobEntity.STATUS_REPLIED)
            AgentBus.log("沟通", "${session.hrName} 发来新消息，已记录待人工回复", "warn")
            automator.back()
            return@withContext SessionResult(true, decision = "manual", note = "待人工")
        }

        val riskSignals = SafetyGuard.riskyKeywords(latestFromHr.text)
        val decision: DialogueDecision = if (riskSignals.isNotEmpty()) {
            DialogueDecision(
                decision = "declined",
                reply = LOCAL_REJECT,
                intent = "风险信号：${riskSignals.joinToString("、")}",
                reason = "本地风控命中"
            )
        } else {
            val profile = repository.profile()
            val resume = configRepository.resumeText()
            val generated = runCatching {
                gateway.generateReply(
                    job, profile, resume, history, latestFromHr.text,
                    communicationInfo = configRepository.settings.value.communicationInfo
                )
            }.onFailure {
                AgentBus.log("沟通", "回复生成失败：${it.message}", "warn")
            }.getOrNull()
            if (generated == null) {
                AgentBus.log("沟通", "本轮不发送，等待下次巡查重试 ${session.hrName}", "warn")
                automator.back()
                return@withContext SessionResult(true, decision = "error")
            }
            generated
        }

        if (decision.decision == "hr_rejected") {
            repository.addMessage(
                job.jobId,
                ChatMessageEntity.ROLE_SYSTEM,
                "HR 明确拒绝：${decision.intent.ifBlank { "不合适" }}"
            )
            repository.closeChat(job.jobId, JobEntity.STATUS_REJECTED, decision.intent.ifBlank { "HR 明确拒绝" })
            AgentBus.log("沟通", "${session.hrName} 明确拒绝，已收口会话", "warn")
            automator.back()
            return@withContext SessionResult(true, decision = "hr_rejected", rejected = true, note = decision.intent)
        }

        if (decision.reply.isBlank()) {
            val needHuman = decision.interview || decision.sendResume || decision.decision == "handoff"
            if (needHuman) {
                repository.markInterview(job.jobId)
                AgentBus.log(
                    "沟通",
                    "${session.hrName} 需要人工跟进：${decision.intent.ifBlank { "面试/简历请求" }}",
                    "warn"
                )
                BossClawApp.graph.notify("HR 需要人工跟进", "${session.companyName} · ${session.jobTitle}")
                automator.back()
                return@withContext SessionResult(true, decision = "handoff", interview = true, note = decision.intent)
            }
            AgentBus.log("沟通", "${session.hrName} 的消息无需回复，已跳过", "info")
            automator.back()
            return@withContext SessionResult(true, decision = "skip", note = decision.intent)
        }

        val sanitized = SafetyGuard.sanitizeReply(decision.reply)
        val problem = SafetyGuard.replyProblem(
            sanitized,
            userFacts = configRepository.settings.value.communicationInfo
        )
        val reply = if (problem != null) {
            AgentBus.log("沟通", "回复未通过校验（$problem），改用通用回复", "warn")
            SafetyGuard.localReply(job.title)
        } else {
            sanitized
        }

        val sent = automator.sendReply(session, reply)
        if (!sent) {
            AgentBus.log("沟通", "回复发送失败：${session.hrName}", "error")
            repository.recordLog("沟通", "回复发送失败：${session.companyName}", "error", job.jobId)
            return@withContext SessionResult(true, decision = "error")
        }

        repository.addMessage(job.jobId, ChatMessageEntity.ROLE_ME, reply)
        val interview = decision.interview || decision.decision == "handoff" ||
            decision.intent.contains("面试") || decision.intent.contains("约面")
        val rejected = decision.decision == "declined"
        if (decision.sendResume) {
            repository.recordLog("沟通", "HR 索要简历，需人工发送附件：${session.companyName}", "warn", job.jobId)
            BossClawApp.graph.notify("HR 索要简历", "${session.companyName} · ${session.jobTitle} 需人工发送附件")
        }
        if (rejected) {
            repository.closeChat(job.jobId, JobEntity.STATUS_REJECTED, decision.intent.ifBlank { "AI 判定婉拒" })
        } else {
            repository.markReplied(job.jobId)
        }
        if (interview) {
            repository.markInterview(job.jobId)
        }
        AgentBus.log(
            "沟通",
            "已回复 ${session.hrName}（${decision.intent}）${if (interview) " · 建议人工跟进面试" else ""}",
            if (interview) "warn" else "success"
        )
        repository.recordLog("沟通", "回复 ${session.companyName}：${decision.intent}", if (interview) "warn" else "info", job.jobId)
        SessionResult(
            handled = true,
            decision = decision.decision,
            interview = interview,
            rejected = rejected,
            note = decision.intent
        )
    }

    /** 为会话生成一条可直接发送的回复草稿；不可用时返回 null。 */
    suspend fun draftReply(job: JobEntity): String? = withContext(Dispatchers.IO) {
        val history = repository.recentChat(job.jobId, 10)
        val latestHr = history.lastOrNull { it.role == ChatMessageEntity.ROLE_HR }?.text.orEmpty()
        if (latestHr.isBlank()) return@withContext null
        val facts = configRepository.settings.value.communicationInfo
        val decision = runCatching {
            gateway.generateReply(job, repository.profile(), configRepository.resumeText(), history, latestHr, facts)
        }.onFailure {
            AgentBus.log("沟通", "回复草稿生成失败：${it.message}", "warn")
        }.getOrNull() ?: return@withContext null

        val raw = decision.reply.ifBlank {
            if (decision.decision == "hr_rejected") return@withContext null
            SafetyGuard.localReply(job.title)
        }
        val sanitized = SafetyGuard.sanitizeReply(raw)
        val problem = SafetyGuard.replyProblem(sanitized, userFacts = facts)
        if (problem != null) {
            AgentBus.log("沟通", "回复草稿未通过校验：$problem", "warn")
            return@withContext null
        }
        sanitized
    }

    /**
     * 人工确认后的回复：先定位到该岗位的 BOSS 会话再发送。
     * 返回 null 表示成功，否则返回失败原因。
     */
    suspend fun sendManualReply(job: JobEntity, rawText: String): String? = withContext(Dispatchers.Main) {
        if (configRepository.settings.value.paused) {
            return@withContext "自动化已暂停，任务不会执行；可先继续巡查或在 BOSS 中手动发送"
        }
        val text = SafetyGuard.sanitizeReply(rawText)
        val problem = SafetyGuard.replyProblem(text, checkCommitments = false)
        if (problem != null) return@withContext problem
        val outcome = automator.sendManualReply(job.company, job.title, text)
        if (!outcome.ok) return@withContext outcome.note
        repository.addMessage(job.jobId, ChatMessageEntity.ROLE_ME, text)
        repository.markReplied(job.jobId)
        repository.recordLog("沟通", "已人工回复 ${job.company} · ${job.title}", "success", job.jobId)
        null
    }

    private suspend fun resolveJob(session: UnreadChatSession): JobEntity {
        val jobId = session.resolveJobId()
        repository.findJob(jobId)?.let { return it }
        repository.findSimilar(session.jobTitle, session.companyName)?.let { return it }
        val job = JobEntity(
            jobId = jobId,
            title = session.jobTitle.ifBlank { "未命名岗位" },
            company = session.companyName.ifBlank { "未知公司" },
            salaryText = session.salaryText,
            hrName = session.hrName,
            source = JobEntity.SOURCE_MESSAGE,
            status = JobEntity.STATUS_DELIVERED,
            deliveredAt = System.currentTimeMillis()
        )
        repository.saveJob(job)
        return job
    }

    private companion object {
        const val LOCAL_REJECT = "感谢您的沟通。这个岗位的工作模式与我的职业规划不太匹配，就先不继续占用您的时间了，祝您招聘顺利。"
    }
}
