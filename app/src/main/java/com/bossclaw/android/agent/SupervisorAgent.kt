package com.bossclaw.android.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SupervisorAgent(
    private val scope: CoroutineScope,
    private val onRisk: (String) -> Unit
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            AgentBus.riskEvents.collect { reason -> onRisk(reason) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
