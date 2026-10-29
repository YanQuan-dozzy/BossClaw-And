package com.bossclaw.android.floating

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
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
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

class FloatingHudService : Service() {

    private lateinit var windowManager: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var bubbleView: View? = null
    private var panelView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelAttached = false

    private var bubbleText: TextView? = null
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
        installBubble()
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
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
        bubbleView = null
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notification: Notification = NotificationCompat.Builder(this, AppGraph.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bossclaw)
            .setContentTitle("BossClaw 悬浮控制台")
            .setContentText("点击悬浮球展开巡查状态")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun installBubble() {
        val size = dp(54)
        val bubble = TextView(this).apply {
            text = "BC"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#078A83"), Color.parseColor("#0B6E68"))
            ).apply { cornerRadius = dp(18).toFloat() }
            elevation = dp(6).toFloat()
        }
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - dp(14)
            y = dp(120)
        }
        bubbleText = bubble
        bubbleView = bubble
        bubbleParams = params
        attachDrag(bubble, params)
        windowManager.addView(bubble, params)
    }

    private fun installPanel() {
        if (panelAttached) {
            panelView?.visibility = View.VISIBLE
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
        val title = TextView(this).apply {
            text = "BossClaw 控制台"
            setTextColor(Color.parseColor("#E8F0EC"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        panelStatus = TextView(this).apply {
            text = "状态：待命"
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
            maxLines = 6
            setPadding(0, dp(8), 0, dp(8))
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        pauseButton = actionButton("暂停", "#078A83").also { button ->
            button.setOnClickListener {
                if (state == EngineState.RUNNING) {
                    AppGraphHolder.orchestrator.pause()
                } else {
                    AppGraphHolder.orchestrator.resume()
                }
            }
            actions.addView(button, actionParams())
        }
        actions.addView(actionButton("急停", "#B3443A").also { button ->
            button.setOnClickListener {
                AppGraphHolder.orchestrator.emergencyStop("用户从悬浮窗紧急停止")
            }
        }, actionParams())
        actions.addView(actionButton("打开", "#2C3E3A").also { button ->
            button.setOnClickListener {
                startActivity(Intent(this@FloatingHudService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }, actionParams())
        actions.addView(actionButton("收起", "#2C3E3A").also { button ->
            button.setOnClickListener { hidePanel() }
        }, actionParams())

        panel.addView(title)
        panel.addView(panelStatus)
        panel.addView(panelTarget)
        panel.addView(panelLogs)
        panel.addView(actions)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            x = 0
            y = dp(186)
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

    private fun observeBus() {
        scope.launch {
            AgentBus.state.collect { current ->
                state = current
                bubbleText?.text = when (current) {
                    EngineState.RUNNING -> "BC·巡"
                    EngineState.PAUSED -> "BC·停"
                    EngineState.RISK_STOP -> "BC·险"
                    EngineState.IDLE -> "BC"
                }
                panelStatus?.text = "状态：${current.label}"
                bubbleText?.background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    when (current) {
                        EngineState.RUNNING -> intArrayOf(Color.parseColor("#078A83"), Color.parseColor("#0B6E68"))
                        EngineState.PAUSED -> intArrayOf(Color.parseColor("#C4782E"), Color.parseColor("#9A5A1E"))
                        EngineState.RISK_STOP -> intArrayOf(Color.parseColor("#B3443A"), Color.parseColor("#8C2E27"))
                        EngineState.IDLE -> intArrayOf(Color.parseColor("#42514E"), Color.parseColor("#2C3E3A"))
                    }
                ).apply { cornerRadius = dp(18).toFloat() }
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
                    logs.takeLast(6).reversed().joinToString("\n") { line ->
                        "${TimeUtils.formatTime(line.timestamp)} [${line.source}] ${line.message.take(90)}"
                    }
                }
            }
        }
    }

    private fun attachDrag(view: View, params: WindowManager.LayoutParams) {
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
                    if (kotlin.math.abs(dx) > dp(8) || kotlin.math.abs(dy) > dp(8)) dragged = true
                    params.x = (initialX + dx.toInt()).coerceIn(0, resources.displayMetrics.widthPixels - view.width)
                    params.y = (initialY + dy.toInt()).coerceIn(0, resources.displayMetrics.heightPixels - view.height)
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) {
                        if (panelAttached) hidePanel() else installPanel()
                    }
                    true
                }
                else -> false
            }
        }
        view.setOnLongClickListener {
            stopSelf()
            true
        }
    }

    private fun actionButton(text: String, color: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 12f
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(7), dp(14), dp(7))
        background = GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(14).toFloat()
        }
    }

    private fun actionParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(6)
        }

    private fun setOverlayTouchable(touchable: Boolean) {
        listOfNotNull(bubbleView to bubbleParams, panelView to panelParams).forEach { (view, params) ->
            if (params == null) return@forEach
            if (touchable) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private object AppGraphHolder {
        val orchestrator get() = BossClawApp.graph.orchestrator
    }

    companion object {
        const val ACTION_STOP = "com.bossclaw.android.action.STOP_HUD"
        private const val NOTIFICATION_ID = 2001
    }
}
