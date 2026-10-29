package com.bossclaw.android.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bossclaw.android.BossClawApp
import com.bossclaw.android.accessibility.AutomationGateway
import com.bossclaw.android.agent.AgentBus
import com.bossclaw.android.data.db.JobEntity
import com.bossclaw.android.data.db.ProfileEntity
import com.bossclaw.android.data.db.SkillEntity
import com.bossclaw.android.data.pref.AppSettings
import com.bossclaw.android.domain.DocumentParser
import com.bossclaw.android.domain.ResumeTools
import com.bossclaw.android.floating.FloatingHudService
import com.bossclaw.android.llm.LlmConfig
import com.bossclaw.android.model.DailyStats
import com.bossclaw.android.model.FitLevel
import com.bossclaw.android.core.JsonCodec
import com.bossclaw.android.core.TimeUtils
import com.bossclaw.android.util.PermissionTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 沟通页输入框里的回复草稿状态：生成中、生成结果或失败原因。 */
data class ReplyDraftState(
    val jobId: String = "",
    val text: String = "",
    val loading: Boolean = false,
    val error: String? = null
)

data class PermissionSnapshot(
    val accessibility: Boolean = false,
    val overlay: Boolean = false,
    val notifications: Boolean = false,
    val batteryOptimized: Boolean = false
) {
    val allReady: Boolean get() = accessibility && overlay
}

open class BossClawViewModel(application: Application) : AndroidViewModel(application) {

    protected val graph = BossClawApp.graph
    protected val repository = graph.repository
    protected val configRepository = graph.configRepository
    protected val context = application.applicationContext

    val jobs: StateFlow<List<JobEntity>> = repository.observeJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val chatMessages = repository.observeChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logs = repository.observeLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val profile: StateFlow<ProfileEntity?> = repository.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val directions = repository.observeDirections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val blacklist = repository.observeBlacklist()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val skills: StateFlow<List<SkillEntity>> = graph.skillRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = configRepository.settings

    val engineState: StateFlow<com.bossclaw.android.agent.EngineState> = AgentBus.state
    val currentTarget: StateFlow<String> = AgentBus.currentTarget
    val scene = AgentBus.scene
    val connected = AgentBus.connected
    val console = AgentBus.logs

    private val tokensToday = repository.observeTokensToday(TimeUtils.todayStart())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val stats: StateFlow<DailyStats> = combine(jobs, tokensToday) { allJobs, tokens ->
        computeToday(allJobs, tokens)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyStats())

    val funnel: StateFlow<DailyStats> = combine(jobs, tokensToday) { allJobs, tokens ->
        DailyStats(
            discovered = allJobs.size,
            filtered = allJobs.count { it.status == JobEntity.STATUS_FILTERED },
            matched = allJobs.count { it.status == JobEntity.STATUS_MATCHED },
            delivered = allJobs.count { it.deliveredAt != null },
            replied = allJobs.count { it.replyCount > 0 || it.status == JobEntity.STATUS_REPLIED || it.status == JobEntity.STATUS_INTERVIEW },
            interview = allJobs.count { it.interviewFlag },
            tokensToday = tokens
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyStats())

    val resumeText = MutableStateFlow(configRepository.resumeText())
    val resumeFileName = MutableStateFlow(configRepository.resumeFileName())
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val testing = MutableStateFlow(false)
    val permissions = MutableStateFlow(PermissionSnapshot())
    val busyJobId = MutableStateFlow<String?>(null)
    val replyDraft = MutableStateFlow<ReplyDraftState?>(null)
    val sendingReply = MutableStateFlow(false)

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        permissions.value = PermissionSnapshot(
            accessibility = PermissionTools.isAccessibilityEnabled(context),
            overlay = PermissionTools.isOverlayGranted(context),
            notifications = PermissionTools.notificationsGranted(context),
            batteryOptimized = PermissionTools.isBatteryOptimized(context)
        )
    }

    fun consumeMessage() {
        message.value = null
    }

    fun notify(text: String) {
        message.value = text
    }

    fun startEngine() {
        if (!graph.orchestrator.start(false)) {
            message.value = "启动条件未满足，请检查无障碍、模型与简历配置"
        }
    }

    fun startOnce() {
        if (!graph.orchestrator.start(true)) {
            message.value = "试跑条件未满足，请检查配置"
        }
    }

    fun pauseEngine() = graph.orchestrator.pause()

    fun resumeEngine() = graph.orchestrator.resume()

    fun stopEngine() = graph.orchestrator.stop()

    fun emergencyStop() = graph.orchestrator.emergencyStop("用户手动急停")

    fun runInboxOnce() {
        if (!graph.orchestrator.runInboxOnce()) {
            message.value = "无法扫描消息，请检查无障碍与模型配置"
        }
    }

    fun generateGreeting(jobId: String) {
        if (busyJobId.value != null) return
        busyJobId.value = jobId
        viewModelScope.launch {
            val greeting = graph.orchestrator.generateGreetingFor(jobId)
            busyJobId.value = null
            message.value = greeting?.let { "开场白已生成，可复制到 BOSS" } ?: "开场白生成失败，请检查模型配置"
        }
    }

    /** 让 AI 结合聊天记录与「沟通信息」生成一条回复草稿，填入沟通页输入框。 */
    fun generateReplyDraft(jobId: String) {
        if (replyDraft.value?.loading == true) return
        replyDraft.value = ReplyDraftState(jobId = jobId, loading = true)
        viewModelScope.launch {
            val draft = graph.orchestrator.generateDraftReply(jobId)
            replyDraft.value = if (draft.isNullOrBlank()) {
                ReplyDraftState(jobId = jobId, error = "暂未生成可用回复，请检查模型配置或手动输入")
            } else {
                ReplyDraftState(jobId = jobId, text = draft)
            }
        }
    }

    fun clearReplyDraft() {
        replyDraft.value = null
    }

    /** 人工确认后发送：先定位到该岗位的 BOSS 会话，再填入并发送。 */
    fun sendChatReply(jobId: String, text: String, onSuccess: () -> Unit = {}) {
        if (sendingReply.value) return
        if (text.isBlank()) {
            message.value = "回复内容不能为空"
            return
        }
        sendingReply.value = true
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) { graph.orchestrator.sendManualReply(jobId, text) }
            sendingReply.value = false
            message.value = error ?: "回复已发送"
            if (error == null) {
                replyDraft.value = null
                onSuccess()
            }
        }
    }

    fun ignoreJob(job: JobEntity) {
        viewModelScope.launch {
            repository.updateStatus(job.jobId, JobEntity.STATUS_CLOSED, "用户忽略")
            message.value = "已忽略 ${job.company} · ${job.title}"
        }
    }

    fun blacklistJob(job: JobEntity) {
        viewModelScope.launch {
            repository.blacklist(job.company, "用户从岗位页拉黑")
            if (job.status != JobEntity.STATUS_DELIVERED) {
                repository.updateStatus(job.jobId, JobEntity.STATUS_CLOSED, "公司已拉黑")
            }
            message.value = "已拉黑 ${job.company}"
        }
    }

    fun importResume(uri: Uri) {
        viewModelScope.launch {
            busy.value = true
            val result = runCatching {
                val name = queryFileName(uri)
                val text = withContext(Dispatchers.IO) {
                    DocumentParser.extract(context, uri, name, context.contentResolver.getType(uri))
                }
                val normalized = ResumeTools.normalize(text)
                if (normalized.isBlank()) error("文件内容为空")
                configRepository.saveResume(normalized, name)
                normalized to name
            }
            busy.value = false
            result.onSuccess { (text, name) ->
                resumeText.value = text
                resumeFileName.value = name
                message.value = if (ResumeTools.looksLikeResume(text)) {
                    "已导入 $name，建议生成画像"
                } else {
                    "已导入 $name，但内容不像完整简历，请检查"
                }
            }.onFailure { message.value = "导入失败：${it.message}" }
        }
    }

    fun saveResume(text: String, fileName: String = resumeFileName.value) {
        val normalized = ResumeTools.normalize(text)
        if (normalized.isBlank()) {
            message.value = "简历内容不能为空"
            return
        }
        configRepository.saveResume(normalized, fileName.ifBlank { "手动编辑" })
        resumeText.value = normalized
        resumeFileName.value = fileName.ifBlank { "手动编辑" }
        message.value = "简历已保存（${normalized.length} 字）"
    }

    fun generateProfile() {
        val resume = resumeText.value
        if (resume.isBlank()) {
            message.value = "请先导入或粘贴简历"
            return
        }
        busy.value = true
        viewModelScope.launch {
            val aiResult = runCatching { graph.llmGateway.generateProfile(resume) }
            val result = aiResult.getOrElse {
                ResumeTools.fallbackProfile(resume).also { profile ->
                    repository.saveProfile(ResumeTools.toEntity(profile))
                    message.value = "AI 画像失败（${it.message}），已用本地解析生成，请手动校正"
                }
            }
            if (aiResult.isSuccess) {
                repository.saveProfile(ResumeTools.toEntity(result))
                message.value = "候选人画像已生成"
            }
            busy.value = false
        }
    }

    fun saveProfileFields(
        name: String,
        years: Int,
        education: String,
        skills: List<String>,
        strengths: List<String>,
        summary: String,
        roles: List<String>,
        cities: List<String>,
        salaryK: Int
    ) {
        viewModelScope.launch {
            repository.saveProfile(
                ProfileEntity(
                    name = name.trim(),
                    years = years,
                    education = education.trim(),
                    skillsJson = JsonCodec.encode(skills.filter { it.isNotBlank() }),
                    strengthsJson = JsonCodec.encode(strengths.filter { it.isNotBlank() }),
                    summary = summary.trim(),
                    targetRolesJson = JsonCodec.encode(roles.filter { it.isNotBlank() }),
                    targetCitiesJson = JsonCodec.encode(cities.filter { it.isNotBlank() }),
                    minSalaryK = salaryK,
                    updatedAt = System.currentTimeMillis()
                )
            )
            message.value = "画像已更新"
        }
    }

    fun addDirection(name: String, query: String, keywords: String) {
        if (name.isBlank()) {
            message.value = "方向名称不能为空"
            return
        }
        val keywordList = keywords.split(',', '，', '、', ' ', '/').map { it.trim() }.filter { it.isNotBlank() }
        viewModelScope.launch {
            repository.saveDirection(
                com.bossclaw.android.data.db.DirectionEntity(
                    name = name.trim(),
                    query = query.ifBlank { name.trim() },
                    keywordsJson = JsonCodec.encode(keywordList),
                    priority = directions.value.size + 1
                )
            )
            message.value = "已添加求职方向「${name.trim()}」"
        }
    }

    fun toggleDirection(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setDirectionEnabled(id, enabled) }
    }

    fun deleteDirection(id: Long) {
        viewModelScope.launch { repository.deleteDirection(id) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        configRepository.updateSettings(transform)
    }

    fun saveLlmConfig(config: LlmConfig) {
        configRepository.saveLlmConfig(config)
        message.value = "模型配置已保存"
    }

    fun llmConfig(): LlmConfig = configRepository.llmConfig()

    fun testConnection(config: LlmConfig) {
        testing.value = true
        viewModelScope.launch {
            val result = runCatching { graph.llmGateway.testConnection(config) }
            testing.value = false
            message.value = result.fold(
                { "连接成功" },
                { "连接失败：${it.message}" }
            )
        }
    }

    fun setHudEnabled(enabled: Boolean) {
        if (enabled && !PermissionTools.isOverlayGranted(context)) {
            message.value = "请先授予悬浮窗权限"
            context.startActivity(PermissionTools.overlayIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        configRepository.updateSettings { it.copy(floatingHud = enabled) }
        val intent = Intent(context, FloatingHudService::class.java)
        if (enabled) {
            ContextCompat.startForegroundService(context, intent)
            message.value = "悬浮控制台已开启"
        } else {
            context.stopService(intent)
            message.value = "悬浮控制台已关闭"
        }
    }

    fun openBoss() {
        if (!PermissionTools.launchBoss(context)) {
            message.value = "未安装 BOSS 直聘"
        }
    }

    fun toggleSkill(id: String, enabled: Boolean) {
        viewModelScope.launch { graph.skillRepository.toggle(id, enabled) }
    }

    fun deleteSkill(skill: SkillEntity) {
        if (skill.builtin) {
            message.value = "内置技能只能停用，不能删除"
            return
        }
        viewModelScope.launch {
            graph.skillRepository.deleteCustom(skill.id)
            message.value = "已删除技能「${skill.name}」"
        }
    }

    fun importSkill(uri: Uri) {
        viewModelScope.launch {
            busy.value = true
            val result = runCatching {
                graph.skillRepository.importSkill(uri, queryFileName(uri))
            }
            busy.value = false
            result.onSuccess { skill ->
                message.value = "已导入技能「${skill.name}」（作用域：${com.bossclaw.android.llm.SkillScope.label(skill.scope)}）"
            }.onFailure { message.value = "导入技能失败：${it.message}" }
        }
    }

    fun clearAllData() {
        graph.orchestrator.stop()
        viewModelScope.launch {
            repository.clearBusinessData()
            configRepository.resetAll()
            graph.skillRepository.ensureSeeded()
            resumeText.value = ""
            resumeFileName.value = ""
            refreshPermissions()
            message.value = "本地数据与配置已清除"
        }
    }

    private suspend fun queryFileName(uri: Uri): String = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull() ?: "导入简历"
    }

    private fun computeToday(allJobs: List<JobEntity>, tokens: Int): DailyStats {
        val start = TimeUtils.todayStart()
        return DailyStats(
            discovered = allJobs.count { it.createdAt >= start },
            filtered = allJobs.count { it.status == JobEntity.STATUS_FILTERED && it.updatedAt >= start },
            matched = allJobs.count { it.status == JobEntity.STATUS_MATCHED },
            delivered = allJobs.count { (it.deliveredAt ?: 0L) >= start },
            replied = allJobs.count {
                (it.lastChatAt ?: 0L) >= start &&
                    (it.replyCount > 0 || it.status == JobEntity.STATUS_REPLIED || it.status == JobEntity.STATUS_INTERVIEW)
            },
            interview = allJobs.count { it.interviewFlag && it.updatedAt >= start },
            tokensToday = tokens
        )
    }

    companion object {
        val FIT_COLORS: Map<FitLevel, Long> = emptyMap()
        const val BOSS_PACKAGE = AutomationGateway.BOSS_PACKAGE
    }
}
