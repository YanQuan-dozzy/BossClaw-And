package com.bossclaw.android.accessibility.task

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.bossclaw.android.BossClawApp
import com.bossclaw.android.agent.AgentBus

interface TaskCallback {
    fun onTaskFinished(taskName: String)
    fun onTaskFailed(taskName: String, reason: String)
}

abstract class BaseAutomationTask {

    protected lateinit var service: AccessibilityService
    protected var callback: TaskCallback? = null
    protected val mainHandler = Handler(Looper.getMainLooper())

    protected var currentStep: Int = 0
    protected var retryCount: Int = 0
    protected open val maxRetryCount: Int = 4
    protected open var baseTaskDurationMs: Long = 2 * 60 * 1000L

    @Volatile
    protected var isTaskRunning: Boolean = false
    private var isPausedLooping: Boolean = false

    private val timeoutRunnable = Runnable {
        if (isTaskRunning) {
            Log.e(getTaskName(), "任务执行超时")
            failedTask("任务执行超时")
        }
    }

    open fun execute(service: AccessibilityService, callback: TaskCallback) {
        this.service = service
        this.callback = callback
        this.currentStep = 1
        this.retryCount = 0
        this.isTaskRunning = true
        startTimeoutMonitor()
        processStep()
    }

    protected fun getSpeedFactor(): Float = BossClawApp.graph.settingsStore.settings.value.speed.factor

    protected fun getAdjustedDelay(originDelay: Long): Long = (originDelay * getSpeedFactor()).toLong()

    private fun processStep() {
        if (!isTaskRunning) return
        if (BossClawApp.graph.settingsStore.settings.value.paused) {
            if (!isPausedLooping) {
                AgentBus.log("自动化", "任务已暂停，等待恢复", "warn")
                stopTimeoutMonitor()
                isPausedLooping = true
            }
            mainHandler.postDelayed({ processStep() }, 1500L)
            return
        }
        if (isPausedLooping) {
            isPausedLooping = false
            startTimeoutMonitor()
        }
        try {
            onStep(currentStep)
        } catch (error: Exception) {
            Log.e(getTaskName(), "步骤异常: ${error.message}", error)
            failedTask("步骤内部异常: ${error.message}")
        }
    }

    protected abstract fun onStep(step: Int)

    protected fun nextStep(delayMillis: Long) {
        retryCount = 0
        mainHandler.postDelayed({
            currentStep++
            processStep()
        }, getAdjustedDelay(delayMillis))
    }

    protected fun sendStep(targetStep: Int, delayMillis: Long) {
        retryCount = 0
        mainHandler.postDelayed({
            currentStep = targetStep
            processStep()
        }, getAdjustedDelay(delayMillis))
    }

    protected fun retryCurrentStep(delayMillis: Long, retryLog: String = "目标未出现") {
        if (!isTaskRunning) return
        if (retryCount >= maxRetryCount) {
            failedTask("第 $currentStep 步重试超限：$retryLog")
            return
        }
        retryCount++
        mainHandler.postDelayed({ processStep() }, getAdjustedDelay(delayMillis))
    }

    protected open fun finishTask() {
        if (!isTaskRunning) return
        stopTimeoutMonitor()
        isTaskRunning = false
        callback?.onTaskFinished(getTaskName())
    }

    protected open fun failedTask(reason: String) {
        stopTimeoutMonitor()
        isTaskRunning = false
        AgentBus.log("自动化", "任务失败：$reason", "error")
        callback?.onTaskFailed(getTaskName(), reason)
    }

    private fun startTimeoutMonitor() {
        mainHandler.removeCallbacks(timeoutRunnable)
        if (baseTaskDurationMs > 0) {
            mainHandler.postDelayed(timeoutRunnable, getAdjustedDelay(baseTaskDurationMs))
        }
    }

    private fun stopTimeoutMonitor() = mainHandler.removeCallbacks(timeoutRunnable)

    open fun onForceStopped() {}

    fun forceStopSilently() {
        if (isTaskRunning) {
            isTaskRunning = false
            stopTimeoutMonitor()
            mainHandler.removeCallbacksAndMessages(null)
            onForceStopped()
        }
    }

    protected fun sendTaskMessage(message: String) {
        AgentBus.log("自动化", message)
    }

    protected fun safeText(node: AccessibilityNodeInfo?): String =
        node?.text?.toString()?.trim() ?: node?.contentDescription?.toString()?.trim() ?: ""

    protected fun extractTextById(parent: AccessibilityNodeInfo, viewId: String): String {
        val nodes = parent.findAccessibilityNodeInfosByViewId(viewId) ?: return ""
        val text = nodes.firstOrNull()?.text?.toString()?.trim() ?: ""
        nodes.forEach { it.recycle() }
        return text
    }

    open fun getTaskName(): String = this::class.java.simpleName
}
