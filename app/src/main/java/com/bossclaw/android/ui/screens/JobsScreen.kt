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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.bossclaw.android.ui.theme.BossClawColors

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
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = "岗位",
                subtitle = "${settings.runMode.label} · 共 ${jobs.size} 条，当前 ${visible.size} 条"
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(JobFilter.entries.toList()) { entry ->
                    FilterChip(
                        selected = filter == entry,
                        onClick = { filter = entry },
                        label = { Text(entry.label) }
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
        items(visible, key = { it.jobId }) { job ->
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
                modifier = Modifier.padding(vertical = 12.dp)
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
                    modifier = Modifier.padding(top = 3.dp)
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
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                FitChip(job.fit())
                Text(
                    text = "${job.score} 分",
                    style = MaterialTheme.typography.labelLarge,
                    color = fitColor(job.fit()),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (job.reason.isNotBlank()) {
            Text(
                text = job.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        val highlights = job.highlights()
        if (highlights.isNotEmpty()) {
            Text(
                text = highlights.take(3).joinToString(" · ") { "✓ $it" },
                style = MaterialTheme.typography.labelSmall,
                color = BossClawColors.Success,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        val risks = job.risks()
        if (risks.isNotEmpty()) {
            Text(
                text = risks.take(2).joinToString(" · ") { "! $it" },
                style = MaterialTheme.typography.labelSmall,
                color = BossClawColors.Danger,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (job.greeting.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "开场白草稿",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = BossClawColors.TealDeep
                    )
                    Text(
                        text = job.greeting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(text = job.statusLabel(), color = statusColor(job))
            Text(
                text = job.hrActive.ifBlank { "HR 活跃度未知" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
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
                TextButton(onClick = onCopy) { Text("复制") }
            }
            TextButton(onClick = onIgnore) { Text("忽略") }
            TextButton(
                onClick = onBlacklist,
                colors = ButtonDefaults.textButtonColors(contentColor = BossClawColors.Danger)
            ) { Text("拉黑") }
        }
    }
}

private fun statusColor(job: JobEntity): Color = when {
    job.status == JobEntity.STATUS_INTERVIEW -> BossClawColors.Amber
    job.status == JobEntity.STATUS_REPLIED -> BossClawColors.Success
    job.status == JobEntity.STATUS_DELIVERED -> BossClawColors.Teal
    job.status == JobEntity.STATUS_FILTERED || job.status == JobEntity.STATUS_CLOSED -> BossClawColors.Muted
    else -> fitColor(job.fit())
}
