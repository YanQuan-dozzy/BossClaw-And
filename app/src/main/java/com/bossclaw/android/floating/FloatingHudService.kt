package com.bossclaw.android.floating

import android.animation.ValueAnimator
import android.app.Notification
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
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
import com.bossclaw.android.agent.JobAnalysis
import com.bossclaw.android.accessibility.util.GestureEngine
import com.bossclaw.android.core.TimeUtils
import com.bossclaw.android.di.AppGraph
import com.bossclaw.android.util.PermissionTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
    private var panelAnalysis: TextView? = null
    private var panelGreeting: TextView? = null
    private var panelLogs: TextView? = null
    private var pauseButton: TextView? = null

    private var analysisView: LinearLayout? = null
    private var analysisParams: WindowManager.LayoutParams? = null
    private var statusDot: View? = null
    private var statusText: TextView? = null
    private var statusDetail: TextView? = null
    private var statusGreeting: TextView? = null
    private var statusCopy: TextView? = null

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
        analysisView?.let { runCatching { windowManager.removeView(it) } }
        panelView?.let { runCatching { windowManager.removeView(it) } }
        ballView?.let { runCatching { windowManager.removeView(it) } }
        analysisView = null
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

    private fun ballBackground(current: EngineState): GradientDrawable {
        val night = isNight()
        val color = when (current) {
            EngineState.RUNNING -> if (night) "#5FD1C8" else "#0B7A75"
            EngineState.PAUSED -> if (night) "#E0A467" else "#C4782E"
            EngineState.RISK_STOP -> if (night) "#E58C82" else "#B3443A"
            EngineState.IDLE -> if (night) "#1E2A27" else "#5F6E68"
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(color))
            // 柔和描边替代原来的硬白圈，深浅主题各用一套。
            setStroke(
                dp(1),
                if (night) Color.parseColor("#2A3835") else Color.argb(120, 255, 255, 255)
            )
        }
    }

    private fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

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
                    syncAnalysisPosition()
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
                syncAnalysisPosition()
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
        // 控制台展开后接管状态展示，避免与旁边的状态胶囊重叠。
        hideStatus()
        val night = isNight()
        val surface = if (night) "#17211F" else "#FFFFFF"
        val onSurface = if (night) "#E9F1ED" else "#1B2422"
        val muted = if (night) "#93A39D" else "#5F6E68"
        val softTrack = if (night) "#1E2A27" else "#E7E2D6"
        val primary = if (night) "#5FD1C8" else "#0B7A75"
        val onPrimary = if (night) "#00312D" else "#FFFFFF"
        val danger = if (night) "#E58C82" else "#B3443A"
        val onDanger = if (night) "#3B0A06" else "#FFFFFF"
        val logColor = if (night) "#B6C4BF" else "#46544F"
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor(surface))
                cornerRadius = dp(24).toFloat()
            }
            setPadding(dp(20), dp(16), dp(20), dp(16))
            elevation = dp(12).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(this).apply {
                text = "BossClaw 控制台"
                setTextColor(Color.parseColor(onSurface))
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(
            TextView(this).apply {
                text = "收起"
                setTextColor(Color.parseColor(muted))
                textSize = 12f
                setPadding(dp(10), dp(2), dp(2), dp(2))
                setOnClickListener { hidePanel() }
            }
        )
        panelStatus = TextView(this).apply {
            text = "状态：${state.label}"
            setTextColor(Color.parseColor(primary))
            textSize = 12f
            setPadding(0, dp(4), 0, 0)
        }
        panelTarget = TextView(this).apply {
            text = "目标：—"
            setTextColor(Color.parseColor(muted))
            textSize = 12f
            setPadding(0, dp(3), 0, 0)
        }
        panelAnalysis = TextView(this).apply {
            text = AgentBus.analysis.value?.let { analysisSummary(it) } ?: "分析：等待评估岗位…"
            setTextColor(Color.parseColor(logColor))
            textSize = 11f
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(softTrack))
                cornerRadius = dp(12).toFloat()
            }
        }
        panelGreeting = TextView(this).apply {
            val greeting = AgentBus.analysis.value?.greeting?.trim().orEmpty()
            text = if (greeting.isBlank()) "" else "开场白 · 点击复制\n$greeting"
            setTextColor(Color.parseColor(logColor))
            textSize = 11f
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(softTrack))
                cornerRadius = dp(12).toFloat()
            }
            visibility = if (greeting.isBlank()) View.GONE else View.VISIBLE
            setOnClickListener { copyGreetingToClipboard() }
        }
        panelLogs = TextView(this).apply {
            text = "等待日志…"
            setTextColor(Color.parseColor(logColor))
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            maxLines = 5
            setPadding(0, dp(8), 0, dp(8))
        }
        pauseButton = actionButton(
            if (state == EngineState.RUNNING) "暂停" else "继续",
            primary,
            onPrimary
        ).also {
            it.setOnClickListener {
                if (state == EngineState.RUNNING) {
                    BossClawApp.graph.orchestrator.pause()
                } else {
                    BossClawApp.graph.orchestrator.resume()
                }
            }
        }
        // 打开 BOSS 后只做岗位评估与投递，不切到消息列表；消息处理保留独立按钮。
        val bossButton = actionButton("打开BOSS", primary, onPrimary).also { button ->
            button.setOnClickListener {
                val launched = PermissionTools.launchBoss(this)
                val orchestrator = BossClawApp.graph.orchestrator
                when {
                    orchestrator.isRunning ->
                        toast(if (launched) "BOSS 已打开，岗位巡查进行中" else "岗位巡查进行中")
                    orchestrator.start(false, inbox = false) ->
                        toast(if (launched) "BOSS 已打开，仅评估岗位并投递" else "已开始评估岗位并投递")
                    else ->
                        toast("巡查未启动，请检查 BOSS 安装、权限与模型配置")
                }
            }
        }
        val inboxButton = actionButton("处理消息", softTrack, onSurface).also { button ->
            button.setOnClickListener {
                val orchestrator = BossClawApp.graph.orchestrator
                // 巡查与消息巡检共用 BOSS 页面，巡查中不允许再并发启动消息处理
                if (orchestrator.isRunning) {
                    toast("岗位巡查进行中，暂停或停止后再处理消息")
                    return@setOnClickListener
                }
                if (!orchestrator.runInboxOnce()) {
                    toast("无法扫描消息，请检查无障碍与模型配置")
                }
            }
        }
        val emergencyButton = actionButton("急停", danger, onDanger).also { button ->
            button.setOnClickListener {
                BossClawApp.graph.orchestrator.emergencyStop("用户从悬浮球紧急停止")
            }
        }
        val exitButton = actionButton("收起悬浮球", softTrack, onSurface).also { button ->
            button.setOnClickListener { closeHud() }
        }

        panel.addView(header)
        panel.addView(panelStatus)
        panel.addView(panelTarget)
        val analysisText = panelAnalysis!!
        panel.addView(
            analysisText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )
        panel.addView(
            panelGreeting!!,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        )
        panel.addView(panelLogs)
        panel.addView(buttonRow(bossButton, pauseButton!!))
        panel.addView(buttonRow(inboxButton, emergencyButton))
        panel.addView(
            exitButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        )

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
        // 面板高度会随评估信息与开场白变化，测量完成后重新贴边，避免底部越界
        panel.post { syncPanelPosition() }
    }

    private fun hidePanel() {
        if (!panelAttached) return
        runCatching { windowManager.removeView(panelView) }
        panelView = null
        panelAttached = false
        // 收起控制台后恢复常驻状态胶囊，不必再点开悬浮球查看当前进度。
        showStatus()
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
        val y = params.y.coerceIn(
            0,
            max(0, screenHeight() - (panelView?.height?.takeIf { it > 0 } ?: dp(PANEL_ESTIMATE_HEIGHT_DP)))
        )
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

    /** 收起状态下的常驻评估卡：贴在球体旁展示完整评估结论与可复制开场白，点按展开控制台。 */
    private fun showStatus() {
        if (panelAttached) return
        if (analysisView == null && !installStatusView()) return
        updateStatusContent()
        syncAnalysisPosition()
    }

    private fun installStatusView(): Boolean {
        val night = isNight()
        val surface = if (night) "#17211F" else "#FFFFFF"
        val onSurface = if (night) "#E9F1ED" else "#1B2422"
        val muted = if (night) "#93A39D" else "#5F6E68"
        val stroke = if (night) "#2A3835" else "#E7E2D6"
        val softTrack = if (night) "#1E2A27" else "#E7E2D6"
        val primary = if (night) "#5FD1C8" else "#0B7A75"
        val onPrimary = if (night) "#00312D" else "#FFFFFF"
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor(surface))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.parseColor(stroke))
            }
            setPadding(dp(12), dp(8), dp(12), dp(10))
            elevation = dp(8).toFloat()
            setOnClickListener { showPanel() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(statusColor())
            }
        }
        header.addView(dot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(7) })
        val label = TextView(this).apply {
            setTextColor(Color.parseColor(onSurface))
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(STATUS_HEADLINE_MAX_WIDTH_DP)
        }
        header.addView(
            label,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        card.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // 完整评估信息：公司岗位、评分理由、亮点与风险，全量展示不截断
        val detail = TextView(this).apply {
            setTextColor(Color.parseColor(muted))
            textSize = 11f
            setPadding(0, dp(4), 0, 0)
            maxWidth = dp(STATUS_CONTENT_MAX_WIDTH_DP)
            visibility = View.GONE
        }
        card.addView(
            detail,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val greeting = TextView(this).apply {
            setTextColor(Color.parseColor(muted))
            textSize = 11f
            setPadding(dp(8), dp(6), dp(8), dp(6))
            maxWidth = dp(STATUS_CONTENT_MAX_WIDTH_DP)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(softTrack))
                cornerRadius = dp(10).toFloat()
            }
            visibility = View.GONE
            setOnClickListener { copyGreetingToClipboard() }
        }
        card.addView(
            greeting,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        )

        val copy = TextView(this).apply {
            text = "复制开场白"
            setTextColor(Color.parseColor(onPrimary))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(primary))
                cornerRadius = dp(14).toFloat()
            }
            visibility = View.GONE
            setOnClickListener { copyGreetingToClipboard() }
        }
        card.addView(
            copy,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        )

        val position = analysisPosition()
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.first
            y = position.second
        }
        val added = runCatching { windowManager.addView(card, params) }.isSuccess
        if (!added) return false
        analysisView = card
        analysisParams = params
        statusDot = dot
        statusText = label
        statusDetail = detail
        statusGreeting = greeting
        statusCopy = copy
        // 宽高测量完成后再贴边对齐，避免球在右半屏时卡片越界
        card.post { syncAnalysisPosition() }
        // 卡片宽度随文字内容变化，尺寸变化后立即按新宽度重新贴边
        card.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val widthChanged = (right - left) != (oldRight - oldLeft)
            val heightChanged = (bottom - top) != (oldBottom - oldTop)
            if (widthChanged || heightChanged) syncAnalysisPosition()
        }
        return true
    }

    private fun updateStatusContent() {
        val label = statusText ?: return
        val text = statusSummary()
        if (label.text != text) label.text = text
        (statusDot?.background as? GradientDrawable)?.setColor(statusColor())

        // 风控/暂停时只保留状态文案；有新鲜评估时展开完整结论与开场白
        val analysis = AgentBus.analysis.value
        val current = analysis?.takeIf {
            System.nanoTime() - it.at < ANALYSIS_FRESH_NS &&
                state != EngineState.RISK_STOP && state != EngineState.PAUSED
        }
        statusDetail?.let { view ->
            if (current != null) {
                view.text = analysisDetailText(current)
                view.visibility = View.VISIBLE
            } else {
                view.text = ""
                view.visibility = View.GONE
            }
        }
        val greeting = current?.greeting?.trim().orEmpty()
        statusGreeting?.let { view ->
            if (greeting.isBlank()) {
                view.text = ""
                view.visibility = View.GONE
            } else {
                view.text = "开场白\n$greeting"
                view.visibility = View.VISIBLE
            }
        }
        statusCopy?.visibility = if (greeting.isBlank()) View.GONE else View.VISIBLE
        // 卡片高度随内容变化，重新贴边避免超出屏幕
        analysisView?.post { syncAnalysisPosition() }
    }

    /** 收起时的信息优先级：风控/暂停 > 最近一次评估 > 当前目标 > 空闲 */
    private fun statusSummary(): String {
        val analysis = AgentBus.analysis.value
        val fresh = analysis != null && System.nanoTime() - analysis.at < ANALYSIS_FRESH_NS
        return when {
            state == EngineState.RISK_STOP -> "风控暂停"
            state == EngineState.PAUSED -> "已暂停"
            fresh -> when {
                analysis.evaluating -> "评估中 · ${analysis.title}"
                analysis.score <= 0 -> "${analysis.fitLabel} · ${analysis.title}"
                else -> "${analysis.fitLabel} ${analysis.score}分 · ${analysis.title}"
            }
            state == EngineState.RUNNING ->
                AgentBus.currentTarget.value.takeIf { it.isNotBlank() } ?: "巡查中"
            else -> "空闲就绪"
        }
    }

    private fun statusColor(): Int {
        val night = isNight()
        return Color.parseColor(
            when (state) {
                EngineState.RUNNING -> if (night) "#5FD1C8" else "#0B7A75"
                EngineState.PAUSED -> if (night) "#E0A467" else "#C4782E"
                EngineState.RISK_STOP -> if (night) "#E58C82" else "#B3443A"
                EngineState.IDLE -> if (night) "#93A39D" else "#5F6E68"
            }
        )
    }

    private fun hideStatus() {
        analysisView?.let { runCatching { windowManager.removeView(it) } }
        analysisView = null
        analysisParams = null
        statusDot = null
        statusText = null
        statusDetail = null
        statusGreeting = null
        statusCopy = null
    }

    /** 首次挂载前估算卡片宽度：有新鲜评估时按完整宽度贴边，空闲时按单行宽度 */
    private fun estimatedStatusWidthDp(): Int {
        val analysis = AgentBus.analysis.value
        val expanded = analysis != null &&
            System.nanoTime() - analysis.at < ANALYSIS_FRESH_NS &&
            state != EngineState.RISK_STOP && state != EngineState.PAUSED
        return if (expanded) STATUS_ESTIMATE_WIDTH_DP else STATUS_COMPACT_WIDTH_DP
    }

    private fun analysisPosition(): Pair<Int, Int> {
        val params = ballParams ?: return dp(12) to dp(140)
        val width = analysisView?.width?.takeIf { it > 0 } ?: dp(estimatedStatusWidthDp())
        val height = analysisView?.height?.takeIf { it > 0 } ?: dp(STATUS_ESTIMATE_HEIGHT_DP)
        val ballSize = dp(BALL_SIZE_DP)
        val onLeftHalf = params.x + ballSize / 2 < screenWidth() / 2
        val x = if (onLeftHalf) {
            params.x + ballSize + dp(10)
        } else {
            params.x - width - dp(10)
        }
        val y = params.y + (ballSize - height) / 2
        return x.coerceIn(0, max(0, screenWidth() - width)) to
            y.coerceIn(0, max(0, screenHeight() - height))
    }

    private fun syncAnalysisPosition() {
        val params = analysisParams ?: return
        val view = analysisView ?: return
        val position = analysisPosition()
        if (params.x == position.first && params.y == position.second) return
        params.x = position.first
        params.y = position.second
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun analysisSummary(analysis: JobAnalysis): String = buildString {
        append("${analysis.company} · ${analysis.title}\n")
        if (analysis.evaluating) {
            append(analysis.reason.ifBlank { "正在读取岗位详情并评估…" })
            return@buildString
        }
        if (analysis.score > 0) {
            append("${analysis.fitLabel} ${analysis.score} 分")
        } else {
            append(analysis.fitLabel)
        }
        if (analysis.reason.isNotBlank()) append("｜${analysis.reason}")
        if (analysis.highlights.isNotEmpty()) {
            append("\n亮点：${analysis.highlights.joinToString("；")}")
        }
        if (analysis.risks.isNotEmpty()) {
            append("\n风险：${analysis.risks.joinToString("；")}")
        }
    }

    /** 收起卡片上的完整评估：公司岗位、理由、亮点与风险，不做截断。 */
    private fun analysisDetailText(analysis: JobAnalysis): String = buildString {
        append("${analysis.company} · ${analysis.title}")
        if (analysis.evaluating) {
            append("\n${analysis.reason.ifBlank { "正在读取岗位详情并评估…" }}")
            return@buildString
        }
        if (analysis.reason.isNotBlank()) append("\n${analysis.reason}")
        if (analysis.highlights.isNotEmpty()) {
            append("\n亮点：${analysis.highlights.joinToString("；")}")
        }
        if (analysis.risks.isNotEmpty()) {
            append("\n风险：${analysis.risks.joinToString("；")}")
        }
    }

    /** 复制当前评估的开场白，用户可直接粘贴到 BOSS 会话。 */
    private fun copyGreetingToClipboard() {
        val greeting = AgentBus.analysis.value?.greeting?.trim().orEmpty()
        if (greeting.isEmpty()) {
            toast("暂无开场白可复制")
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("开场白", greeting))
        toast("开场白已复制")
    }

    /** 控制台里的开场白区块：有内容时展示并提供点击复制，无内容时隐藏。 */
    private fun updatePanelGreeting(analysis: JobAnalysis) {
        val view = panelGreeting ?: return
        val greeting = analysis.greeting.trim()
        if (greeting.isBlank()) {
            view.text = ""
            view.visibility = View.GONE
        } else {
            view.text = "开场白 · 点击复制\n$greeting"
            view.visibility = View.VISIBLE
        }
        // 面板高度变化后重新贴边，避免底部内容超出屏幕
        if (panelAttached) panelView?.post { syncPanelPosition() }
    }

    private fun observeBus() {
        scope.launch {
            AgentBus.state.collect { current ->
                state = current
                ballView?.background = ballBackground(current)
                panelStatus?.text = "状态：${current.label}"
                if (panelAttached) {
                    pauseButton?.text = if (current == EngineState.RUNNING) "暂停" else "继续"
                } else {
                    showStatus()
                }
            }
        }
        scope.launch {
            AgentBus.analysis.collect { analysis ->
                if (analysis == null) return@collect
                panelAnalysis?.text = analysisSummary(analysis)
                updatePanelGreeting(analysis)
                // 收起时直接更新常驻评估卡，展开时信息在控制台内展示
                if (!panelAttached) showStatus()
            }
        }
        scope.launch {
            AgentBus.currentTarget.collect { target ->
                panelTarget?.text = "目标：${target.ifBlank { "—" }}"
                if (!panelAttached) updateStatusContent()
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

    private fun actionButton(text: String, bgColor: String, textColor: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor(textColor))
        textSize = 12f
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(10), dp(12), dp(10))
        elevation = dp(2).toFloat()
        background = GradientDrawable().apply {
            setColor(Color.parseColor(bgColor))
            cornerRadius = dp(20).toFloat()
        }
    }

    private fun actionParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(6)
            topMargin = dp(2)
        }

    private fun setOverlayTouchable(touchable: Boolean) {
        listOfNotNull(
            ballView to ballParams,
            panelView to panelParams,
            analysisView to analysisParams
        ).forEach { (view, params) ->
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
        private const val PANEL_WIDTH_DP = 248

        /** 评估卡宽度随文字内容收缩，展示评估时最多占 260dp */
        private const val STATUS_CONTENT_MAX_WIDTH_DP = 236
        private const val STATUS_HEADLINE_MAX_WIDTH_DP = 220

        /** 评估卡首次挂载时的估算宽高，测量完成后会按真实宽高贴边 */
        private const val STATUS_ESTIMATE_WIDTH_DP = 260
        private const val STATUS_COMPACT_WIDTH_DP = 110
        private const val STATUS_ESTIMATE_HEIGHT_DP = 32

        /** 控制台首次挂载时的估算高度，测量完成后按真实高度贴边 */
        private const val PANEL_ESTIMATE_HEIGHT_DP = 380

        /** 评估结论在胶囊上的展示时长，超过后回到巡查目标/状态文案。 */
        private const val ANALYSIS_FRESH_NS = 60_000_000_000L
    }
}
