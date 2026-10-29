package com.bossclaw.android.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bossclaw.android.core.TimeUtils
import com.bossclaw.android.data.db.ChatMessageEntity
import com.bossclaw.android.data.db.JobEntity
import com.bossclaw.android.ui.BossClawViewModel
import com.bossclaw.android.ui.components.BossClawCard
import com.bossclaw.android.ui.components.EmptyState
import com.bossclaw.android.ui.components.ScreenHeader
import com.bossclaw.android.ui.theme.BossClawColors

@Composable
fun ChatScreen(viewModel: BossClawViewModel) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    var selectedJobId by remember { mutableStateOf<String?>(null) }

    val jobMap = remember(jobs) { jobs.associateBy { it.jobId } }
    val conversations = remember(messages) {
        messages.groupBy { it.jobId }
            .map { (jobId, msgs) -> jobId to msgs.sortedBy { it.timestamp } }
            .sortedByDescending { it.second.lastOrNull()?.timestamp ?: 0L }
    }

    val selected = selectedJobId
    if (selected == null) {
        ConversationList(
            conversations = conversations,
            jobMap = jobMap,
            onOpen = { selectedJobId = it },
            onScan = viewModel::runInboxOnce
        )
    } else {
        ConversationDetail(
            viewModel = viewModel,
            jobId = selected,
            job = jobMap[selected],
            messages = conversations.firstOrNull { it.first == selected }?.second.orEmpty(),
            onBack = { selectedJobId = null },
            onScan = viewModel::runInboxOnce
        )
    }
}

@Composable
private fun ConversationList(
    conversations: List<Pair<String, List<ChatMessageEntity>>>,
    jobMap: Map<String, JobEntity>,
    onOpen: (String) -> Unit,
    onScan: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader(
            title = "沟通",
            subtitle = "${conversations.size} 个会话 · 自动回复仅处理安全可控的对话"
        ) {
            TextButton(onClick = onScan) {
                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("扫描消息", modifier = Modifier.padding(start = 4.dp))
            }
        }
        if (conversations.isEmpty()) {
            BossClawCard(modifier = Modifier.padding(top = 14.dp)) {
                EmptyState(
                    title = "暂无会话",
                    message = "投递成功后，HR 的回复会在这里汇总；也可以点击右上角立即扫描 BOSS 消息"
                )
            }
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(conversations, key = { it.first }) { (jobId, msgs) ->
                val job = jobMap[jobId]
                val last = msgs.lastOrNull()
                BossClawCard(padding = 14.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = job?.let { "${it.company} · ${it.title}" } ?: "未知岗位",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (last != null) {
                                Text(
                                    text = if (last.role == ChatMessageEntity.ROLE_ME) "我：${last.text}" else last.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                    maxLines = 2
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = last?.let { TimeUtils.relative(it.timestamp) } ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (msgs.size > 1) {
                                Surface(
                                    color = BossClawColors.Teal.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(top = 6.dp)
                                ) {
                                    Text(
                                        text = "${msgs.size} 条",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BossClawColors.Teal,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = { onOpen(jobId) },
                        modifier = Modifier.padding(top = 4.dp)
                    ) { Text("查看对话") }
                }
            }
        }
    }
}

@Composable
private fun ConversationDetail(
    viewModel: BossClawViewModel,
    jobId: String,
    job: JobEntity?,
    messages: List<ChatMessageEntity>,
    onBack: () -> Unit,
    onScan: () -> Unit
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val draftState by viewModel.replyDraft.collectAsStateWithLifecycle()
    val sending by viewModel.sendingReply.collectAsStateWithLifecycle()
    var input by remember(jobId) { mutableStateOf("") }

    // AI 草稿只填入输入框，发送仍由用户点击确认
    LaunchedEffect(draftState) {
        val state = draftState
        if (state != null && state.jobId == jobId && !state.loading && state.text.isNotBlank()) {
            input = state.text
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = job?.title ?: "会话 $jobId",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = job?.let { "${it.company} · ${statusText(it.status)}" } ?: "本地记录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onScan) { Text("同步") }
        }

        if (job != null && job.reason.isNotBlank()) {
            BossClawCard(modifier = Modifier.padding(top = 8.dp), padding = 12.dp) {
                Text(
                    text = job.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message -> Bubble(message) }
            item {
                Text(
                    text = "本地保存的会话记录，仅用于生成回复与复盘",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                )
            }
        }

        ReplyComposer(
            draftLoading = draftState?.loading == true && draftState?.jobId == jobId,
            draftError = draftState?.takeIf { it.jobId == jobId }?.error,
            value = input,
            onValueChange = { input = it },
            sending = sending,
            canSend = job != null && input.isNotBlank(),
            onGenerate = { viewModel.generateReplyDraft(jobId) },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("回复草稿", input))
                viewModel.notify("回复已复制到剪贴板")
            },
            onSend = { viewModel.sendChatReply(jobId, input) { input = "" } }
        )
    }
}

/** 会话底部输入框：AI 只负责生成草稿，发送由用户点击确认。 */
@Composable
private fun ReplyComposer(
    draftLoading: Boolean,
    draftError: String?,
    value: String,
    onValueChange: (String) -> Unit,
    sending: Boolean,
    canSend: Boolean,
    onGenerate: () -> Unit,
    onCopy: () -> Unit,
    onSend: () -> Unit
) {
    BossClawCard(modifier = Modifier.padding(top = 6.dp), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "回复",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (draftLoading) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    text = "AI 正在写草稿",
                    style = MaterialTheme.typography.labelSmall,
                    color = BossClawColors.Teal,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        if (draftError != null) {
            Text(
                text = draftError,
                style = MaterialTheme.typography.labelSmall,
                color = BossClawColors.Danger,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("输入回复，或点「AI 帮我回复」生成草稿") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onGenerate, enabled = !draftLoading && !sending) {
                Text("AI 帮我回复")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCopy, enabled = value.isNotBlank()) { Text("复制") }
                Button(
                    onClick = onSend,
                    enabled = canSend && !sending,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    if (sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("发送")
                    }
                }
            }
        }
        Text(
            text = "发送前会先在 BOSS 消息列表定位该岗位的会话；找不到即中止，不会发给其他人",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun Bubble(message: ChatMessageEntity) {
    if (message.role == ChatMessageEntity.ROLE_SYSTEM) {
        Text(
            text = message.text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
        )
        return
    }
    val mine = message.role == ChatMessageEntity.ROLE_ME
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        if (!mine) Avatar("HR")
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (mine) 14.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 14.dp
            ),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(start = if (!mine) 8.dp else 0.dp, end = if (mine) 8.dp else 0.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = TimeUtils.formatTime(message.timestamp) + if (!message.delivered) " · 未送达" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        if (mine) Avatar("我")
    }
}

@Composable
private fun Avatar(label: String) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(if (label == "我") BossClawColors.Teal else BossClawColors.Amber, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = androidx.compose.ui.graphics.Color.White
        )
    }
}

private fun statusText(status: String): String = when (status) {
    JobEntity.STATUS_DELIVERED -> "已投递"
    JobEntity.STATUS_REPLIED -> "已回复"
    JobEntity.STATUS_INTERVIEW -> "已约面"
    JobEntity.STATUS_REJECTED -> "已婉拒"
    JobEntity.STATUS_CLOSED -> "已结束"
    else -> "沟通中"
}
