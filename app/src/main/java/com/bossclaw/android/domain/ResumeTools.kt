package com.bossclaw.android.domain

import com.bossclaw.android.core.JsonCodec
import com.bossclaw.android.data.db.ProfileEntity
import com.bossclaw.android.llm.ProfileResult

object ResumeTools {

    private val commonSkills = listOf(
        "Kotlin", "Java", "Python", "Go", "JavaScript", "TypeScript", "C++", "C#", "Rust", "Swift",
        "Android", "iOS", "Flutter", "React Native", "React", "Vue", "Node.js", "Spring", "Spring Boot",
        "MySQL", "PostgreSQL", "Redis", "MongoDB", "Kafka", "Docker", "Kubernetes", "Linux", "Git",
        "Figma", "Photoshop", "SQL", "Hadoop", "Spark", "Flink", "PyTorch", "TensorFlow", "LLM",
        "产品设计", "数据分析", "项目管理", "用户增长", "新媒体运营", "短视频", "直播运营", "SEO", "SEM"
    )

    fun normalize(raw: String): String = raw
        .replace("\uFEFF", "")
        .replace("\u200B", "")
        .replace("\r\n", "\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()

    fun looksLikeResume(text: String): Boolean {
        if (text.length < 80) return false
        val markers = listOf("工作经历", "项目经历", "教育经历", "专业技能", "个人优势", "求职意向", "自我评价")
        return markers.any { text.contains(it) } || Regex("""(19|20)\d{2}[.\-/年]""").containsMatchIn(text)
    }

    fun fallbackProfile(resume: String): ProfileResult {
        val years = Regex("""(\d{1,2})\s*年(?:工作)?经验""").find(resume)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val education = when {
            resume.contains("博士") -> "博士"
            resume.contains("硕士") || resume.contains("研究生") -> "硕士"
            resume.contains("本科") -> "本科"
            resume.contains("大专") || resume.contains("专科") -> "大专"
            else -> ""
        }
        val skills = commonSkills.filter { resume.contains(it, ignoreCase = true) }.take(20)
        val name = Regex("""^(?:姓名|Name)[：:\s]+([\u4e00-\u9fa5]{2,4})""", RegexOption.MULTILINE)
            .find(resume)?.groupValues?.get(1).orEmpty()
        val firstLine = resume.lineSequence().firstOrNull { it.isNotBlank() && it.length < 30 }?.trim().orEmpty()
        return ProfileResult(
            name = name.ifBlank { firstLine.take(4) },
            years = years,
            education = education,
            skills = skills,
            strengths = emptyList(),
            summary = resume.lineSequence().take(4).joinToString(" ").take(80),
            targetRoles = emptyList(),
            targetCities = emptyList(),
            minSalaryK = 0
        )
    }

    fun toEntity(result: ProfileResult, previous: ProfileEntity? = null): ProfileEntity = ProfileEntity(
        name = result.name,
        years = result.years,
        education = result.education,
        skillsJson = JsonCodec.encode(result.skills),
        strengthsJson = JsonCodec.encode(result.strengths),
        summary = result.summary,
        targetRolesJson = JsonCodec.encode(result.targetRoles),
        targetCitiesJson = JsonCodec.encode(result.targetCities),
        minSalaryK = result.minSalaryK,
        updatedAt = System.currentTimeMillis()
    )
}
