package com.bossclaw.android.data.repo

import android.content.Context
import android.net.Uri
import com.bossclaw.android.data.db.SkillDao
import com.bossclaw.android.data.db.SkillEntity
import com.bossclaw.android.llm.SkillScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * AI 技能库：
 * - 内置技能从 assets/skills/<id>/SKILL.md 读取，随版本更新但保留用户的启用状态；
 * - 用户可导入标准 SKILL.md（frontmatter: name/description/scope/defaultEnabled）；
 * - 启用状态存 Room，运行时由 LlmGateway 按 scope 注入 system prompt。
 */
class SkillRepository(
    private val context: Context,
    private val dao: SkillDao
) {

    fun observeAll(): Flow<List<SkillEntity>> = dao.observeAll()

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        BUILT_INS.forEach { id ->
            val raw = runCatching {
                context.assets.open("skills/$id/SKILL.md").bufferedReader().use { it.readText() }
            }.getOrNull() ?: return@forEach
            val parsed = parseSkill(raw, fallbackId = id, builtin = true)
            val existing = dao.find(id)
            dao.save(if (existing == null) parsed else parsed.copy(enabled = existing.enabled))
        }
    }

    suspend fun enabledInstructions(scope: String): String = withContext(Dispatchers.IO) {
        dao.enabled(scope)
            .joinToString("\n\n") { it.instructions.trim() }
            .take(8000)
    }

    suspend fun toggle(id: String, enabled: Boolean) {
        dao.setEnabled(id, enabled)
    }

    suspend fun deleteCustom(id: String) {
        dao.deleteCustom(id)
    }

    suspend fun importSkill(uri: Uri, fileName: String): SkillEntity = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("无法读取技能文件")
        if (!raw.trimStart().startsWith("---")) {
            throw IllegalStateException("技能文件缺少 frontmatter：需以 --- 开头并包含 name / description / scope")
        }
        val fallbackId = "user-" + System.currentTimeMillis().toString(36) + "-" +
            fileName.substringBeforeLast('.').replace(Regex("""[^\w\u4e00-\u9fa5-]"""), "-").take(16)
        val skill = parseSkill(raw, fallbackId, builtin = false).copy(enabled = true)
        dao.save(skill)
        skill
    }

    suspend fun clearAll() = dao.clear()

    private fun parseSkill(raw: String, fallbackId: String, builtin: Boolean): SkillEntity {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').trimStart('\uFEFF')
        val (meta, body) = splitFrontMatter(text)
        val name = meta["title"] ?: meta["name"] ?: fallbackId
        val description = meta["description"].orEmpty()
        val scope = meta["scope"]?.takeIf { it in SkillScope.all } ?: SkillScope.ASSISTANT
        val enabled = meta["defaultEnabled"]?.let { it == "true" } ?: true
        return SkillEntity(
            id = fallbackId,
            name = name,
            description = description,
            scope = scope,
            instructions = body.ifBlank { text },
            enabled = enabled,
            builtin = builtin
        )
    }

    private fun splitFrontMatter(text: String): Pair<Map<String, String>, String> {
        if (!text.startsWith("---")) return emptyMap<String, String>() to text
        val end = text.indexOf("\n---", 3)
        if (end < 0) return emptyMap<String, String>() to text
        val header = text.substring(3, end)
        val body = text.substring(end + 4).trim()
        val meta = header.lineSequence()
            .mapNotNull { line ->
                if (!line.contains(':')) return@mapNotNull null
                val key = line.substringBefore(':').trim()
                val value = line.substringAfter(':').trim().trim('"', '\'')
                if (key.isBlank()) null else key to value
            }
            .toMap()
        return meta to body
    }

    private companion object {
        val BUILT_INS = listOf("resume-profile", "job-analysis", "greetings")
    }
}
