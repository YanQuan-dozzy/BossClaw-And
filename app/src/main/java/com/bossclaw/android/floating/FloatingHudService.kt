package com.bossclaw.android.floating

import android.animation.ValueAnimator
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.bossclaw.android.BossClawApp
import com.bossclaw.android.R
import com.bossclaw.android.agent.AgentBus
import com.bossclaw.android.agent.EngineState
import com.bossclaw.android.accessibility.util.GestureEngine
import com.bossclaw.android.core.TimeUtils
import com.bossclaw.android.di.AppGraph
import com.bossclaw.android.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * 悬浮按钮球：
 * 拖动可换位置，松手吸附到最近的屏幕边缘；点击展开运行控制台，
 * 在不离开 BOSS 的情况下开始巡查、暂停、处理消息或急停。
 */
class FloatingHudService : Service() {

    private lateinit var windowManager: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var ballView: FrameLayout? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var panelView: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelAttached = false

    private var panelStatus: TextView? = null
    private var panelTarget: TextView? = null
    private var panelLogs: TextView? = null
    private var pauseButton: TextView? = null

    private var state: EngineState = EngineState.IDLE

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundCompat()
        installBall()
        observeBus()
        GestureEngine.windowTouchListener = { touchable -> setOverlayTouchable(touchable) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        GestureEngine.windowTouchListener = null
        panelView?.let { runCatching { windowManager.removeView(it) } }
        ballView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
        ballView = null
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notification: Notification = NotificationCompat.Builder(this, AppGraph.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bossclaw)
            .setContentTitle("BossClaw 悬浮球")
            .setContentText("点击悬浮球展开运行控制台")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun installBall() {
        val size = dp(BALL_SIZE_DP)
        val ball = FrameLayout(this).apply {
            contentDescription = "BossClaw 悬浮球"
            background = ballBackground(state)
            elevation = dp(8).toFloat()
        }
        // 内层浅色圆底让品牌图标在深色球面上依然清晰。
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_brand_mark)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F7F6F2"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        ball.addView(
            icon,
            FrameLayout.LayoutParams(dp(BALL_INNER_DP), dp(BALL_INNER_DP)).apply {
                gravity = Gravity.CENTER
            }
        )
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - dp(12)
            y = dp(140)
        }
        ballView = ball
        ballParams = params
        attachBallTouch(ball, params)
        windowManager.addView(ball, params)
    }

    private fun ballBackground(current: EngineState): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            when (current) {
                EngineState.RUNNING -> intArrayOf(Color.parseColor("#078A83"), Color.parseColor("#0B6E68"))
                EngineState.PAUSED -> intArrayOf(Color.parseColor("#C4782E"), Color.parseColor("#9A5A1E"))
                EngineState.RISK_STOP -> intArrayOf(Color.parseColor("#B3443A"), Color.parseColor("#8C2E27"))
                EngineState.IDLE -> intArrayOf(Color.parseColor("#42514E"), Color.parseColor("#2C3E3A"))
            }
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(2), Color.parseColor("#E8F0EC"))
        }

    private fun attachBallTouch(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var dragged = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragged = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) dragged = true
                    params.x = (initialX + dx.toInt()).coerceIn(0, max(0, screenWidth() - view.width))
                    params.y = (initialY + dy.toInt()).coerceIn(0, max(0, screenHeight() - view.height))
                    runCatching { windowManager.updateViewLayout(view, params) }
                    syncPanelPosition()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragged) {
                        if (panelAttached) hidePanel() else showPanel()
                    } else {
                        snapToEdge(view, params)
                    }
                    true
                }

                else -> false
            }
        }
        view.setOnLongClickListener {
            closeHud()
            true
        }
    }

    /** 松手后吸附到最近的左右边缘，动画结束再把面板对齐到球边。 */
    private fun snapToEdge(view: View, params: WindowManager.LayoutParams) {
        val target = if (params.x + view.width / 2 < screenWidth() / 2) 0 else max(0, screenWidth() - view.width)
        ValueAnimator.ofInt(params.x, target).apply {
            duration = 180L
            addUpdateListener { animator ->
                params.x = animator.animatedValue as Int
                runCatching { windowManager.updateViewLayout(view, params) }
                syncPanelPosition()
            }
            start()
        }
    }

    private fun showPanel() {
        if (panelAttached) {
            panelView?.visibility = View.VISIBLE
            syncPanelPosition()
            return
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#14201E"))
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.parseColor("#2C3E3A"))
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dp(10).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(this).apply {
                text = "BossClaw 控制台"
                setTextColor(Color.parseColor("#E8F0EC"))
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(
            TextView(this).apply {
                text = "收起"
                setTextColor(Color.parseColor("#8FA8A3"))
                textSize = 12f
                setPadding(dp(10), dp(2), dp(2), dp(2))
                setOnClickListener { hidePanel() }
            }
        )
        panelStatus = TextView(this).apply {
            text = "状态：${state.label}"
            setTextColor(Color.parseColor("#7FD9CF"))
            textSize = 12f
            setPadding(0, dp(4), 0, 0)
        }
        panelTarget = TextView(this).apply {
            text = "目标：—"
            setTextColor(Color.parseColor("#A9B8B4"))
            textSize = 12f
            setPadding(0, dp(3), 0, 0)
        }
        panelLogs = TextView(this).apply {
            text = "等待日志…"
            setTextColor(Color.parseColor("#C9D6D2"))
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            maxLines = 5
            setPadding(0, dp(8), 0, dp(8))
        }
        pauseButton = actionButton(if (state == EngineState.RUNNING) "暂停" else "继续", "#078A83").also {
            it.setOnClickListener {
                if (state == EngineState.RUNNING) {
                    BossClawApp.graph.orchestrator.pause()
                } else {
                    BossClawApp.graph.orchestrator.resume()
                }
            }
        }
        val startButton = actionButton("开始巡查", "#0B6E68").also { button ->
            button.setOnClickListener {
                if (!BossClawApp.graph.orchestrator.start(false)) {
                    toast("启动条件未满足，请检查权限与模型配置")
                }
            }
        }
        val inboxButton = actionButton("处理消息", "#2C3E3A").also { button ->
            button.setOnClickListener {
                if (!BossClawApp.graph.orchestrator.runInboxOnce()) {
                    toast("无法扫描消息，请检查无障碍与模型配置")
                }
            }
        }
        val emergencyButton = actionButton("急停", "#B3443A").also { button ->
            button.setOnClickListener {
                BossClawApp.graph.orchestrator.emergencyStop("用户从悬浮球紧急停止")
            }
        }
        val openButton = actionButton("打开应用", "#2C3E3A").also { button ->
            button.setOnClickListener {
                startActivity(Intent(this@FloatingHudService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        val exitButton = actionButton("收起悬浮球", "#2C3E3A").also { button ->
            button.setOnClickListener { closeHud() }
        }

        panel.addView(header)
        panel.addView(panelStatus)
        panel.addView(panelTarget)
        panel.addView(panelLogs)
        panel.addView(buttonRow(startButton, pauseButton!!))
        panel.addView(buttonRow(inboxButton, emergencyButton))
        panel.addView(buttonRow(openButton, exitButton))

        val position = panelPosition()
        val params = WindowManager.LayoutParams(
            dp(PANEL_WIDTH_DP),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.first
            y = position.second
        }
        panelView = panel
        panelParams = params
        windowManager.addView(panel, params)
        panelAttached = true
    }

    private fun hidePanel() {
        if (!panelAttached) return
        runCatching { windowManager.removeView(panelView) }
        panelView = null
        panelAttached = false
    }

    /** 面板贴着球体展开：球在左半屏时放右侧，反之放左侧。 */
    private fun panelPosition(): Pair<Int, Int> {
        val params = ballParams
        val panelWidth = dp(PANEL_WIDTH_DP)
        if (params == null) return dp(12) to dp(140)
        val ballSize = dp(BALL_SIZE_DP)
        val onLeftHalf = params.x + ballSize / 2 < screenWidth() / 2
        val x = if (onLeftHalf) {
            params.x + ballSize + dp(10)
        } else {
            params.x - panelWidth - dp(10)
        }
        val y = params.y.coerceIn(0, max(0, screenHeight() - dp(340)))
        return x.coerceIn(0, max(0, screenWidth() - panelWidth)) to y
    }

    private fun syncPanelPosition() {
        if (!panelAttached) return
        val params = panelParams ?: return
        val position = panelPosition()
        params.x = position.first
        params.y = position.second
        panelView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
    }

    private fun observeBus() {
        scope.launch {
            AgentBus.state.collect { current ->
                state = current
                ballView?.background = ballBackground(current)
                panelStatus?.text = "状态：${current.label}"
                if (panelAttached) {
                    pauseButton?.text = if (current == EngineState.RUNNING) "暂停" else "继续"
                }
            }
        }
        scope.launch {
            AgentBus.currentTarget.collect { target ->
                panelTarget?.text = "目标：${target.ifBlank { "—" }}"
            }
        }
        scope.launch {
            combine(AgentBus.logs, AgentBus.connected) { logs, connected -> logs to connected }.collect { (logs, connected) ->
                panelLogs?.text = if (logs.isEmpty()) {
                    if (connected) "通道就绪，等待巡查…" else "无障碍未连接"
                } else {
                    logs.takeLast(5).reversed().joinToString("\n") { line ->
                        "${TimeUtils.formatTime(line.timestamp)} [${line.source}] ${line.message.take(80)}"
                    }
                }
            }
        }
    }

    private fun buttonRow(left: TextView, right: TextView): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
            addView(left, actionParams())
            addView(right, actionParams())
        }

    private fun actionButton(text: String, color: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 12f
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(14).toFloat()
        }
    }

    private fun actionParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(6)
            topMargin = dp(2)
        }

    private fun setOverlayTouchable(touchable: Boolean) {
        listOfNotNull(ballView to ballParams, panelView to panelParams).forEach { (view, params) ->
            if (params == null) return@forEach
            if (touchable) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    /** 退出悬浮球：同步关闭设置里的开关，避免下次启动状态不一致。 */
    private fun closeHud() {
        BossClawApp.graph.configRepository.updateSettings { it.copy(floatingHud = false) }
        toast("悬浮球已关闭")
        stopSelf()
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun screenWidth(): Int = resources.displayMetrics.widthPixels

    private fun screenHeight(): Int = resources.displayMetrics.heightPixels

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_STOP = "com.bossclaw.android.action.STOP_HUD"
        private const val NOTIFICATION_ID = 2001
        private const val BALL_SIZE_DP = 54
        private const val BALL_INNER_DP = 40
        private const val PANEL_WIDTH_DP = 226
    }
}
