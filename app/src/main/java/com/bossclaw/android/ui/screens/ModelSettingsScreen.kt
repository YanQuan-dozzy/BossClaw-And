package com.bossclaw.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bossclaw.android.llm.ContextBudget
import com.bossclaw.android.llm.ContextUsage
import com.bossclaw.android.llm.LlmConfig
import com.bossclaw.android.llm.LlmPresets
import com.bossclaw.android.llm.ThinkingMode
import com.bossclaw.android.llm.ThinkingProfiles
import com.bossclaw.android.ui.BossClawViewModel
import com.bossclaw.android.ui.components.BossClawCard
import com.bossclaw.android.ui.components.ScreenHeader
import com.bossclaw.android.ui.theme.BossClawColors

@Composable
fun ModelSettingsScreen(viewModel: BossClawViewModel) {
    val testing by viewModel.testing.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader(
            title = "模型服务",
            subtitle = "兼容 OpenAI 协议与 Anthropic Messages"
        )
        ModelCard(
            initial = viewModel.llmConfig(),
            testing = testing,
            onSave = { config -> viewModel.saveLlmConfig(config) },
            onTest = { config -> viewModel.testConnection(config) }
        )
        Text(
            text = "接口密钥使用 Android Keystore 加密后存于本机；测试连接只发送一条极短请求",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModelCard(
    initial: LlmConfig,
    testing: Boolean,
    onSave: (LlmConfig) -> Unit,
    onTest: (LlmConfig) -> Unit
) {
    var provider by remember { mutableStateOf(initial.provider) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }
    var protocol by remember { mutableStateOf(initial.protocol) }
    var contextWindow by remember { mutableStateOf(ContextBudget.normalizeWindow(initial.contextWindowTokens)) }
    var contextUsage by remember { mutableStateOf(ContextUsage.from(initial.contextUsage)) }
    var thinkingEnabled by remember { mutableStateOf(initial.thinkingEnabled) }
    var thinkingEffort by remember { mutableStateOf(initial.thinkingEffort) }

    fun currentConfig(): LlmConfig = initial.copy(
        provider = provider,
        protocol = protocol,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        contextWindowTokens = contextWindow,
        contextUsage = contextUsage.raw,
        thinkingEnabled = thinkingEnabled,
        thinkingEffort = thinkingEffort
    )

    val config = currentConfig()
    val thinking = ThinkingProfiles.resolve(config)
    val budget = ContextBudget.of(config)

    BossClawCard {
        Text(
            text = "服务配置",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "选择预设后仍可手动修改地址与模型名",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp)
        ) {
            items(LlmPresets.all) { preset ->
                FilterChip(
                    selected = provider == preset.id,
                    onClick = {
                        provider = preset.id
                        protocol = preset.protocol
                        if (preset.baseUrl.isNotBlank()) baseUrl = preset.baseUrl
                        if (preset.model.isNotBlank()) model = preset.model
                        // 换服务商后思考能力随之变化，先回到关闭状态，避免把旧档位带到不支持的模型
                        thinkingEnabled = false
                        thinkingEffort = ""
                    },
                    label = { Text(preset.name) }
                )
            }
        }

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("接口地址") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("模型名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "思考强度",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = thinking.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Switch(
                checked = thinking.isActive(thinkingEnabled),
                onCheckedChange = { thinkingEnabled = it },
                enabled = thinking.mode == ThinkingMode.TOGGLEABLE
            )
        }
        if (thinking.supported && thinking.isActive(thinkingEnabled) && thinking.efforts.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                items(thinking.efforts) { effort ->
                    FilterChip(
                        selected = thinking.resolveEffort(thinkingEffort) == effort,
                        onClick = { thinkingEffort = effort },
                        label = { Text(thinking.effortLabel(effort)) }
                    )
                }
            }
        }

        Text(
            text = "上下文窗口",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "按模型实际窗口选择；填大超出窗口会请求失败，填小会截断长简历",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            items(ContextBudget.WINDOW_PRESETS) { (value, label) ->
                FilterChip(
                    selected = contextWindow == value,
                    onClick = { contextWindow = value },
                    label = { Text(label) }
                )
            }
        }
        Text(
            text = "上下文用量",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 14.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            ContextUsage.entries.forEach { usage ->
                FilterChip(
                    selected = contextUsage == usage,
                    onClick = { contextUsage = usage },
                    label = { Text(usage.label) }
                )
            }
        }
        Text(
            text = budget.note,
            style = MaterialTheme.typography.labelSmall,
            color = BossClawColors.Teal,
            modifier = Modifier.padding(top = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onTest(currentConfig()) },
                enabled = !testing,
                modifier = Modifier.weight(1f)
            ) {
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Text("测试连接")
                }
            }
            Button(
                onClick = {
                    // 不支持的模型不落库思考开状态，避免换模型后沿用无效配置
                    val cfg = currentConfig()
                    onSave(
                        if (thinking.supported) cfg
                        else cfg.copy(thinkingEnabled = false, thinkingEffort = "")
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("保存配置")
            }
        }
        Text(
            text = "当前模型：${model.ifBlank { "未填写" }} · Key ${initial.maskedKey()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
