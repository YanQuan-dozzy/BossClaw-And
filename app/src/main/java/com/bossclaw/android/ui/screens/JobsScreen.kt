package com.bossclaw.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bossclaw.android.data.db.JobEntity
import com.bossclaw.android.model.fit
import com.bossclaw.android.model.highlights
import com.bossclaw.android.model.risks
import com.bossclaw.android.model.statusLabel
import com.bossclaw.android.ui.BossClawViewModel
import com.bossclaw.android.ui.components.BossClawCard
import com.bossclaw.android.ui.components.EmptyState
import com.bossclaw.android.ui.components.FitChip
import com.bossclaw.android.ui.components.ScreenHeader
import com.bossclaw.android.ui.components.StatusPill
import com.bossclaw.android.ui.components.fitColor
import com.bossclaw.android.ui.components.soft.SoftChip
import com.bossclaw.android.ui.theme.BossClawColors
import com.bossclaw.android.ui.theme.BossClawRadius
import com.bossclaw.android.ui.theme.BossClawSpacing
import com.bossclaw.android.ui.theme.LocalBossClawDark

private enum class JobFilter(val label: String) {
    ALL("全部"),
    WAITING("待确认"),
    DELIVERED("已投递"),
    CHATTING("沟通中"),
    ENDED("已结束");

    fun match(job: JobEntity): Boolean = when (this) {
        ALL -> true
        WAITING -> job.status == JobEntity.STATUS_MATCHED || job.status == JobEntity.STATUS_APPROVED
        DELIVERED -> job.status == JobEntity.STATUS_DELIVERED || job.status == JobEntity.STATUS_DELIVERING
        CHATTING -> job.status == JobEntity.STATUS_REPLIED ||
            job.status == JobEntity.STATUS_INTERVIEW ||
            job.status == JobEntity.STATUS_REJECTED
        ENDED -> job.status == JobEntity.STATUS_FILTERED ||
            job.status == JobEntity.STATUS_CLOSED ||
            job.status == JobEntity.STATUS_FAILED
    }
}

@Composable
fun JobsScreen(viewModel: BossClawViewModel) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val busyJobId by viewModel.busyJobId.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var filter by remember { mutableStateOf(JobFilter.ALL) }

    val visible = remember(jobs, filter) { jobs.filter { filter.match(it) } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = BossClawSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(BossClawSpacing.sm)
    ) {
        item {
            ScreenHeader(
                title = "岗位",
                subtitle = "${settings.runMode.label} · 共 ${jobs.size} 条，当前 ${visible.size} 条"
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(BossClawSpacing.xs)) {
                items(JobFilter.entries.toList()) { entry ->
                    SoftChip(
                        text = entry.label,
                        selected = filter == entry,
                        onClick = { filter = entry }
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            item {
                BossClawCard {
                    EmptyState(
                        title = "暂无岗位",
                        message = "开启巡查后，符合方向的岗位会出现在这里；也可以先在 BOSS 中浏览，再回到本页查看评估"
                    )
                }
            }
        }
        items(visible, key = { it.jobId }, contentType = { it.status }) { job ->
            JobCard(
                job = job,
                busy = busyJobId == job.jobId,
                onGenerate = { viewModel.generateGreeting(job.jobId) },
                onCopy = { clipboard.setText(AnnotatedString(job.greeting)) },
                onIgnore = { viewModel.ignoreJob(job) },
                onBlacklist = { viewModel.blacklistJob(job) }
            )
        }
        item {
            Text(
                text = "提示：开场白超过 250 字会被平台限制，BossClaw 已自动压缩并校验事实边界",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = BossClawSpacing.sm)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobCard(
    job: JobEntity,
    busy: Boolean,
    onGenerate: () -> Unit,
    onCopy: () -> Unit,
    onIgnore: () -> Unit,
    onBlacklist: () -> Unit
) {
    val dark = LocalBossClawDark.current
    val colors = MaterialTheme.colorScheme
    BossClawCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${job.company} · ${job.city.ifBlank { "城市未知" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = BossClawSpacing.xxs)
                )
                val meta = listOfNotNull(
                    job.salaryText.takeIf { it.isNotBlank() },
                    job.experience.takeIf { it.isNotBlank() },
                    job.degree.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = BossClawSpacing.xxs)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = BossClawSpacing.xs)) {
                FitChip(job.fit())
                Text(
                    text = "${job.score} 分",
                    style = MaterialTheme.typography.labelLarge,
                    color = fitColor(job.fit()),
                    modifier = Modifier.padding(top = BossClawSpacing.xxs)
                )
            }
        }

        if (job.reason.isNotBlank()) {
            Text(
                text = job.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                modifier = Modifier.padding(top = BossClawSpacing.sm)
            )
        }

        val highlights = job.highlights()
        if (highlights.isNotEmpty()) {
            Text(
                text = highlights.take(3).joinToString(" · ") { "✓ $it" },
                style = MaterialTheme.typography.labelSmall,
                color = if (dark) Color(0xFF6FD69B) else BossClawColors.Success,
                modifier = Modifier.padding(top = BossClawSpacing.xs)
            )
        }
        val risks = job.risks()
        if (risks.isNotEmpty()) {
            Text(
                text = risks.take(2).joinToString(" · ") { "! $it" },
                style = MaterialTheme.typography.labelSmall,
                color = if (dark) Color(0xFFE58C82) else BossClawColors.Danger,
                modifier = Modifier.padding(top = BossClawSpacing.xxs)
            )
        }

        if (job.greeting.isNotBlank()) {
            Surface(
                color = colors.primaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(BossClawRadius.control),
                modifier = Modifier.fillMaxWidth().padding(top = BossClawSpacing.sm)
            ) {
                Column(modifier = Modifier.padding(BossClawSpacing.sm)) {
                    Text(
                        text = "开场白草稿",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = colors.onPrimaryContainer
                    )
                    Text(
                        text = job.greeting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                        modifier = Modifier.padding(top = BossClawSpacing.xxs)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = BossClawSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(text = job.statusLabel(), color = statusColor(job, dark))
            Text(
                text = job.hrActive.ifBlank { "HR 活跃度未知" },
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = BossClawSpacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(BossClawSpacing.xxs),
            verticalArrangement = Arrangement.spacedBy(BossClawSpacing.xxs)
        ) {
            TextButton(onClick = onGenerate, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(if (job.greeting.isBlank()) "生成开场白" else "重新生成")
            }
            if (job.greeting.isNotBlank()) {
                TextButton(
                    onClick = onCopy,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurfaceVariant)
                ) { Text("复制") }
            }
            TextButton(
                onClick = onIgnore,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurfaceVariant)
            ) { Text("忽略") }
            TextButton(
                onClick = onBlacklist,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.error)
            ) { Text("拉黑") }
        }
    }
}

private fun statusColor(job: JobEntity, dark: Boolean): Color = when {
    job.status == JobEntity.STATUS_INTERVIEW ->
        if (dark) Color(0xFFE8B45C) else BossClawColors.Amber
    job.status == JobEntity.STATUS_REPLIED ->
        if (dark) Color(0xFF6FD69B) else BossClawColors.Success
    job.status == JobEntity.STATUS_DELIVERED ->
        if (dark) Color(0xFF5FD1C8) else BossClawColors.Teal
    job.status == JobEntity.STATUS_FILTERED || job.status == JobEntity.STATUS_CLOSED ->
        if (dark) Color(0xFF93A39D) else BossClawColors.Muted
    else -> fitColor(job.fit())
}
