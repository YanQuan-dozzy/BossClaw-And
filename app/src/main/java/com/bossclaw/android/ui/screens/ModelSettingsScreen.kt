package com.bossclaw.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bossclaw.android.llm.ContextBudget
import com.bossclaw.android.llm.ContextUsage
import com.bossclaw.android.llm.LlmConfig
import com.bossclaw.android.llm.LlmPresets
import com.bossclaw.android.llm.ThinkingMode
import com.bossclaw.android.llm.ThinkingProfiles
import com.bossclaw.android.ui.BossClawViewModel
import com.bossclaw.android.ui.components.BossClawCard
import com.bossclaw.android.ui.components.BossClawDropdown
import com.bossclaw.android.ui.components.BossClawNumberField
import com.bossclaw.android.ui.components.ScreenHeader
import com.bossclaw.android.ui.components.SettingSwitchRow
import com.bossclaw.android.ui.components.soft.SoftButton
import com.bossclaw.android.ui.components.soft.SoftButtonVariant
import com.bossclaw.android.ui.components.soft.SoftTextField
import com.bossclaw.android.ui.theme.BossClawSpacing

/** 窗口下拉里的「自定义」哨兵值，不对应真实 token 数。 */
private const val CUSTOM_WINDOW = "custom"

@Composable
fun ModelSettingsScreen(viewModel: BossClawViewModel) {
    val testing by viewModel.testing.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = BossClawSpacing.screen,
                end = BossClawSpacing.screen,
                top = BossClawSpacing.xxs,
                bottom = BossClawSpacing.md
            ),
        verticalArrangement = Arrangement.spacedBy(BossClawSpacing.md)
    ) {
        ScreenHeader(
            title = "模型服务"
        )
        ModelCard(
            initial = viewModel.llmConfig(),
            testing = testing,
            onSave = { config -> viewModel.saveLlmConfig(config) },
            onTest = { config -> viewModel.testConnection(config) }
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
    // 窗口不在预设档时进入自定义态，允许直接填入模型实际窗口
    var customWindow by remember {
        mutableStateOf(ContextBudget.WINDOW_PRESETS.none { it.first == contextWindow })
    }

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

        BossClawDropdown(
            label = "服务商预设",
            selectedValue = provider,
            options = LlmPresets.all.map { it.id to it.name },
            onSelect = { id ->
                val preset = LlmPresets.find(id)
                provider = preset.id
                protocol = preset.protocol
                if (preset.baseUrl.isNotBlank()) baseUrl = preset.baseUrl
                if (preset.model.isNotBlank()) model = preset.model
                // 换服务商后思考能力随之变化，先回到关闭状态，避免把旧档位带到不支持的模型
                thinkingEnabled = false
                thinkingEffort = ""
            },
            modifier = Modifier.padding(top = BossClawSpacing.sm)
        )

        SoftTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = "接口地址",
            singleLine = true,
            modifier = Modifier.padding(top = BossClawSpacing.sm)
        )
        SoftTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = "API Key",
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.padding(top = BossClawSpacing.xs)
        )
        SoftTextField(
            value = model,
            onValueChange = { model = it },
            label = "模型名称",
            singleLine = true,
            modifier = Modifier.padding(top = BossClawSpacing.xs)
        )

        SettingSwitchRow(
            title = "思考强度",
            subtitle = thinking.note,
            checked = thinking.isActive(thinkingEnabled),
            onCheckedChange = { thinkingEnabled = it },
            enabled = thinking.mode == ThinkingMode.TOGGLEABLE
        )
        if (thinking.supported && thinking.isActive(thinkingEnabled) && thinking.efforts.isNotEmpty()) {
            BossClawDropdown(
                label = "思考强度",
                selectedValue = thinking.resolveEffort(thinkingEffort),
                options = thinking.efforts.map { it to thinking.effortLabel(it) },
                onSelect = { thinkingEffort = it },
                modifier = Modifier.padding(top = BossClawSpacing.sm)
            )
        }

        Text(
            text = "上下文窗口",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = BossClawSpacing.md)
        )
        BossClawDropdown(
            label = "窗口大小",
            selectedValue = if (customWindow) CUSTOM_WINDOW else contextWindow.toString(),
            options = ContextBudget.WINDOW_PRESETS.map { it.first.toString() to it.second } +
                (CUSTOM_WINDOW to "自定义…"),
            hint = "填大超出模型窗口会请求失败",
            onSelect = { raw ->
                if (raw == CUSTOM_WINDOW) {
                    customWindow = true
                } else {
                    customWindow = false
                    contextWindow = raw.toIntOrNull() ?: ContextBudget.DEFAULT_WINDOW
                }
            },
            modifier = Modifier.padding(top = BossClawSpacing.xs)
        )
        if (customWindow) {
            // 输入与展示统一按 K 为单位，落库时再换算成 token 数，避免出现 128000 这类长数字
            BossClawNumberField(
                value = contextWindow / 1_000,
                onValueChange = { contextWindow = ContextBudget.normalizeWindow(it * 1_000) },
                label = "自定义窗口（K tokens）",
                hint = "范围 ${ContextBudget.MIN_WINDOW / 1_000}K–${ContextBudget.MAX_WINDOW / 1_000}K",
                minValue = ContextBudget.MIN_WINDOW / 1_000,
                maxValue = ContextBudget.MAX_WINDOW / 1_000,
                placeholder = "如 128",
                modifier = Modifier.padding(top = BossClawSpacing.xs)
            )
        }
        Text(
            text = "上下文用量",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = BossClawSpacing.md)
        )
        BossClawDropdown(
            label = "用量档位",
            selectedValue = contextUsage.raw,
            options = ContextUsage.entries.map { it.raw to it.label },
            onSelect = { contextUsage = ContextUsage.from(it) },
            modifier = Modifier.padding(top = BossClawSpacing.xs)
        )
        Text(
            text = budget.note,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = BossClawSpacing.xs)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = BossClawSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(BossClawSpacing.xs)
        ) {
            SoftButton(
                text = "测试连接",
                onClick = { onTest(currentConfig()) },
                enabled = !testing,
                loading = testing,
                variant = SoftButtonVariant.Secondary,
                modifier = Modifier.weight(1f)
            )
            SoftButton(
                text = "保存配置",
                onClick = {
                    // 不支持的模型不落库思考开状态，避免换模型后沿用无效配置
                    val cfg = currentConfig()
                    onSave(
                        if (thinking.supported) cfg
                        else cfg.copy(thinkingEnabled = false, thinkingEffort = "")
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "当前模型：${model.ifBlank { "未填写" }} · Key ${initial.maskedKey()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = BossClawSpacing.xs)
        )
    }
}
