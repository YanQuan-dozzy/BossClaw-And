package com.bossclaw.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bossclaw.android.ui.BossClawViewModel
import com.bossclaw.android.ui.components.BossClawCard
import com.bossclaw.android.ui.components.BossClawDropdown
import com.bossclaw.android.ui.components.ScreenHeader
import com.bossclaw.android.ui.components.soft.SoftButton
import com.bossclaw.android.ui.components.soft.SoftButtonVariant
import com.bossclaw.android.ui.components.soft.SoftIconBadge
import com.bossclaw.android.ui.navigation.Routes
import com.bossclaw.android.ui.theme.BossClawColors
import com.bossclaw.android.ui.theme.BossClawElevation
import com.bossclaw.android.ui.theme.BossClawRadius
import com.bossclaw.android.ui.theme.BossClawShadow
import com.bossclaw.android.ui.theme.BossClawSpacing
import com.bossclaw.android.ui.theme.LocalBossClawDark
import com.bossclaw.android.ui.theme.softShadow

/**
 * 通用设置：只保留外观、入口与数据管理；
 * 运行边界、模型、技能、权限各自独立成页，从侧栏或本页跳转。
 */
@Composable
fun SettingsScreen(viewModel: BossClawViewModel, onNavigate: (String) -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }

    val dark = LocalBossClawDark.current
    val colors = MaterialTheme.colorScheme

    val readyCount = listOf(
        permissions.accessibility,
        permissions.overlay,
        permissions.notifications,
        !permissions.batteryOptimized
    ).count { it }
    val modelName = viewModel.llmConfig().model.ifBlank { "未配置" }
    val enabledSkills = skills.count { it.enabled }
    val permissionAccent = if (readyCount == 4) {
        if (dark) Color(0xFF6FD69B) else BossClawColors.Success
    } else {
        colors.error
    }

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
            title = "设置",
            subtitle = "先划定边界，再交给自动化"
        )

        BossClawCard(padding = BossClawSpacing.sm) {
            SettingLinkRow(
                icon = Icons.Outlined.Tune,
                title = "自动化边界",
                subtitle = "运行模式、节奏、筛选与额度",
                value = settings.runMode.label,
                accent = colors.primary,
                onClick = { onNavigate(Routes.AUTOMATION) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = BossClawSpacing.xs),
                color = colors.outline.copy(alpha = 0.4f)
            )
            SettingLinkRow(
                icon = Icons.Outlined.Cloud,
                title = "模型服务",
                subtitle = "接口、密钥与连通性测试",
                value = modelName,
                accent = colors.secondary,
                onClick = { onNavigate(Routes.MODEL) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = BossClawSpacing.xs),
                color = colors.outline.copy(alpha = 0.4f)
            )
            SettingLinkRow(
                icon = Icons.Outlined.AutoAwesome,
                title = "技能库",
                subtitle = "内置 SKILL.md 与自定义导入",
                value = "$enabledSkills 启用",
                accent = colors.primary,
                onClick = { onNavigate(Routes.SKILLS) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = BossClawSpacing.xs),
                color = colors.outline.copy(alpha = 0.4f)
            )
            SettingLinkRow(
                icon = Icons.Outlined.Lock,
                title = "权限自检",
                subtitle = "无障碍、悬浮窗、通知与后台",
                value = "$readyCount/4 就绪",
                accent = permissionAccent,
                onClick = { onNavigate(Routes.PERMISSIONS) }
            )
        }

        BossClawCard {
            Text(
                text = "外观",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface
            )
            Text(
                text = "跟随系统可自动切换深浅色，渐变与插画会同步适配",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = BossClawSpacing.xxs)
            )
            BossClawDropdown(
                label = "深浅色",
                selectedValue = settings.themeMode,
                options = listOf(
                    "system" to "跟随系统",
                    "light" to "浅色",
                    "dark" to "深色"
                ),
                onSelect = { value ->
                    viewModel.updateSettings { it.copy(themeMode = value) }
                },
                modifier = Modifier.padding(top = BossClawSpacing.sm)
            )
        }

        BossClawCard {
            Text(
                text = "应用与数据",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = BossClawSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(BossClawSpacing.xs)
            ) {
                SoftButton(
                    text = "打开 BOSS",
                    onClick = viewModel::openBoss,
                    variant = SoftButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )
                DangerSoftButton(
                    text = "清除数据",
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "清除后将删除岗位、会话、日志、画像与模型配置，且不可恢复",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = BossClawSpacing.xs)
            )
        }

        Text(
            text = "安全说明：BossClaw 不绕过平台限制，不群发、不伪造事实；遇到验证码会立即暂停并提示你人工处理",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("确认清除所有数据？") },
            text = { Text("岗位、会话、日志、画像与模型配置都会被删除，无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error)
                ) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    accent: Color,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BossClawRadius.control))
            .clickable(onClick = onClick)
            .padding(horizontal = BossClawSpacing.xs, vertical = BossClawSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SoftIconBadge(icon = icon, size = 36.dp, tint = accent)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = BossClawSpacing.sm)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = BossClawSpacing.xxs)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            maxLines = 1
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 危险操作按钮：沿用柔和次要按钮的形态，但文字使用错误色，替换原来的红色描边按钮。 */
@Composable
private fun DangerSoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = LocalBossClawDark.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(BossClawRadius.button)
    Row(
        modifier = modifier
            .softShadow(BossClawElevation.card, shape, BossClawShadow.neutral(dark))
            .background(colors.surfaceContainerHigh, shape)
            .clickable(onClick = onClick)
            .heightIn(min = BossClawSpacing.controlHeight)
            .padding(horizontal = BossClawSpacing.md, vertical = BossClawSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = colors.error,
            maxLines = 1
        )
    }
}
