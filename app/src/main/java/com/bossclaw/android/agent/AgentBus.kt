package com.bossclaw.android.agent

import com.bossclaw.android.accessibility.model.PageScene
import com.bossclaw.android.model.LogLine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class EngineState(val label: String) {
    IDLE("未运行"),
    RUNNING("巡查中"),
    PAUSED("已暂停"),
    RISK_STOP("风控暂停")
}

/** 单个岗位的评估结论，用于悬浮球旁的即时分析展示。 */
data class JobAnalysis(
    val company: String,
    val title: String,
    val fitLabel: String,
    val score: Int,
    val reason: String,
    val highlights: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val at: Long = System.nanoTime(),
    /** 岗位主键，用于把后生成的开场白挂回同一条评估 */
    val jobId: String = "",
    /** 人工确认模式下生成的开场白，供悬浮球直接复制 */
    val greeting: String = "",
    /** 是否仍在读取详情与评分：先占位展示当前岗位，评估完成后补齐结论 */
    val evaluating: Boolean = false
)

object AgentBus {
    private val _state = MutableStateFlow(EngineState.IDLE)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<LogLine>>(emptyList())
    val logs: StateFlow<List<LogLine>> = _logs.asStateFlow()

    private val _currentTarget = MutableStateFlow("")
    val currentTarget: StateFlow<String> = _currentTarget.asStateFlow()

    private val _scene = MutableStateFlow(PageScene.UNKNOWN)
    val scene: StateFlow<PageScene> = _scene.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _analysis = MutableStateFlow<JobAnalysis?>(null)
    val analysis: StateFlow<JobAnalysis?> = _analysis.asStateFlow()

    val riskEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun log(source: String, message: String, level: String = LogLine.LEVEL_INFO) {
        _logs.update { current ->
            (current + LogLine(source = source, message = message, level = level)).takeLast(200)
        }
    }

    fun setState(state: EngineState) {
        _state.value = state
    }

    fun setTarget(target: String) {
        _currentTarget.value = target
    }

    fun setScene(scene: PageScene) {
        if (_scene.value != scene) _scene.value = scene
    }

    fun setConnected(connected: Boolean) {
        _connected.value = connected
    }

    fun publishAnalysis(analysis: JobAnalysis) {
        _analysis.value = analysis
    }

    /** 开始评估某个岗位时先占位展示，避免详情读取与模型评分期间卡片还停在上一个岗位。 */
    fun publishEvaluating(company: String, title: String) {
        _analysis.value = JobAnalysis(
            company = company,
            title = title,
            fitLabel = "评估中",
            score = 0,
            reason = "正在读取岗位详情并评估…",
            evaluating = true
        )
    }

    /** 岗位被过滤或跳过时同样更新卡片，避免一直停留在「评估中」。 */
    fun publishSkipped(company: String, title: String, reason: String) {
        _analysis.value = JobAnalysis(
            company = company,
            title = title,
            fitLabel = "已跳过",
            score = 0,
            reason = reason
        )
    }

    /** 把生成好的开场白挂到当前评估上，悬浮球即可直接复制；岗位不匹配时忽略。 */
    fun attachGreeting(jobId: String, greeting: String) {
        val current = _analysis.value ?: return
        if (current.jobId.isNotEmpty() && current.jobId != jobId) return
        // 开场白生成耗时不定，挂载时重新计时，保证完整评估卡不会被 freshness 提前收起
        _analysis.value = current.copy(jobId = jobId, greeting = greeting, at = System.nanoTime())
    }

    fun raiseRisk(reason: String) {
        setState(EngineState.RISK_STOP)
        riskEvents.tryEmit(reason)
    }
}
