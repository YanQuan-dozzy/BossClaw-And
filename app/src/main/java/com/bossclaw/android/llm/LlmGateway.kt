package com.bossclaw.android.llm

import com.bossclaw.android.data.db.ChatMessageEntity
import com.bossclaw.android.data.db.JobEntity
import com.bossclaw.android.data.db.LlmAuditEntity
import com.bossclaw.android.data.db.ProfileEntity
import com.bossclaw.android.data.repo.BossClawRepository
import com.bossclaw.android.data.repo.ConfigRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LlmGateway(
    private val transport: OpenAiCompatibleTransport,
    private val repository: BossClawRepository,
    private val configRepository: ConfigRepository,
    private val skillRepository: com.bossclaw.android.data.repo.SkillRepository
) {
    private val evaluationCache = ConcurrentHashMap<String, JobEvaluation>()

    suspend fun evaluateJob(job: JobEntity, profile: ProfileEntity?, resume: String): JobEvaluation =
        withContext(Dispatchers.IO) {
            val cacheKey = "${job.jobId}:${job.jd.hashCode()}"
            evaluationCache[cacheKey]?.let { return@withContext it }
            val skillText = skillRepository.enabledInstructions(SkillScope.JOB_ANALYSIS)
            val result = guardBudget { config ->
                val call = transport.chat(
                    config,
                    PromptFactory.evaluationSystemPrompt(skillText),
                    PromptFactory.evaluationUserPrompt(profile, resume, job, ContextBudget.of(config)),
                    temperature = 0.2f
                )
                ResponseParser.parseEvaluation(call.content).also {
                    audit("evaluate", job.jobId, config, call, true)
                }
            }
            evaluationCache[cacheKey] = result
            result
        }

    suspend fun generateGreeting(
        job: JobEntity,
        profile: ProfileEntity?,
        resume: String,
        points: List<String>
    ): String = withContext(Dispatchers.IO) {
        val skillText = skillRepository.enabledInstructions(SkillScope.GREETINGS)
        guardBudget { config ->
            val call = transport.chat(
                config,
                PromptFactory.greetingSystemPrompt(skillText),
                PromptFactory.greetingUserPrompt(profile, resume, job, points, ContextBudget.of(config)),
                temperature = 0.4f,
                maxTokens = 800
            )
            audit("greeting", job.jobId, config, call, true)
            ResponseParser.parseGreeting(call.content).greeting
        }
    }

    suspend fun generateReply(
        job: JobEntity,
        profile: ProfileEntity?,
        resume: String,
        history: List<ChatMessageEntity>,
        latestHrMessage: String,
        communicationInfo: String = ""
    ): DialogueDecision = withContext(Dispatchers.IO) {
        guardBudget { config ->
            val call = transport.chat(
                config,
                PromptFactory.dialogueSystemPrompt(),
                PromptFactory.dialogueUserPrompt(
                    profile, resume, job, history, latestHrMessage, communicationInfo,
                    ContextBudget.of(config)
                ),
                temperature = 0.35f,
                maxTokens = 800
            )
            audit("dialogue", job.jobId, config, call, true)
            ResponseParser.parseDialogue(call.content)
        }
    }

    suspend fun generateProfile(resume: String): ProfileResult = withContext(Dispatchers.IO) {
        val skillText = skillRepository.enabledInstructions(SkillScope.PROFILE)
        guardBudget { config ->
            val call = transport.chat(
                config,
                PromptFactory.profileSystemPrompt(skillText),
                PromptFactory.profileUserPrompt(resume, ContextBudget.of(config)),
                temperature = 0.2f
            )
            audit("profile", "", config, call, true)
            ResponseParser.parseProfile(call.content)
        }
    }

    suspend fun testConnection(config: LlmConfig): String = withContext(Dispatchers.IO) {
        val call = transport.chat(
            config,
            PromptFactory.testSystemPrompt(),
            PromptFactory.testUserPrompt(),
            temperature = 0f,
            maxTokens = 200
        )
        call.content
    }

    private suspend fun <T> guardBudget(block: suspend (LlmConfig) -> T): T {
        val config = configRepository.llmConfig()
        if (!config.isReady) throw LlmException("请先在设置中配置模型服务与 API Key")
        val started = System.currentTimeMillis()
        return try {
            block(config)
        } catch (error: Exception) {
            if (error is LlmException) {
                repository.recordAudit(
                    LlmAuditEntity(
                        scene = "error",
                        jobId = "",
                        model = config.model,
                        prompt = "",
                        response = "",
                        promptTokens = 0,
                        completionTokens = 0,
                        totalTokens = 0,
                        durationMs = System.currentTimeMillis() - started,
                        success = false,
                        error = error.message.orEmpty().take(300)
                    )
                )
            }
            throw error
        }
    }

    private suspend fun audit(scene: String, jobId: String, config: LlmConfig, call: LlmCallResult, success: Boolean) {
        repository.recordAudit(
            LlmAuditEntity(
                scene = scene,
                jobId = jobId,
                model = config.model,
                prompt = "",
                response = call.content.take(600),
                promptTokens = call.promptTokens,
                completionTokens = call.completionTokens,
                totalTokens = call.totalTokens,
                durationMs = call.durationMs,
                success = success
            )
        )
    }
}
