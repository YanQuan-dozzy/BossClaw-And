package com.bossclaw.android.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bossclaw.android.agent.EngineState
import com.bossclaw.android.model.DailyStats
import com.bossclaw.android.ui.BossClawViewModel
import com.bossclaw.android.ui.PermissionSnapshot
import com.bossclaw.android.ui.components.BossClawCard
import com.bossclaw.android.ui.components.QuickTile
import com.bossclaw.android.ui.components.StatusCell
import com.bossclaw.android.ui.navigation.Routes
import com.bossclaw.android.ui.theme.BossClawColors
import com.bossclaw.android.ui.theme.BossClawGradients
import com.bossclaw.android.ui.theme.BossClawMotion
import com.bossclaw.android.ui.theme.LocalBossClawDark
import kotlinx.coroutines.delay

/**
 * 工作台：只保留「引擎状态 + 环境自检 + 快捷入口 + 今日概要」四块，
 * 其余统计与日志拆到数据看板，避免首页信息过载。
 */
@Composable
fun WorkbenchScreen(viewModel: BossClawViewModel, onNavigate: (String) -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val scene by viewModel.scene.collectAsStateWithLifecycle()
    val target by viewModel.currentTarget.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Reveal(index = 0) {
            HeroEngineCard(
                state = engineState,
                connected = connected,
                sceneText = scene.description,
                target = target,
                runModeLabel = settings.runMode.label,
                runModeHint = settings.runMode.hint,
                delivered = stats.delivered,
                dailyLimit = settings.maxDailyGreetings,
                onStart = viewModel::startEngine,
                onStartOnce = viewModel::startOnce,
                onPause = viewModel::pauseEngine,
                onResume = viewModel::resumeEngine,
                onStop = viewModel::stopEngine,
                onInbox = viewModel::runInboxOnce,
                onEmergency = viewModel::emergencyStop
            )
        }
        Reveal(index = 1) {
            SystemStatusCard(permissions = permissions) { onNavigate(Routes.PERMISSIONS) }
        }
        Reveal(index = 2) {
            QuickAccessCard(onNavigate = onNavigate)
        }
        Reveal(index = 3) {
            TodayOverview(stats = stats) { onNavigate(Routes.INSIGHTS) }
        }
    }
}

/** 首屏逐块淡入上浮，形成「内容依次进场」的节奏感。 */
@Composable
private fun Reveal(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        shown = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(BossClawMotion.BASE, easing = BossClawMotion.EmphasizedDecel),
        label = "reveal-alpha"
    )
    val offset by animateDpAsState(
        targetValue = if (shown) 0.dp else 16.dp,
        animationSpec = tween(BossClawMotion.BASE, easing = BossClawMotion.EmphasizedDecel),
        label = "reveal-offset"
    )
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            translationY = offset.toPx()
        }
    ) {
        content()
    }
}

@Composable
private fun HeroEngineCard(
    state: EngineState,
    connected: Boolean,
    sceneText: String,
    target: String,
    runModeLabel: String,
    runModeHint: String,
    delivered: Int,
    dailyLimit: Int,
    onStart: () -> Unit,
    onStartOnce: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onInbox: () -> Unit,
    onEmergency: () -> Unit
) {
    val dark = LocalBossClawDark.current
    val stateColor = when (state) {
        EngineState.RUNNING -> Color(0xFF9CF2E7)
        EngineState.PAUSED -> Color(0xFFFFD79A)
        EngineState.RISK_STOP -> Color(0xFFFFB4A9)
        EngineState.IDLE -> Color(0xFFD7E4E1)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(BossClawGradients.hero(dark))
    ) {
        RadarBackdrop(modifier = Modifier.matchParentSize())
        Column(modifier = Modifier.padding(22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "巡查引擎",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = Color.White.copy(alpha = 0.7f)
                )
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (connected) Color(0xFF9CF2E7) else Color(0xFFFFC9C4),
                                    CircleShape
                                )
                        )
                        Text(
                            text = if (connected) "无障碍已连接" else "无障碍未连接",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.92f),
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PulsingDot(color = stateColor, pulsing = state == EngineState.RUNNING)
                Text(
                    text = state.label,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }

            Text(
                text = "场景：$sceneText",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f),
                modifier = Modifier.padding(top = 6.dp)
            )
            if (target.isNotBlank()) {
                Text(
                    text = "正在处理 · $target",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            QuotaBar(
                delivered = delivered,
                limit = dailyLimit,
                dark = dark,
                modifier = Modifier.padding(top = 18.dp)
            )
            Text(
                text = "$runModeLabel · $runModeHint",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.68f),
                modifier = Modifier.padding(top = 9.dp)
            )

            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (state) {
                    EngineState.RUNNING -> {
                        HeroButton(
                            text = "暂停",
                            icon = Icons.Filled.Pause,
                            primary = true,
                            onClick = onPause
                        )
                        HeroButton(
                            text = "停止",
                            icon = Icons.Filled.Stop,
                            primary = false,
                            onClick = onStop
                        )
                    }

                    EngineState.PAUSED -> {
                        HeroButton(
                            text = "继续",
                            icon = Icons.Filled.PlayArrow,
                            primary = true,
                            onClick = onResume
                        )
                        HeroButton(
                            text = "停止",
                            icon = Icons.Filled.Stop,
                            primary = false,
                            onClick = onStop
                        )
                    }

                    else -> {
                        HeroButton(
                            text = "开始巡查",
                            icon = Icons.Filled.PlayArrow,
                            primary = true,
                            onClick = onStart
                        )
                        HeroButton(
                            text = "试跑一轮",
                            icon = Icons.Filled.Refresh,
                            primary = false,
                            onClick = onStartOnce
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onInbox) {
                    Icon(
                        imageVector = Icons.Filled.Sync,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "处理消息",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(start = 5.dp)
                    )
                }
                TextButton(onClick = onEmergency) {
                    Text(
                        text = "急停",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFFFD2CC)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean,
    onClick: () -> Unit
) {
    if (primary) {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = BossClawColors.TealDeep
            )
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 5.dp)
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 5.dp)
            )
        }
    }
}

@Composable
private fun QuotaBar(delivered: Int, limit: Int, dark: Boolean, modifier: Modifier = Modifier) {
    val safeLimit = limit.coerceAtLeast(1)
    val fraction = (delivered.toFloat() / safeLimit.toFloat()).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(BossClawMotion.SLOW, easing = BossClawMotion.EmphasizedDecel),
        label = "quota-bar"
    )
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "今日投递",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = "$delivered / $limit",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(7.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated.coerceAtLeast(0.001f))
                    .clip(CircleShape)
                    .background(BossClawGradients.quota(dark))
            )
        }
    }
}

@Composable
private fun PulsingDot(color: Color, pulsing: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse-dot")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulsing) 1.9f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = BossClawMotion.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse-dot-scale"
    )
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulsing) 0.35f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = BossClawMotion.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse-dot-alpha"
    )
    Box(
        modifier = Modifier
            .size(12.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .background(color, CircleShape)
    )
}

/** 雷达装饰：同心圆 + 旋转扫描扇面，低透明度，不干扰正文可读性。 */
@Composable
private fun RadarBackdrop(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(7200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar-sweep"
    )
    val ripple by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = BossClawMotion.Standard),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar-ripple"
    )
    Canvas(modifier = modifier) {
        val center = Offset(size.width * 0.93f, size.height * 0.08f)
        val radius = size.minDimension * 0.72f
        val ring = Color.White.copy(alpha = 0.12f)
        repeat(3) { index ->
            drawCircle(
                color = ring,
                radius = radius * (0.45f + index * 0.28f),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }
        rotate(degrees = sweep, pivot = center) {
            val rect = Rect(
                offset = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
            val wedge = Path().apply {
                moveTo(center.x, center.y)
                arcTo(rect, -90f, 42f, false)
                close()
            }
            drawPath(
                path = wedge,
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.2f), Color.Transparent),
                    center = center,
                    radius = radius
                )
            )
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = center,
                end = Offset(center.x, center.y - radius),
                strokeWidth = 1.5.dp.toPx()
            )
        }
        drawCircle(
            color = Color.White.copy(alpha = 0.45f * (1f - ripple)),
            radius = 6.dp.toPx() + 30.dp.toPx() * ripple,
            center = center,
            style = Stroke(width = 1.4.dp.toPx())
        )
    }
}

@Composable
private fun SystemStatusCard(permissions: PermissionSnapshot, onOpen: () -> Unit) {
    val readyCount = listOf(
        permissions.accessibility,
        permissions.overlay,
        permissions.notifications,
        !permissions.batteryOptimized
    ).count { it }
    BossClawCard(padding = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "运行环境",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$readyCount / 4 项就绪",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (readyCount == 4) BossClawColors.Success else BossClawColors.Amber
                )
            }
            TextButton(onClick = onOpen) { Text("权限自检") }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            StatusCell(
                title = "无障碍",
                ready = permissions.accessibility,
                modifier = Modifier.weight(1f),
                onClick = onOpen
            )
            StatusCell(
                title = "悬浮窗",
                ready = permissions.overlay,
                modifier = Modifier.weight(1f),
                onClick = onOpen
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            StatusCell(
                title = "通知",
                ready = permissions.notifications,
                modifier = Modifier.weight(1f),
                onClick = onOpen
            )
            StatusCell(
                title = "后台运行",
                ready = !permissions.batteryOptimized,
                modifier = Modifier.weight(1f),
                onClick = onOpen
            )
        }
    }
}

@Composable
private fun QuickAccessCard(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile(
                icon = Icons.Outlined.Work,
                title = "岗位",
                subtitle = "评估与投递",
                accent = BossClawColors.Teal,
                onClick = { onNavigate(Routes.JOBS) },
                modifier = Modifier.weight(1f)
            )
            QuickTile(
                icon = Icons.AutoMirrored.Outlined.Chat,
                title = "沟通",
                subtitle = "HR 会话",
                accent = BossClawColors.Amber,
                onClick = { onNavigate(Routes.CHAT) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile(
                icon = Icons.Outlined.Description,
                title = "简历中心",
                subtitle = "画像与方向",
                accent = BossClawColors.TealDeep,
                onClick = { onNavigate(Routes.RESUME) },
                modifier = Modifier.weight(1f)
            )
            QuickTile(
                icon = Icons.Outlined.BarChart,
                title = "数据看板",
                subtitle = "漏斗与足迹",
                accent = BossClawColors.Muted,
                onClick = { onNavigate(Routes.INSIGHTS) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TodayOverview(stats: DailyStats, onOpenInsights: () -> Unit) {
    BossClawCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "今日概要",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onOpenInsights) { Text("数据看板") }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniMetric(
                label = "发现",
                value = stats.discovered,
                color = BossClawColors.Teal,
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = "投递",
                value = stats.delivered,
                color = BossClawColors.Amber,
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = "回复",
                value = stats.replied,
                color = BossClawColors.Success,
                modifier = Modifier.weight(1f)
            )
            MiniMetric(
                label = "约面",
                value = stats.interview,
                color = BossClawColors.TealDeep,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "首次发送后会自动暂停，便于核对会话；遇到验证码会立即停手",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun MiniMetric(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = tween(BossClawMotion.SLOW, easing = BossClawMotion.EmphasizedDecel),
        label = "mini-metric"
    )
    Column(modifier = modifier) {
        Text(
            text = animated.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
