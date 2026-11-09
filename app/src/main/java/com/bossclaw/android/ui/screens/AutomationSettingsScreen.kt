package com.bossclaw.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bossclaw.android.data.pref.AppSettings
import com.bossclaw.android.domain.HrActivity
import com.bossclaw.android.domain.JobFilter
import com.bossclaw.android.model.RunMode
import com.bossclaw.android.ui.BossClawViewModel
import com.bossclaw.android.ui.components.BossClawCard
import com.bossclaw.android.ui.components.BossClawDropdown
import com.bossclaw.android.ui.components.BossClawNumberField
import com.bossclaw.android.ui.components.BossClawSlider
import com.bossclaw.android.ui.components.ScreenHeader
import com.bossclaw.android.ui.components.SettingSwitchRow
import com.bossclaw.android.ui.components.soft.SoftTextField
import com.bossclaw.android.ui.theme.BossClawSpacing
import kotlin.math.roundToInt

@Composable
fun AutomationSettingsScreen(viewModel: BossClawViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

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
            title = "自动化边界"
        )

        BossClawCard {
            Text(
                text = "运行模式",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            RunMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = BossClawSpacing.xxs),
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

            BossClawNumberField(
                value = settings.autoSendMinScore,
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(autoSendMinScore = value) }
                },
                label = "自动运行分数（分，65–100）",
                hint = "达到该分数自动发送，低于则待确认",
                minValue = AppSettings.MIN_AUTO_SEND_SCORE,
                maxValue = AppSettings.MAX_AUTO_SEND_SCORE,
                placeholder = "如 81",
                modifier = Modifier.padding(top = BossClawSpacing.sm)
            )

            SliderRow(
                title = "岗位间隔",
                hint = "执行时自动加入随机抖动",
                value = settings.jobIntervalSeconds,
                range = 10..600,
                step = 5,
                unit = "秒/岗"
            ) { value ->
                viewModel.updateSettings { it.copy(jobIntervalSeconds = value) }
            }
        }

        BossClawCard {
            Text(
                text = "自动化边界",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            SettingSwitchRow(
                title = "自定义打招呼",
                subtitle = "开启后使用 AI 生成的开场白，关闭时使用 BOSS 官方招呼语",
                checked = settings.customGreeting,
                onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(customGreeting = enabled) } }
            )
            SettingSwitchRow(
                title = "自动回复",
                subtitle = "仅对安全可控的会话生成简短回复",
                checked = settings.autoReply,
                onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(autoReply = enabled) } }
            )
            SettingSwitchRow(
                title = "悬浮球控制台",
                subtitle = "在 BOSS 上方常驻",
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
                text = "采集范围",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            SettingSwitchRow(
                title = "无关键字匹配（BOSS 推荐流）",
                subtitle = "直接读取 BOSS「推荐」流",
                checked = settings.collectWithoutKeyword,
                onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(collectWithoutKeyword = enabled) } }
            )
        }

        BossClawCard {
            Text(
                text = "沟通信息（AI 参考）",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "只填真实信息，AI 只引用这里的内容",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = BossClawSpacing.xxs)
            )
            SoftTextField(
                value = settings.communicationInfo,
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(communicationInfo = value) }
                },
                placeholder = "例：\n薪资期望：20K 左右，可面议\n可面试时间：本周工作日晚上或周末\n可到岗时间：一个月内",
                singleLine = false,
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.padding(top = BossClawSpacing.sm)
            )
        }

        BossClawCard {
            Text(
                text = "筛选条件",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "平台内置筛选请在 BOSS 内手动设置",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = BossClawSpacing.xxs)
            )
            BossClawNumberField(
                value = settings.internshipMinMonths,
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(internshipMinMonths = value) }
                },
                label = "实习时长（个月，0=不限）",
                hint = "卡片未标注时长时不判定",
                minValue = 0,
                maxValue = 60,
                placeholder = "如 3",
                modifier = Modifier.padding(top = BossClawSpacing.sm)
            )
            BossClawNumberField(
                value = settings.weeklyMinDays,
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(weeklyMinDays = value) }
                },
                label = "每周出勤天数（0=不限）",
                hint = "1–7 天",
                minValue = 0,
                maxValue = 7,
                placeholder = "如 4",
                modifier = Modifier.padding(top = BossClawSpacing.sm)
            )
            Text(
                text = "HR 活跃度",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = BossClawSpacing.sm)
            )
            BossClawDropdown(
                label = "BOSS活跃状态",
                selectedValue = settings.hrActivityFilter,
                options = HrActivity.OPTIONS,
                onSelect = { raw ->
                    viewModel.updateSettings { it.copy(hrActivityFilter = raw) }
                },
                modifier = Modifier.padding(top = BossClawSpacing.xs)
            )
            BossClawDropdown(
                label = "学历要求",
                selectedValue = settings.degreeRequirement,
                options = JobFilter.DEGREE_OPTIONS,
                onSelect = { raw ->
                    viewModel.updateSettings { it.copy(degreeRequirement = raw) }
                },
                hint = "高于画像学历的岗位跳过",
                modifier = Modifier.padding(top = BossClawSpacing.xs)
            )
            BossClawDropdown(
                label = "经验要求",
                selectedValue = settings.experienceCeiling,
                options = JobFilter.EXPERIENCE_OPTIONS,
                onSelect = { raw ->
                    viewModel.updateSettings { it.copy(experienceCeiling = raw) }
                },
                hint = "高于所选档位的岗位跳过",
                modifier = Modifier.padding(top = BossClawSpacing.xs)
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
                text = "额度与薪资",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            SliderRow(
                title = "每日投递上限",
                hint = "上限 150 次/天",
                value = settings.maxDailyGreetings,
                range = 1..150,
                step = 1,
                unit = "次/天"
            ) { value ->
                viewModel.updateSettings { it.copy(maxDailyGreetings = value) }
            }
            SettingSwitchRow(
                title = "薪资按日薪比较",
                subtitle = "开启后按元/天比较，否则按 K/月",
                checked = settings.salaryDaily,
                onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(salaryDaily = enabled) } }
            )
            if (settings.salaryDaily) {
                SliderRow(
                    title = "最低日薪",
                    hint = "低于该值跳过，0=不限",
                    value = settings.minDailySalary,
                    range = 0..2000,
                    step = 10,
                    unit = "元/天"
                ) { value ->
                    viewModel.updateSettings { it.copy(minDailySalary = value) }
                }
            } else {
                BossClawNumberField(
                    value = settings.minSalaryK,
                    onValueChange = { value ->
                        // 数值口径与旧的档位口径互斥，填入具体值后清空档位，避免两套规则叠加
                        viewModel.updateSettings {
                            it.copy(minSalaryK = value, salaryTier = JobFilter.TIER_ANY)
                        }
                    },
                    label = "最低月薪（K/月，0=不限）",
                    hint = "0=按画像期望薪资",
                    minValue = 0,
                    maxValue = 500,
                    placeholder = "如 8",
                    modifier = Modifier.padding(top = BossClawSpacing.sm)
                )
            }
        }
    }
}

@Composable
private fun SettingInput(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    SoftTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = hint,
        singleLine = false,
        minLines = 1,
        maxLines = 3,
        modifier = Modifier.padding(top = BossClawSpacing.xs)
    )
}

/**
 * 数值滑杆行：左侧是标题与说明，右侧可直接键入具体数值；
 * 滑杆只做粗调，拖动结果按 [step] 取整，打字输入则原样生效。
 */
@Composable
private fun SliderRow(
    title: String,
    hint: String,
    value: Int,
    range: IntRange,
    step: Int,
    unit: String,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(top = BossClawSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = BossClawSpacing.sm)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = BossClawSpacing.xxs)
                )
            }
            BossClawNumberField(
                value = value,
                onValueChange = onValueChange,
                label = unit,
                minValue = range.first,
                maxValue = range.last,
                placeholder = value.toString(),
                modifier = Modifier.width(132.dp)
            )
        }
        BossClawSlider(
            value = value.coerceIn(range.first, range.last).toFloat(),
            onValueChange = { raw ->
                onValueChange(((raw / step).roundToInt() * step).coerceIn(range.first, range.last))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.padding(top = BossClawSpacing.xxs)
        )
    }
}
