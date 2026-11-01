package com.bossclaw.android.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bossclaw.android.core.TimeUtils
import com.bossclaw.android.model.DailyStats
import com.bossclaw.android.model.LogLine
import com.bossclaw.android.ui.BossClawViewModel
import com.bossclaw.android.ui.components.BossClawCard
import com.bossclaw.android.ui.components.CompactLogRow
import com.bossclaw.android.ui.components.EmptyState
import com.bossclaw.android.ui.components.ScreenHeader
import com.bossclaw.android.ui.components.StatTile
import com.bossclaw.android.ui.components.tokenLabel
import com.bossclaw.android.ui.theme.BossClawColors
import com.bossclaw.android.ui.theme.BossClawMotion
import com.bossclaw.android.ui.theme.BossClawSpacing
import com.bossclaw.android.ui.theme.LocalBossClawDark

@Composable
fun InsightsScreen(viewModel: BossClawViewModel, onOpenRecords: () -> Unit) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val funnel by viewModel.funnel.collectAsStateWithLifecycle()
    val logs by viewModel.console.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = BossClawSpacing.screen,
                end = BossClawSpacing.screen,
                top = BossClawSpacing.xxs,
                bottom = BossClawSpacing.xl
            ),
        verticalArrangement = Arrangement.spacedBy(BossClawSpacing.md)
    ) {
        ScreenHeader(
            title = "数据看板",
            subtitle = "今日节奏、转化漏斗与最近动态"
        ) {
            TextButton(onClick = onOpenRecords) { Text("全部记录") }
        }

        StatsGrid(stats = stats, funnel = funnel)

        FunnelCard(funnel = funnel, onOpenRecords = onOpenRecords)

        RecentActivity(logs = logs, onOpenRecords = onOpenRecords)

        Text(
            text = "统计来自本地数据库，按本机时区计算；Token 统计仅作本地参考",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatsGrid(stats: DailyStats, funnel: DailyStats) {
    val dark = LocalBossClawDark.current
    val colors = MaterialTheme.colorScheme
    val successColor = if (dark) Color(0xFF6FD69B) else BossClawColors.Success
    Column(verticalArrangement = Arrangement.spacedBy(BossClawSpacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(BossClawSpacing.xs)) {
            StatTile(
                label = "今日发现",
                value = "0",
                numericValue = stats.discovered,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "今日投递",
                value = "0",
                numericValue = stats.delivered,
                accent = colors.secondary,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "今日回复",
                value = "0",
                numericValue = stats.replied,
                accent = successColor,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BossClawSpacing.xs)) {
            StatTile(
                label = "待确认",
                value = "0",
                numericValue = funnel.matched,
                accent = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "已约面",
                value = "0",
                numericValue = funnel.interview,
                accent = colors.secondary,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "今日 Token",
                value = tokenLabel(stats.tokensToday),
                accent = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FunnelCard(funnel: DailyStats, onOpenRecords: () -> Unit) {
    BossClawCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "转化漏斗",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "从发现到约面的每一层掉损耗",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpenRecords) { Text("记录") }
        }
        val total = funnel.discovered.coerceAtLeast(1)
        val colors = MaterialTheme.colorScheme
        val successColor =
            if (LocalBossClawDark.current) Color(0xFF6FD69B) else BossClawColors.Success
        FunnelRow("发现", funnel.discovered, total, colors.onSurfaceVariant)
        FunnelRow("过滤", funnel.filtered, total, colors.secondary)
        FunnelRow("匹配", funnel.matched, total, colors.primary)
        FunnelRow("投递", funnel.delivered, total, colors.primary)
        FunnelRow("回复", funnel.replied, total, successColor)
        FunnelRow("约面", funnel.interview, total, colors.secondary)
    }
}

@Composable
private fun FunnelRow(label: String, value: Int, total: Int, color: Color) {
    val target = (value.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(BossClawMotion.SLOW, easing = BossClawMotion.EmphasizedDecel),
        label = "funnel-row"
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = BossClawSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = BossClawSpacing.xs)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(7.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceAtLeast(0.001f))
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(start = BossClawSpacing.xs)
        )
    }
}

@Composable
private fun RecentActivity(logs: List<LogLine>, onOpenRecords: () -> Unit) {
    BossClawCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "最近动态",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onOpenRecords) { Text("全部") }
        }
        val recent = logs.takeLast(6).reversed()
        if (recent.isEmpty()) {
            EmptyState(
                title = "还没有运行记录",
                message = "开启巡查后，探路、评估与沟通的每一步都会记录在这里"
            )
        } else {
            recent.forEach { line ->
                CompactLogRow(
                    source = line.source,
                    message = line.message,
                    level = line.level,
                    timestamp = line.timestamp
                )
            }
            Text(
                text = "最后更新 ${TimeUtils.formatTime(recent.first().timestamp)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = BossClawSpacing.xxs)
            )
        }
    }
}
