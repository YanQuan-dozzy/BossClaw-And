package com.bossclaw.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bossclaw.android.ui.BossClawViewModel
import com.bossclaw.android.ui.components.BossClawCard
import com.bossclaw.android.ui.components.ScreenHeader
import com.bossclaw.android.ui.components.openSystemSettings
import com.bossclaw.android.ui.theme.BossClawColors
import com.bossclaw.android.ui.theme.BossClawRadius
import com.bossclaw.android.ui.theme.BossClawSpacing
import com.bossclaw.android.ui.theme.LocalBossClawDark
import com.bossclaw.android.util.PermissionTools

@Composable
fun PermissionsScreen(viewModel: BossClawViewModel) {
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
            title = "权限自检",
            subtitle = "四项就绪后才建议开启长时间巡查"
        )

        BossClawCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "必要权限",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = { viewModel.refreshPermissions() }) { Text("刷新") }
            }
            PermissionLine(
                title = "无障碍服务",
                subtitle = "读取 BOSS 页面并执行点击，是自动化的前提",
                granted = permissions.accessibility,
                actionLabel = "开启",
                onClick = { openSystemSettings(context, PermissionTools.accessibilityIntent()) }
            )
            PermissionLine(
                title = "悬浮窗",
                subtitle = "在 BOSS 上方显示悬浮球与控制台",
                granted = permissions.overlay,
                actionLabel = "开启",
                onClick = { openSystemSettings(context, PermissionTools.overlayIntent(context)) }
            )
            PermissionLine(
                title = "通知",
                subtitle = "首单发送、HR 邀约与风控提醒",
                granted = permissions.notifications,
                actionLabel = "开启",
                onClick = { openSystemSettings(context, PermissionTools.notificationIntent(context)) }
            )
            PermissionLine(
                title = "后台运行",
                subtitle = "关闭电池优化，减少巡检被系统中断",
                granted = !permissions.batteryOptimized,
                actionLabel = "设置",
                onClick = { openSystemSettings(context, PermissionTools.batteryIntent(context)) }
            )
        }

        BossClawCard {
            Text(
                text = "建议开启顺序",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            listOf(
                "1. 无障碍服务：找到 BossClaw 并打开开关",
                "2. 悬浮窗：允许在其他应用上层显示",
                "3. 通知：允许提醒，首单发送后会推送",
                "4. 电池优化：设为不限制，避免后台被冻结"
            ).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = BossClawSpacing.xs)
                )
            }
        }

        Text(
            text = "BossClaw 只读取 BOSS 直聘所需的页面控件，不会上传通讯录、相册或位置信息",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionLine(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    val dark = LocalBossClawDark.current
    val colors = MaterialTheme.colorScheme
    val statusColor = if (granted) {
        if (dark) Color(0xFF6FD69B) else BossClawColors.Success
    } else {
        colors.error
    }
    val shape = RoundedCornerShape(BossClawRadius.control)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BossClawSpacing.xxs)
            .background(
                if (granted) colors.surfaceContainer else colors.error.copy(alpha = 0.06f),
                shape
            )
            .padding(horizontal = BossClawSpacing.sm, vertical = BossClawSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(statusColor.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = BossClawSpacing.sm)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
        if (granted) {
            Text(
                text = "已就绪",
                style = MaterialTheme.typography.labelLarge,
                color = statusColor
            )
        } else {
            TextButton(onClick = onClick) { Text(actionLabel) }
        }
    }
}
