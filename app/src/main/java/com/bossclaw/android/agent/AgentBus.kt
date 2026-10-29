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

    fun raiseRisk(reason: String) {
        setState(EngineState.RISK_STOP)
        riskEvents.tryEmit(reason)
    }
}
