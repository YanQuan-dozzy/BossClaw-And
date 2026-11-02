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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bossclaw.android.model.RunMode
import com.bossclaw.android.ui.components.BossClawCard
import com.bossclaw.android.ui.components.ScreenHeader
import com.bossclaw.android.ui.theme.BossClawColors
import com.bossclaw.android.ui.theme.BossClawSpacing

/**
 * 使用文档：把权限、简历、模型、边界到第一次巡查的顺序写清楚，
 * 首次引导弹窗内容较多时，用户也能随时回到这里完整查看。
 */
@Composable
fun UsageGuideScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = BossClawSpacing.screen,
                end = BossClawSpacing.screen,
                top = BossClawSpacing.xxs,
                bottom = BossClawSpacing.xxl
            ),
        verticalArrangement = Arrangement.spacedBy(BossClawSpacing.md)
    ) {
        ScreenHeader(
            title = "使用文档",
            subtitle = "从权限准备到第一次投递"
        )

        BossClawCard {
            SectionTitle("四步准备")
            GuideStep(
                index = 1,
                title = "完成权限自检",
                body = "无障碍、悬浮窗、通知与后台运行四项就绪；从侧栏「权限自检」逐项开启，返回应用会自动刷新状态。"
            )
            GuideStep(
                index = 2,
                title = "导入简历并生成画像",
                body = "「简历中心」支持导入 PDF / DOCX / TXT / MD 或直接粘贴正文，导入后点「生成画像」，匹配评分与筛选都以画像为准。"
            )
            GuideStep(
                index = 3,
                title = "配置模型服务",
                body = "「模型服务」填写接口地址、API Key 与模型名，先点测试连接。密钥只保存在本机加密存储中，不会写入日志。"
            )
            GuideStep(
                index = 4,
                title = "设定自动化边界",
                body = "「自动化边界」选择运行模式与岗位间隔，设置单日投递上限、薪资 / 学历 / 经验筛选与排除关键字；岗位间隔按秒自定义，执行时会自动加入随机抖动。"
            )
        }

        BossClawCard {
            SectionTitle("第一次巡查")
            GuideBullet("把 BOSS 直聘切换到推荐页或搜索结果页，再回到工作台。")
            GuideBullet("先点「试跑一轮」确认页面识别正常，再开启「开始巡查」；也可以点「开始推荐匹配」直接评估推荐流岗位。")
            GuideBullet("巡查过程中可在「数据看板」查看各阶段数据，在「运行记录」查看逐条日志。")
            GuideBullet("点「急停」立即终止当前巡查；需要继续时再手动启动，不会自动恢复。")
        }

        BossClawCard {
            SectionTitle("三种运行模式")
            RunMode.entries.forEach { mode ->
                ModeRow(
                    label = mode.label,
                    hint = mode.hint,
                    accent = when (mode) {
                        RunMode.MANUAL -> BossClawColors.Muted
                        RunMode.SEMI -> BossClawColors.Amber
                        RunMode.AUTO -> BossClawColors.Teal
                    }
                )
            }
        }

        BossClawCard {
            SectionTitle("沟通与跟进")
            GuideBullet("投递成功后，HR 的回复会汇总到「沟通」页；也可以点右上角「扫描消息」立即读取。")
            GuideBullet("打开会话后点「AI 帮我回复」生成草稿，检查内容后再点发送；发送始终保留人工确认。")
            GuideBullet("「自动化边界」里的「沟通信息」填写薪资期望、可面试时间等真实信息，AI 只引用这里的内容，不会编造。")
        }

        BossClawCard {
            SectionTitle("安全边界")
            GuideBullet("不绕过平台风控与验证码；遇到验证码或风控提示会暂停并等待人工处理。")
            GuideBullet("单日投递有硬上限，岗位间隔由你自定义，并自动加入随机抖动，避免密集操作。")
            GuideBullet("不编造简历与经历、不冒充招聘方；岗位描述只作为待评估内容，不会被当作指令执行。")
            GuideBullet("数据只保存在本机，卸载应用或「清除数据」会一并删除。")
        }

        Text(
            text = "侧栏「帮助 → 使用文档」可以随时回到本页",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = BossClawSpacing.xxs)
    )
}

/** 编号步骤：序号用品牌色圆点，正文与标题分两行，避免长句挤成一块。 */
@Composable
private fun GuideStep(index: Int, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BossClawSpacing.xxs),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            modifier = Modifier.size(26.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = BossClawSpacing.sm)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun GuideBullet(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BossClawSpacing.xxs),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .background(BossClawColors.Teal, CircleShape)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = BossClawSpacing.sm)
        )
    }
}

@Composable
private fun ModeRow(label: String, hint: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BossClawSpacing.xxs),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .background(accent, RoundedCornerShape(3.dp))
        )
        Column(modifier = Modifier.weight(1f).padding(start = BossClawSpacing.sm)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = accent
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}
