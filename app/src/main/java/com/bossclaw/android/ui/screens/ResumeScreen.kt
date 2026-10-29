package com.bossclaw.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bossclaw.android.core.JsonCodec
import com.bossclaw.android.data.db.DirectionEntity
import com.bossclaw.android.data.db.ProfileEntity
import com.bossclaw.android.ui.BossClawViewModel
import com.bossclaw.android.ui.components.BossClawCard
import com.bossclaw.android.ui.components.EmptyState
import com.bossclaw.android.ui.components.ScreenHeader
import com.bossclaw.android.ui.theme.BossClawColors

@Composable
fun ResumeScreen(viewModel: BossClawViewModel) {
    val resumeText by viewModel.resumeText.collectAsStateWithLifecycle()
    val fileName by viewModel.resumeFileName.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val directions by viewModel.directions.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    var editor by remember { mutableStateOf("") }
    var showProfileEditor by remember { mutableStateOf(false) }
    var showDirectionForm by remember { mutableStateOf(false) }

    LaunchedEffect(resumeText) { editor = resumeText }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importResume(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader(
            title = "简历中心",
            subtitle = "事实优先：所有开场白只引用这里的真实经历",
            trailing = {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                }
            }
        )

        BossClawCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName.ifBlank { "尚未导入简历" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${resumeText.length} 字 · 本地加密保存",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("导入")
                }
            }
            OutlinedTextField(
                value = editor,
                onValueChange = { editor = it },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 180.dp),
                label = { Text("简历原文（纯文本）") },
                placeholder = { Text("支持 PDF / DOCX / TXT / MD，本地解析，原文不上传") },
                minLines = 8,
                maxLines = 16
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.saveResume(editor, fileName) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
                Button(
                    onClick = { viewModel.generateProfile() },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("生成画像") }
            }
        }

        BossClawCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "候选人画像",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = { showProfileEditor = !showProfileEditor }) {
                    Text(if (showProfileEditor) "收起" else "编辑")
                }
            }

            val current = profile
            if (current == null) {
                EmptyState(
                    title = "尚未生成画像",
                    message = "导入简历后点击“生成画像”，AI 会抽取年限、技能与目标方向，失败时自动回退本地解析"
                )
            } else if (showProfileEditor) {
                ProfileForm(profile = current) { name, years, education, skills, strengths, summary, roles, cities, salary ->
                    viewModel.saveProfileFields(
                        name = name,
                        years = years,
                        education = education,
                        skills = skills,
                        strengths = strengths,
                        summary = summary,
                        roles = roles,
                        cities = cities,
                        salaryK = salary
                    )
                    showProfileEditor = false
                }
            } else {
                ProfileSummary(profile = current)
            }
        }

        DirectionsCard(
            directions = directions,
            showForm = showDirectionForm,
            onToggleForm = { showDirectionForm = !showDirectionForm },
            onAdd = { name, query, keywords ->
                viewModel.addDirection(name, query, keywords)
            },
            onToggle = { id, enabled -> viewModel.toggleDirection(id, enabled) },
            onDelete = { id -> viewModel.deleteDirection(id) }
        )

        Text(
            text = "隐私说明：简历与对话仅保存在本机加密存储中，只有评估岗位时必要的片段会随提示词发送给你配置的模型服务",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileSummary(profile: ProfileEntity) {
    val skills = JsonCodec.decode(profile.skillsJson, emptyList<String>())
    val strengths = JsonCodec.decode(profile.strengthsJson, emptyList<String>())
    val roles = JsonCodec.decode(profile.targetRolesJson, emptyList<String>())
    val cities = JsonCodec.decode(profile.targetCitiesJson, emptyList<String>())

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        InfoPill(profile.name.ifBlank { "未命名" })
        InfoPill("${profile.years} 年")
        if (profile.education.isNotBlank()) InfoPill(profile.education)
    }
    if (profile.summary.isNotBlank()) {
        Text(
            text = profile.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
    if (skills.isNotEmpty()) {
        Text(
            text = "技能 · ${skills.joinToString("、")}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    if (strengths.isNotEmpty()) {
        Text(
            text = "优势 · ${strengths.joinToString("、")}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
    if (roles.isNotEmpty() || cities.isNotEmpty()) {
        Text(
            text = "方向 · ${roles.joinToString(" / ")}${if (cities.isNotEmpty()) " ｜ ${cities.joinToString("、")}" else ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
    if (profile.minSalaryK > 0) {
        Text(
            text = "期望薪资 · ${profile.minSalaryK}K 起",
            style = MaterialTheme.typography.labelSmall,
            color = BossClawColors.Teal,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun InfoPill(text: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ProfileForm(
    profile: ProfileEntity,
    onSave: (String, Int, String, List<String>, List<String>, String, List<String>, List<String>, Int) -> Unit
) {
    val skillsInit = remember(profile) {
        JsonCodec.decode(profile.skillsJson, emptyList<String>()).joinToString("、")
    }
    val strengthsInit = remember(profile) {
        JsonCodec.decode(profile.strengthsJson, emptyList<String>()).joinToString("、")
    }
    val rolesInit = remember(profile) {
        JsonCodec.decode(profile.targetRolesJson, emptyList<String>()).joinToString("、")
    }
    val citiesInit = remember(profile) {
        JsonCodec.decode(profile.targetCitiesJson, emptyList<String>()).joinToString("、")
    }

    var name by remember(profile) { mutableStateOf(profile.name) }
    var years by remember(profile) { mutableStateOf(profile.years.toString()) }
    var education by remember(profile) { mutableStateOf(profile.education) }
    var salary by remember(profile) { mutableStateOf(profile.minSalaryK.toString()) }
    var summary by remember(profile) { mutableStateOf(profile.summary) }
    var skills by remember(profile) { mutableStateOf(skillsInit) }
    var strengths by remember(profile) { mutableStateOf(strengthsInit) }
    var roles by remember(profile) { mutableStateOf(rolesInit) }
    var cities by remember(profile) { mutableStateOf(citiesInit) }

    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormField("姓名", name) { name = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormField("工作年限", years, number = true, modifier = Modifier.weight(1f)) { years = it }
            FormField("期望薪资(K)", salary, number = true, modifier = Modifier.weight(1f)) { salary = it }
        }
        FormField("学历", education) { education = it }
        FormField("技能（用、分隔）", skills) { skills = it }
        FormField("优势（用、分隔）", strengths) { strengths = it }
        FormField("目标岗位（用、分隔）", roles) { roles = it }
        FormField("目标城市（用、分隔）", cities) { cities = it }
        FormField("一句话自我介绍", summary, minLines = 2) { summary = it }
        Button(
            onClick = {
                onSave(
                    name,
                    years.toIntOrNull() ?: 0,
                    education,
                    splitList(skills),
                    splitList(strengths),
                    summary,
                    splitList(roles),
                    splitList(cities),
                    salary.toIntOrNull() ?: 0
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存画像") }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    number: Boolean = false,
    minLines: Int = 1,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            if (number) {
                if (text.all { it.isDigit() }) onChange(text.take(3))
            } else {
                onChange(text)
            }
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = minLines == 1,
        minLines = minLines,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default
    )
}

@Composable
private fun DirectionsCard(
    directions: List<DirectionEntity>,
    showForm: Boolean,
    onToggleForm: () -> Unit,
    onAdd: (String, String, String) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit
) {
    BossClawCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "求职方向",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onToggleForm) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 2.dp))
                Text(if (showForm) "取消" else "添加")
            }
        }
        Text(
            text = "方向用于圈定岗位关键词，评估时会优先匹配已启用的方向",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (showForm) {
            var name by remember { mutableStateOf("") }
            var query by remember { mutableStateOf("") }
            var keywords by remember { mutableStateOf("") }
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField("方向名称，如「AI 产品经理」", name) { name = it }
                FormField("搜索关键词（可留空，默认用名称）", query) { query = it }
                FormField("过滤关键词，用逗号分隔", keywords) { keywords = it }
                Button(
                    onClick = {
                        onAdd(name, query, keywords)
                        name = ""
                        query = ""
                        keywords = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("添加方向") }
            }
        }

        if (directions.isEmpty()) {
            EmptyState(
                title = "还没有方向",
                message = "添加一个方向，例如「AI 产品经理 / 上海 / 25K 起」，BossClaw 会据此筛选岗位"
            )
        } else {
            directions.forEach { direction ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = direction.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val words = JsonCodec.decode(direction.keywordsJson, emptyList<String>())
                        Text(
                            text = listOfNotNull(
                                direction.query.takeIf { it.isNotBlank() },
                                words.takeIf { it.isNotEmpty() }?.joinToString("、")
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = direction.enabled,
                        onCheckedChange = { onToggle(direction.id, it) }
                    )
                    IconButton(onClick = { onDelete(direction.id) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除方向",
                            tint = BossClawColors.Muted
                        )
                    }
                }
            }
        }
    }
}

private fun splitList(text: String): List<String> =
    text.split('、', '，', ',', ' ', '/', '|').map { it.trim() }.filter { it.isNotBlank() }
