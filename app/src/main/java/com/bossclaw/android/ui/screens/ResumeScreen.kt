package com.bossclaw.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bossclaw.android.core.JsonCodec
import com.bossclaw.android.data.db.DirectionEntity
import com.bossclaw.android.data.db.ProfileEntity
import com.bossclaw.android.domain.ResumeTools
import com.bossclaw.android.ui.BossClawViewModel
import com.bossclaw.android.ui.components.BossClawCard
import com.bossclaw.android.ui.components.EmptyState
import com.bossclaw.android.ui.components.ScreenHeader
import com.bossclaw.android.ui.components.soft.SoftButton
import com.bossclaw.android.ui.components.soft.SoftButtonVariant
import com.bossclaw.android.ui.components.soft.SoftTextField
import com.bossclaw.android.ui.theme.BossClawRadius
import com.bossclaw.android.ui.theme.BossClawSpacing

/** 超过该长度时输入框只展示预览，避免长文本逐字排版拖慢页面。 */
private const val PREVIEW_CHARS = 1_500

@Composable
fun ResumeScreen(viewModel: BossClawViewModel) {
    val resumeText by viewModel.resumeText.collectAsStateWithLifecycle()
    val fileName by viewModel.resumeFileName.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val directions by viewModel.directions.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    var editor by remember { mutableStateOf("") }
    var expandEditor by remember { mutableStateOf(false) }
    var showDirectionForm by remember { mutableStateOf(false) }

    LaunchedEffect(resumeText) {
        editor = resumeText
        expandEditor = false
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importResume(it) }
    }
    // 只列出可解析的文档类型，图片等无法解析的文件不会出现在系统选择器里
    val resumeMimeTypes = arrayOf(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain",
        "text/markdown"
    )
    val hasResume = resumeText.isNotBlank() || editor.isNotBlank()
    val folded = editor.length > PREVIEW_CHARS && !expandEditor

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = BossClawSpacing.screen, vertical = BossClawSpacing.md),
        verticalArrangement = Arrangement.spacedBy(BossClawSpacing.md)
    ) {
        ScreenHeader(
            title = "简历中心",
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
                        text = "${editor.length} 字 · 本地加密保存",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                TextButton(onClick = { picker.launch(resumeMimeTypes) }) {
                    Icon(
                        imageVector = Icons.Filled.Upload,
                        contentDescription = null,
                        modifier = Modifier.padding(end = BossClawSpacing.xxs)
                    )
                    Text(if (hasResume) "重新导入" else "导入文件")
                }
            }
            val preview = if (folded) {
                editor.take(PREVIEW_CHARS).trimEnd() + "\n\n…（已折叠，全文 ${editor.length} 字）"
            } else {
                editor
            }
            SoftTextField(
                value = preview,
                onValueChange = { text -> editor = text.take(ResumeTools.MAX_RESUME_CHARS) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = BossClawSpacing.sm)
                    .heightIn(min = 180.dp),
                label = if (folded) "简历原文（预览）" else "简历原文",
                placeholder = "支持 PDF / DOCX / TXT / MD，也可直接粘贴正文",
                readOnly = folded,
                singleLine = false,
                minLines = 8,
                maxLines = 16
            )
            if (editor.length > PREVIEW_CHARS) {
                TextButton(onClick = { expandEditor = !expandEditor }) {
                    Text(if (expandEditor) "收起为预览" else "展开编辑全文")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = BossClawSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(BossClawSpacing.xs)
            ) {
                SoftButton(
                    text = "保存原文",
                    onClick = { viewModel.saveResume(editor, fileName) },
                    enabled = !busy,
                    variant = SoftButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )
                SoftButton(
                    text = "生成画像",
                    onClick = { viewModel.generateProfile() },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        BossClawCard {
            Text(
                text = "候选人画像",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            val current = profile
            if (current == null) {
                EmptyState(
                    title = "尚未生成画像",
                    message = "导入简历后点「生成画像」"
                )
            } else {
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
                }
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
    }
}

@Composable
private fun ProfileForm(
    profile: ProfileEntity,
    onSave: (String, Int, String, List<String>, List<String>, String, List<String>, List<String>, Int) -> Unit
) {
    val skillsInit = remember(profile) {
        JsonCodec.decode(profile.skillsJson, emptyList<String>())
    }
    val strengthsInit = remember(profile) {
        JsonCodec.decode(profile.strengthsJson, emptyList<String>())
    }
    val rolesInit = remember(profile) {
        JsonCodec.decode(profile.targetRolesJson, emptyList<String>())
    }
    val citiesInit = remember(profile) {
        JsonCodec.decode(profile.targetCitiesJson, emptyList<String>())
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

    Column(
        modifier = Modifier.padding(top = BossClawSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(BossClawSpacing.sm)
    ) {
        FormField("姓名", name) { name = it }
        Row(horizontalArrangement = Arrangement.spacedBy(BossClawSpacing.xs)) {
            FormField("工作年限", years, number = true, modifier = Modifier.weight(1f)) { years = it }
            FormField("期望薪资(K)", salary, number = true, modifier = Modifier.weight(1f)) { salary = it }
        }
        FormField("学历", education) { education = it }
        ChipListField(
            label = "技能",
            hint = "如：Kotlin、Python",
            values = skills,
            onChange = { skills = it }
        )
        ChipListField(
            label = "优势",
            hint = "如：沟通顺畅、交付稳定",
            values = strengths,
            onChange = { strengths = it }
        )
        ChipListField(
            label = "目标岗位",
            hint = "如：Android 开发、AI 产品经理",
            values = roles,
            onChange = { roles = it }
        )
        ChipListField(
            label = "目标城市",
            hint = "如：上海、杭州",
            values = cities,
            onChange = { cities = it }
        )
        FormField("一句话自我介绍", summary, minLines = 2) { summary = it }
        SoftButton(
            text = "保存画像",
            onClick = {
                onSave(
                    name,
                    years.toIntOrNull() ?: 0,
                    education,
                    skills,
                    strengths,
                    summary,
                    roles,
                    cities,
                    salary.toIntOrNull() ?: 0
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 标签式多值编辑：可继续输入新增，也可逐个删除，替代一整串用顿号分隔的文本。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipListField(
    label: String,
    hint: String,
    values: List<String>,
    onChange: (List<String>) -> Unit
) {
    var input by remember { mutableStateOf("") }

    fun commit() {
        val parts = splitTags(input)
        if (parts.isEmpty()) return
        onChange((values + parts).distinct())
        input = ""
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (values.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = BossClawSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(BossClawSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(BossClawSpacing.xs)
            ) {
                values.forEach { value ->
                    ProfileChip(value = value, onRemove = { onChange(values - value) })
                }
            }
        }
        SoftTextField(
            value = input,
            onValueChange = { raw ->
                if (raw.any { it in "、,，/|\n" }) {
                    val parts = splitTags(raw)
                    if (parts.isNotEmpty()) onChange((values + parts).distinct())
                    input = ""
                } else {
                    input = raw
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = BossClawSpacing.xs),
            placeholder = hint,
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { commit() }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加$label")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() })
        )
    }
}

@Composable
private fun ProfileChip(value: String, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.padding(
                start = BossClawSpacing.sm,
                top = BossClawSpacing.xxs,
                bottom = BossClawSpacing.xxs,
                end = BossClawSpacing.xxs
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(26.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "删除 $value",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
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
    SoftTextField(
        value = value,
        onValueChange = { text ->
            if (number) {
                if (text.all { it.isDigit() }) onChange(text.take(3))
            } else {
                onChange(text)
            }
        },
        modifier = modifier.fillMaxWidth(),
        label = label,
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
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = BossClawSpacing.xxs)
                )
                Text(if (showForm) "取消" else "添加")
            }
        }

        if (showForm) {
            var name by remember { mutableStateOf("") }
            var query by remember { mutableStateOf("") }
            var keywords by remember { mutableStateOf("") }
            Column(
                modifier = Modifier.padding(top = BossClawSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(BossClawSpacing.xs)
            ) {
                FormField("方向名称，如「AI 产品经理」", name) { name = it }
                FormField("搜索关键词（可留空，默认用名称）", query) { query = it }
                FormField("过滤关键词，用逗号分隔", keywords) { keywords = it }
                SoftButton(
                    text = "添加方向",
                    onClick = {
                        onAdd(name, query, keywords)
                        name = ""
                        query = ""
                        keywords = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (directions.isEmpty()) {
            EmptyState(
                title = "还没有方向",
                message = "添加方向后，会据此筛选岗位"
            )
        } else {
            directions.forEach { direction ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = BossClawSpacing.xs),
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 标签输入按顿号、逗号、斜线等切分，保留词内的空格（如 Spring Boot）。 */
private fun splitTags(text: String): List<String> =
    text.split('、', '，', ',', '/', '|', '\n').map { it.trim() }.filter { it.isNotBlank() }
