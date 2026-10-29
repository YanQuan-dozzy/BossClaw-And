package com.bossclaw.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val WORKBENCH = "workbench"
    const val INSIGHTS = "insights"
    const val JOBS = "jobs"
    const val CHAT = "chat"
    const val RESUME = "resume"
    const val RECORDS = "records"

    const val SETTINGS = "settings"
    const val AUTOMATION = "settings/automation"
    const val MODEL = "settings/model"
    const val SKILLS = "settings/skills"
    const val PERMISSIONS = "settings/permissions"
}

data class NavEntry(val route: String, val label: String, val icon: ImageVector)

data class NavSection(val title: String, val entries: List<NavEntry>)

/** 侧栏分组：工作、运行、配置三段，避免一个长列表把所有入口挤在一起。 */
val navSections = listOf(
    NavSection(
        title = "工作",
        entries = listOf(
            NavEntry(Routes.WORKBENCH, "工作台", Icons.Outlined.Dashboard),
            NavEntry(Routes.INSIGHTS, "数据看板", Icons.Outlined.BarChart),
            NavEntry(Routes.JOBS, "岗位", Icons.Outlined.Work),
            NavEntry(Routes.CHAT, "沟通", Icons.AutoMirrored.Outlined.Chat),
            NavEntry(Routes.RESUME, "简历中心", Icons.Outlined.Description)
        )
    ),
    NavSection(
        title = "运行",
        entries = listOf(
            NavEntry(Routes.RECORDS, "运行记录", Icons.Outlined.History),
            NavEntry(Routes.PERMISSIONS, "权限自检", Icons.Outlined.Lock)
        )
    ),
    NavSection(
        title = "配置",
        entries = listOf(
            NavEntry(Routes.AUTOMATION, "自动化边界", Icons.Outlined.Tune),
            NavEntry(Routes.MODEL, "模型服务", Icons.Outlined.Cloud),
            NavEntry(Routes.SKILLS, "技能库", Icons.Outlined.AutoAwesome),
            NavEntry(Routes.SETTINGS, "通用设置", Icons.Outlined.Settings)
        )
    )
)
