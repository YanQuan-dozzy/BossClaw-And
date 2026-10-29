package com.bossclaw.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.bossclaw.android.agent.AgentBus
import com.bossclaw.android.accessibility.model.PageScene
import com.bossclaw.android.accessibility.task.BaseAutomationTask
import com.bossclaw.android.accessibility.task.TaskCallback

class BossClawAccessibilityService : AccessibilityService(), TaskCallback {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentTask: BaseAutomationTask? = null
    private var lastScene: PageScene = PageScene.UNKNOWN
    private var lastSceneCheck = 0L

    // 任务连续失败达阈值即判定页面卡死/被阻断，触发全局熔断
    private var consecutiveFailures = 0
    private val maxAllowedFailures = 4

    companion object {
        @Volatile
        var instance: BossClawAccessibilityService? = null
            private set
        const val TARGET_PACKAGE = "com.hpbr.bosszhipin"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AgentBus.setConnected(true)
        AgentBus.log("无障碍", "通道已建立，可以开始巡查", "success")
    }

    override fun onDestroy() {
        super.onDestroy()
        currentTask?.forceStopSilently()
        currentTask = null
        instance = null
        AgentBus.setConnected(false)
        AgentBus.log("无障碍", "服务已断开", "warn")
    }

    override fun onInterrupt() {
        currentTask?.forceStopSilently()
        currentTask = null
        AgentBus.setConnected(false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != TARGET_PACKAGE) return
        val now = System.currentTimeMillis()
        if (now - lastSceneCheck < 350L) return
        lastSceneCheck = now
        val root = rootInActiveWindow ?: return
        val scene = SceneDetector.detect(root)
        root.recycle()
        if (scene == PageScene.UNKNOWN) return
        if (scene != lastScene) {
            lastScene = scene
            AgentBus.setScene(scene)
            if (scene.isRisk) {
                AgentBus.raiseRisk("检测到滑动验证码或安全验证，请在 App 内人工处理")
            }
        }
    }

    fun executeTask(task: BaseAutomationTask) {
        mainHandler.post {
            currentTask?.forceStopSilently()
            currentTask = task
            Log.i("BossClawAccess", "开始任务: ${task.getTaskName()}")
            task.execute(this, this)
        }
    }

    fun stopCurrentTask() {
        currentTask?.forceStopSilently()
        currentTask = null
    }

    override fun onTaskFinished(taskName: String) {
        currentTask = null
        consecutiveFailures = 0
        Log.i("BossClawAccess", "任务完成: $taskName")
    }

    override fun onTaskFailed(taskName: String, reason: String) {
        currentTask = null
        consecutiveFailures++
        Log.w("BossClawAccess", "任务失败（连续 $consecutiveFailures 次）: $taskName, $reason")
        if (consecutiveFailures >= maxAllowedFailures) {
            consecutiveFailures = 0
            AgentBus.raiseRisk("任务连续失败 $maxAllowedFailures 次，疑似界面卡死或未知阻断：$reason")
        }
    }
}
