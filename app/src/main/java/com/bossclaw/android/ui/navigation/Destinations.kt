package com.bossclaw.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

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

    const val GUIDE = "guide"
}

data class NavEntry(val route: String, val label: String, val icon: ImageVector)

/** Navigation 3 的返回栈键。先用路由字符串包一层，页面层的回调签名暂时不用改。 */
@Serializable
data class AppKey(val route: String) : NavKey

data class NavSection(val title: String, val entries: List<NavEntry>)

/** 底部常驻的四个工作模块：日常最常用的切换入口，不再重复出现在侧栏与首页。 */
val workTabs = listOf(
    NavEntry(Routes.WORKBENCH, "工作台", Icons.Outlined.Dashboard),
    NavEntry(Routes.JOBS, "岗位", Icons.Outlined.Work),
    NavEntry(Routes.CHAT, "沟通", Icons.AutoMirrored.Outlined.Chat),
    NavEntry(Routes.RESUME, "简历中心", Icons.Outlined.Description)
)

/** 底部常驻入口：切换时清掉中间页，避免返回栈无限增长。 */
val tabRoutes = workTabs.map { it.route }.toSet()

/** 侧栏只保留底部导航之外的入口：看板、运行记录、配置与帮助文档。 */
val navSections = listOf(
    NavSection(
        title = "工作",
        entries = listOf(
            NavEntry(Routes.INSIGHTS, "数据看板", Icons.Outlined.BarChart)
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
    ),
    NavSection(
        title = "帮助",
        entries = listOf(
            NavEntry(Routes.GUIDE, "使用文档", Icons.Outlined.School)
        )
    )
)
