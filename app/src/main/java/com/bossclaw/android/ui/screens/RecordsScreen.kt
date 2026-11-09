package com.bossclaw.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bossclaw.android.core.TimeUtils
import com.bossclaw.android.data.db.LogEntity
import com.bossclaw.android.ui.BossClawViewModel
import com.bossclaw.android.ui.components.BossClawCard
import com.bossclaw.android.ui.components.EmptyState
import com.bossclaw.android.ui.components.ScreenHeader
import com.bossclaw.android.ui.components.StatTile
import com.bossclaw.android.ui.components.levelColor
import com.bossclaw.android.ui.components.tokenLabel
import com.bossclaw.android.ui.theme.BossClawSpacing

@Composable
fun RecordsScreen(viewModel: BossClawViewModel) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val funnel by viewModel.funnel.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = BossClawSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(BossClawSpacing.sm)
    ) {
        item {
            ScreenHeader(
                title = "运行记录",
                subtitle = "最近 300 条 · 仅保存在本机"
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(BossClawSpacing.xs)) {
                StatTile(
                    label = "累计发现",
                    value = funnel.discovered.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "累计投递",
                    value = funnel.delivered.toString(),
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "今日 Token",
                    value = tokenLabel(stats.tokensToday),
                    accent = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (logs.isEmpty()) {
            item {
                BossClawCard {
                    EmptyState(
                        title = "还没有日志",
                        message = "开启巡查后，每一步都会记录在这里"
                    )
                }
            }
        } else {
            items(logs, key = { it.id }, contentType = { it.level }) { log -> LogRow(log) }
        }
    }
}

@Composable
private fun LogRow(log: LogEntity) {
    BossClawCard(padding = BossClawSpacing.md) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .padding(top = BossClawSpacing.xxs)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(levelColor(log.level))
            )
            Row(
                modifier = Modifier.weight(1f).padding(start = BossClawSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "${log.source} · ${log.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = TimeUtils.formatDateTime(log.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(start = BossClawSpacing.xs)
                )
            }
        }
    }
}
