package com.bossclaw.android.agent

import com.bossclaw.android.BossClawApp
import com.bossclaw.android.accessibility.AutomationGateway
import com.bossclaw.android.core.JsonCodec
import com.bossclaw.android.core.TimeUtils
import com.bossclaw.android.data.db.JobEntity
import com.bossclaw.android.data.repo.BossClawRepository
import com.bossclaw.android.data.repo.ConfigRepository
import com.bossclaw.android.model.FitLevel
import com.bossclaw.android.model.RunMode
import com.bossclaw.android.model.fit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class BossClawOrchestrator(
    private val repository: BossClawRepository,
    private val configRepository: ConfigRepository,
    private val scout: ScoutAgent,
    private val evaluator: EvaluatorAgent,
    private val communicator: CommunicatorAgent,
    private val automator: AutomationGateway
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loopJob: Job? = null
    private var inboxJob: Job? = null
    private var lastQuotaNotice = 0L
    private var lastConfigNotice = 0L

    val isRunning: Boolean
        get() = loopJob?.isActive == true

    private fun log(source: String, message: String, level: String = "info", jobId: String = "") {
        BossClawApp.graph.log(source, message, level, jobId)
    }

    /** 单日投递硬上限 150。 */
    private fun dailyCap(settings: com.bossclaw.android.data.pref.AppSettings): Int =
        settings.maxDailyGreetings.coerceIn(1, MAX_SAFE_DAILY)

    /** 岗位间人类化抖动（±35%）。 */
    private suspend fun betweenJobsDelay(factor: Float) {
        val base = (BETWEEN_JOBS_MS * factor).toLong()
        val jitter = (base * 0.35).toLong().coerceAtLeast(1L)
        val wait = base + Random.nextLong(-jitter, jitter)
        delay(wait.coerceAtLeast(500L))
    }

    fun start(once: Boolean = false): Boolean {
        if (isRunning) return false
        if (!automator.isConnected) {
            log("调度", "无障碍服务未连接，请先在系统设置中开启 BossClaw", "error")
            AgentBus.setState(EngineState.IDLE)
            return false
        }
        val llm = configRepository.llmConfig()
        if (!llm.isReady) {
            log("调度", "尚未配置模型服务，请到设置中填写 API Key", "warn")
            AgentBus.setState(EngineState.IDLE)
            return false
        }
        configRepository.setPaused(false)
        AgentBus.setState(EngineState.RUNNING)
        log("调度", if (once) "开始试跑一轮" else "BossClaw 开始巡查", "info")
        loopJob = scope.launch { runLoop(once) }
        return true
    }

    fun pause() {
        configRepository.setPaused(true)
        AgentBus.setState(EngineState.PAUSED)
        log("调度", "已暂停，当前任务会在安全步骤停下", "warn")
    }

    fun resume() {
        configRepository.setPaused(false)
        if (!isRunning) {
            start(false)
            return
        }
        AgentBus.setState(EngineState.RUNNING)
        log("调度", "已恢复巡查", "success")
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        inboxJob?.cancel()
        inboxJob = null
        automator.stopCurrentTask()
        AgentBus.setState(EngineState.IDLE)
        AgentBus.setTarget("")
        log("调度", "已停止巡查", "info")
    }

    fun emergencyStop(reason: String) {
        configRepository.setPaused(true)
        loopJob?.cancel()
        loopJob = null
        automator.stopCurrentTask()
        AgentBus.setState(EngineState.RISK_STOP)
        AgentBus.setTarget("风控暂停")
        log("风控", "紧急停止：$reason", "error")
        BossClawApp.graph.notify("BossClaw 已紧急暂停", reason)
    }

    fun runInboxOnce(): Boolean {
        if (!automator.isConnected) {
            log("沟通", "无障碍服务未连接，无法扫描消息", "error")
            return false
        }
        if (!configRepository.llmConfig().isReady) {
            log("沟通", "请先配置模型服务后再处理会话", "warn")
            return false
        }
        if (inboxJob?.isActive == true) return false
        inboxJob = scope.launch {
            AgentBus.setState(EngineState.RUNNING)
            processInbox()
            if (!configRepository.settings.value.paused) {
                AgentBus.setState(EngineState.IDLE)
            }
        }
        return true
    }

    suspend fun generateGreetingFor(jobId: String): String? = withContext(Dispatchers.IO) {
        val job = repository.findJob(jobId) ?: return@withContext null
        val profile = repository.profile()
        val resume = configRepository.resumeText()
        val points = JsonCodec.decode(job.highlightsJson, emptyList<String>())
        val greeting = communicator.prepareGreeting(job, points, profile, resume)
        if (greeting != null) {
            log("沟通", "已为 ${job.company} · ${job.title} 生成开场白", "success", jobId)
        }
        greeting
    }

    suspend fun generateDraftReply(jobId: String): String? = withContext(Dispatchers.IO) {
        val job = repository.findJob(jobId) ?: return@withContext null
        communicator.draftReply(job)
    }

    /** 返回 null 表示发送成功，否则返回失败原因。 */
    suspend fun sendManualReply(jobId: String, text: String): String? = withContext(Dispatchers.IO) {
        val job = repository.findJob(jobId) ?: return@withContext "本地找不到该岗位记录"
        communicator.sendManualReply(job, text)
    }

    private suspend fun runLoop(once: Boolean) = coroutineScope {
        while (isActive) {
            val settings = configRepository.settings.value
            if (settings.paused) {
                AgentBus.setState(EngineState.PAUSED)
                delay(700)
                continue
            }
            if (!automator.isConnected) {
                log("调度", "无障碍连接已断开，巡查结束", "warn")
                AgentBus.setState(EngineState.IDLE)
                break
            }
            val llm = configRepository.llmConfig()
            if (!llm.isReady) {
                if (System.currentTimeMillis() - lastConfigNotice > 30_000) {
                    lastConfigNotice = System.currentTimeMillis()
                    log("调度", "模型配置不完整，等待配置后继续", "warn")
                }
                AgentBus.setState(EngineState.PAUSED)
                delay(1500)
                continue
            }
            val profile = repository.profile()
            val resume = configRepository.resumeText()
            if (profile == null || resume.isBlank()) {
                log("调度", "请先在简历中心完善简历并生成画像", "error")
                AgentBus.setState(EngineState.IDLE)
                break
            }
            val quota = repository.deliveredSince(TimeUtils.todayStart())
            val cap = dailyCap(settings)
            if (quota >= cap) {
                if (System.currentTimeMillis() - lastQuotaNotice > 60_000) {
                    lastQuotaNotice = System.currentTimeMillis()
                    AgentBus.setTarget("今日额度已满（$quota/$cap）")
                    log("调度", "今日打招呼额度已用尽（$quota/$cap），明日自动继续", "warn")
                }
                delay(10_000)
                continue
            }
            AgentBus.setState(EngineState.RUNNING)
            processInbox()
            if (configRepository.settings.value.paused) {
                AgentBus.setState(EngineState.PAUSED)
                delay(700)
                continue
            }
            val feedCount = processFeed(profile, resume, if (once) 2 else 2)
            if (once) {
                AgentBus.setState(EngineState.IDLE)
                AgentBus.setTarget("")
                log("调度", "试跑完成，本轮处理 $feedCount 个岗位", "info")
                break
            }
            delay(2500)
        }
    }

    private suspend fun processInbox() {
        for (i in 0 until 5) {
            if (configRepository.settings.value.paused) return
            val result = communicator.processOneSession()
            if (!result.handled) {
                if (result.note.isNotBlank() && result.note != "无未读") {
                    log("沟通", result.note, "warn")
                }
                return
            }
            if (result.interview) {
                log("沟通", "HR 有面试/需人工确认的意向，已记录并等待你跟进", "warn")
                BossClawApp.graph.notify("HR 邀约需人工跟进", "打开 BossClaw 查看最新会话与意图")
            }
            delay(700)
        }
    }

    private suspend fun processFeed(
        profile: com.bossclaw.android.data.db.ProfileEntity,
        resume: String,
        limit: Int
    ): Int {
        val settings = configRepository.settings.value
        if (!scout.prepareFeed()) {
            log("探路", "当前不在职位列表页，尝试返回", "warn")
            return 0
        }
        val cards = scout.scanCards().take(limit)
        if (cards.isEmpty()) {
            scout.nextPage()
            return 0
        }
        var processed = 0
        for (card in cards) {
            if (configRepository.settings.value.paused) break
            val existing = repository.findSimilar(card.title.trim(), card.company.trim())
            if (existing != null) continue
            AgentBus.setTarget("${card.company} · ${card.title}")
            val raw = scout.inspect(card)
            if (raw == null) {
                log("探路", "详情页采集失败，跳过 ${card.title}", "warn")
                scout.returnToFeed()
                continue
            }
            processed++
            val directions = repository.enabledDirections()
            val outcome = evaluator.evaluate(raw, directions)
            if (outcome.duplicate) {
                scout.returnToFeed()
                continue
            }
            val job = outcome.job
            if (outcome.verdict.rejected) {
                log("筛选", "${job.company} · ${job.title} 已过滤：${outcome.verdict.reason}", "info")
                scout.returnToFeed()
                continue
            }
            if (job.status == JobEntity.STATUS_CLOSED) {
                log("评估", "${job.company} · ${job.title} 判定不匹配，跳过", "info")
                scout.returnToFeed()
                continue
            }
            val fit = job.fit()
            log(
                "评估",
                "${job.company} · ${job.title} → ${fit.label} ${job.score} 分：${job.reason.take(70)}",
                if (fit == FitLevel.STRONG) "success" else "info"
            )
            if (shouldDeliver(fit, settings.runMode)) {
                val points = JsonCodec.decode(job.highlightsJson, emptyList<String>())
                val greeting = communicator.prepareGreeting(job, points, profile, resume)
                if (greeting == null) {
                    log("沟通", "开场白未通过校验，岗位留在待确认队列", "warn")
                    scout.returnToFeed()
                    continue
                }
                val sent = communicator.deliver(job, greeting)
                scout.returnToFeed()
                if (sent) {
                    if (!configRepository.settings.value.firstDeliveryDone) {
                        configRepository.markFirstDelivery()
                        configRepository.setPaused(true)
                        AgentBus.setState(EngineState.PAUSED)
                        AgentBus.setTarget("首单已发送，自动暂停")
                        log("风控", "首次发送完成，已自动暂停，请检查 BOSS 会话", "warn")
                        BossClawApp.graph.notify("首条消息已发送", "BossClaw 已自动暂停，确认会话后可继续")
                        break
                    }
                }
                betweenJobsDelay(settings.speed.factor)
            } else {
                log("调度", "岗位已进入待确认队列（${settings.runMode.label}）", "info", job.jobId)
                scout.returnToFeed()
            }
        }
        if (!configRepository.settings.value.paused) {
            scout.nextPage()
        }
        return processed
    }

    private fun shouldDeliver(fit: FitLevel, mode: RunMode): Boolean = when {
        mode == RunMode.MANUAL -> false
        fit == FitLevel.UNFIT || fit == FitLevel.CAUTIOUS -> false
        mode == RunMode.SEMI -> fit == FitLevel.STRONG
        else -> fit == FitLevel.STRONG || fit == FitLevel.MATCH
    }

    private companion object {
        const val MAX_SAFE_DAILY = 150
        const val BETWEEN_JOBS_MS = 20_000L
    }
}
