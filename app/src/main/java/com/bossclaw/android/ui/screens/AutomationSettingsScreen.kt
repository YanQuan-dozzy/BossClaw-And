package com.bossclaw.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bossclaw.android.domain.HrActivity
import com.bossclaw.android.model.RunMode
import com.bossclaw.android.model.Speed
import com.bossclaw.android.ui.BossClawViewModel
import com.bossclaw.android.ui.components.BossClawCard
import com.bossclaw.android.ui.components.ScreenHeader
import com.bossclaw.android.ui.components.SettingSwitchRow
import com.bossclaw.android.ui.components.tokenLabel
import com.bossclaw.android.ui.theme.BossClawColors
import kotlin.math.roundToInt

@Composable
fun AutomationSettingsScreen(viewModel: BossClawViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader(
            title = "自动化边界",
            subtitle = "先划定边界，再交给自动化"
        )

        BossClawCard {
            Text(
                text = "运行模式",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            RunMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = settings.runMode == mode,
                        onClick = { viewModel.updateSettings { it.copy(runMode = mode) } }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = mode.hint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = "节奏",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Speed.entries.forEach { speed ->
                    FilterChip(
                        selected = settings.speed == speed,
                        onClick = { viewModel.updateSettings { it.copy(speed = speed) } },
                        label = { Text(speed.label) }
                    )
                }
            }
        }

        BossClawCard {
            Text(
                text = "自动化边界",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            SettingSwitchRow(
                title = "自动回复",
                subtitle = "仅对安全可控的会话生成简短回复",
                checked = settings.autoReply,
                onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(autoReply = enabled) } }
            )
            SettingSwitchRow(
                title = "悬浮控制台",
                subtitle = "在 BOSS 上方显示状态与急停按钮",
                checked = settings.floatingHud,
                onCheckedChange = { viewModel.setHudEnabled(it) }
            )
            SettingSwitchRow(
                title = "系统通知",
                subtitle = "首单发送、HR 邀约与风控提醒",
                checked = settings.notifications,
                onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(notifications = enabled) } }
            )
        }

        BossClawCard {
            Text(
                text = "沟通信息（AI 参考）",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "填写你愿意提供给 HR 的真实信息，AI 跟聊与手动回复都会引用；留空则不承诺这类内容",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            OutlinedTextField(
                value = settings.communicationInfo,
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(communicationInfo = value) }
                },
                placeholder = {
                    Text(
                        "例：\n薪资期望：20K 左右，可面议\n可面试时间：本周工作日晚上或周末\n可到岗时间：一个月内"
                    )
                },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            Text(
                text = "只写真实信息：AI 不会补充未填写的薪资、到岗或面试安排",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        BossClawCard {
            Text(
                text = "筛选偏好",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            SettingSwitchRow(
                title = "过滤外包",
                subtitle = "跳过人力外包与驻场岗位",
                checked = settings.filterOutsourcing,
                onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(filterOutsourcing = enabled) } }
            )
            SettingSwitchRow(
                title = "过滤猎头",
                subtitle = "跳过猎头与代招岗位",
                checked = settings.filterHeadhunter,
                onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(filterHeadhunter = enabled) } }
            )
            Text(
                text = "HR 活跃度",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HrActivity.OPTIONS) { (value, label) ->
                    FilterChip(
                        selected = settings.hrActivityFilter == value,
                        onClick = { viewModel.updateSettings { it.copy(hrActivityFilter = value) } },
                        label = { Text(label) }
                    )
                }
            }
            SettingSwitchRow(
                title = "城市匹配",
                subtitle = "只投递画像中的目标城市（未识别城市不误杀）",
                checked = settings.filterCity,
                onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(filterCity = enabled) } }
            )
            SettingInput(
                label = "岗位描述排除关键字",
                hint = "如：出差、驻场、长期外派（用、分隔）",
                value = settings.excludedJobKeywordsCsv,
                onValueChange = { value -> viewModel.updateSettings { it.copy(excludedJobKeywordsCsv = value) } }
            )
            SettingInput(
                label = "招聘方黑名单",
                hint = "如：王老师、李经理（用、分隔，命中即跳过）",
                value = settings.excludedRecruitersCsv,
                onValueChange = { value -> viewModel.updateSettings { it.copy(excludedRecruitersCsv = value) } }
            )
            SettingInput(
                label = "排除城市",
                hint = "如：北京、上海（用、分隔，命中即跳过）",
                value = settings.excludedCitiesCsv,
                onValueChange = { value -> viewModel.updateSettings { it.copy(excludedCitiesCsv = value) } }
            )
        }

        BossClawCard {
            Text(
                text = "额度与预算",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            SliderRow(
                title = "每日投递上限",
                valueText = "${settings.maxDailyGreetings} 次（硬上限 150）",
                value = settings.maxDailyGreetings.toFloat(),
                range = 10f..150f,
                steps = 27
            ) { raw ->
                val rounded = (raw / 5f).roundToInt() * 5
                viewModel.updateSettings { it.copy(maxDailyGreetings = rounded.coerceIn(10, 150)) }
            }
            SliderRow(
                title = "期望薪资下限",
                valueText = if (settings.minSalaryK <= 0) "不限" else "${settings.minSalaryK}K",
                value = settings.minSalaryK.toFloat(),
                range = 0f..80f,
                steps = 15
            ) { raw ->
                val rounded = (raw / 5f).roundToInt() * 5
                viewModel.updateSettings { it.copy(minSalaryK = rounded.coerceIn(0, 80)) }
            }
            SliderRow(
                title = "每日 Token 预算",
                valueText = tokenLabel(settings.dailyTokenBudget),
                value = settings.dailyTokenBudget.toFloat(),
                range = 50_000f..1_000_000f,
                steps = 18
            ) { raw ->
                val rounded = (raw / 50_000f).roundToInt() * 50_000
                viewModel.updateSettings { it.copy(dailyTokenBudget = rounded.coerceIn(50_000, 1_000_000)) }
            }
        }

        Text(
            text = "边界规则优先于模型判断：命中黑名单、城市或关键词时会直接跳过，不会交给模型解释",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingInput(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(hint) },
        minLines = 1,
        maxLines = 3,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = BossClawColors.Teal
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}
