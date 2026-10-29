package com.bossclaw.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bossclaw.android.data.db.SkillEntity
import com.bossclaw.android.llm.SkillScope
import com.bossclaw.android.ui.BossClawViewModel
import com.bossclaw.android.ui.components.BossClawCard
import com.bossclaw.android.ui.components.ScreenHeader
import com.bossclaw.android.ui.theme.BossClawColors

@Composable
fun SkillsSettingsScreen(viewModel: BossClawViewModel) {
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importSkill(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader(
            title = "技能库",
            subtitle = "按作用域注入提示词，内置技能可停用、自定义技能可删除"
        )
        BossClawCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已加载 ${skills.size} 个技能",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("导入 .md")
                }
            }
            Text(
                text = "评估、开场白、回复时按技能作用域追加约束",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (skills.isEmpty()) {
                Text(
                    text = "技能加载中…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            } else {
                skills.forEach { skill ->
                    SkillRow(
                        skill = skill,
                        onToggle = viewModel::toggleSkill,
                        onDelete = viewModel::deleteSkill
                    )
                }
            }
        }
        Text(
            text = "导入的自定义技能只保存在本机，随提示词一起发送给模型服务",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SkillRow(
    skill: SkillEntity,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (SkillEntity) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = skill.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${SkillScope.label(skill.scope)} · ${skill.description}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (skill.builtin) "内置技能" else "自定义技能",
                style = MaterialTheme.typography.labelSmall,
                color = if (skill.builtin) BossClawColors.Muted else BossClawColors.Teal
            )
        }
        Switch(
            checked = skill.enabled,
            onCheckedChange = { onToggle(skill.id, it) }
        )
        if (!skill.builtin) {
            IconButton(onClick = { onDelete(skill) }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除技能",
                    tint = BossClawColors.Muted
                )
            }
        }
    }
}
