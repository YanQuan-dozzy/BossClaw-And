package com.bossclaw.android.llm

import com.bossclaw.android.core.JsonCodec
import com.bossclaw.android.model.FitLevel
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class JobEvaluation(
    val fitLevel: String,
    val score: Int,
    val highlights: List<String>,
    val risks: List<String>,
    val reason: String,
    val greetingPoints: List<String>,
    val action: String
)

data class GreetingResult(val greeting: String)

data class DialogueDecision(
    val decision: String,
    val reply: String,
    val intent: String,
    val reason: String,
    val sendResume: Boolean = false,
    val interview: Boolean = false
)

data class ProfileResult(
    val name: String,
    val years: Int,
    val education: String,
    val skills: List<String>,
    val strengths: List<String>,
    val summary: String,
    val targetRoles: List<String>,
    val targetCities: List<String>,
    val minSalaryK: Int
)

object ResponseParser {

    fun extractJson(raw: String): JsonObject {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.removePrefix("```json").removePrefix("```").trim()
            if (text.endsWith("```")) text = text.dropLast(3).trim()
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        val candidate = if (start >= 0 && end > start) text.substring(start, end + 1) else text
        return runCatching { JsonParser.parseString(candidate).asJsonObject }
            .getOrElse { throw LlmException("模型输出不是有效 JSON：${raw.take(120)}") }
    }

    fun parseEvaluation(raw: String): JobEvaluation {
        val json = extractJson(raw)
        val score = (json.int("match_score", "matchScore", "score") ?: 60).coerceIn(0, 100)
        val fit = normalizeFitLevel(json.string("fit_level", "fitLevel", "level"), score)
        return JobEvaluation(
            fitLevel = fit,
            score = score,
            highlights = json.stringList("highlights", "matched_evidence", "matchedEvidence"),
            risks = json.stringList("risks"),
            reason = json.string("summary_reason", "reason", "summary") ?: "",
            greetingPoints = json.stringList("greeting_points", "greetingPoints"),
            action = normalizeAction(json.string("recommended_action", "action"), fit)
        )
    }

    fun parseGreeting(raw: String): GreetingResult {
        val json = extractJson(raw)
        val greeting = json.string("greeting", "message", "content") ?: ""
        if (greeting.isBlank()) throw LlmException("模型未生成打招呼内容")
        return GreetingResult(greeting.trim())
    }

    fun parseDialogue(raw: String): DialogueDecision {
        val json = extractJson(raw)
        val outcome = normalizeOutcome(json.string("outcome", "decision"))
        val reply = json.string("reply", "message", "content") ?: ""
        return DialogueDecision(
            decision = outcome,
            reply = reply.trim(),
            intent = json.string("intent") ?: "",
            reason = json.string("reason") ?: "",
            sendResume = json.bool("send_resume", "sendResume"),
            interview = json.bool("interview")
        )
    }

    fun parseProfile(raw: String): ProfileResult {
        val json = extractJson(raw)
        val years = json.int("years", "experience_years", "years_of_experience") ?: 0
        val targetRoles = json.stringList("target_roles", "targetRoles")
        val targetCities = json.stringList("target_cities", "targetCities")
        return ProfileResult(
            name = json.string("name") ?: "",
            years = years.coerceIn(0, 45),
            education = json.string("education", "degree") ?: "",
            skills = json.stringList("skills").take(15),
            strengths = json.stringList("strengths").take(5),
            summary = json.string("summary", "profile_summary") ?: "",
            targetRoles = targetRoles.take(3),
            targetCities = targetCities.take(8),
            minSalaryK = (json.int("min_salary_k", "expected_salary_k") ?: 0).coerceIn(0, 500)
        )
    }

    /** 档位归一：兼容中文/近义写法，识别失败按分数反推。 */
    private fun normalizeFitLevel(raw: String?, score: Int): String {
        val value = raw.orEmpty().trim().lowercase()
        if (value in setOf("strong", "match", "cautious", "unfit")) return value
        if (value.isBlank()) return FitLevel.fromScore(score).raw
        return when {
            Regex("unfit|不推荐|不适格|不合格|不匹配|reject").containsMatchIn(value) -> "unfit"
            Regex("cautious|谨慎|存疑|待定").containsMatchIn(value) -> "cautious"
            Regex("strong|推荐|高度|非常匹配|强匹配").containsMatchIn(value) -> "strong"
            Regex("match|匹配|合适").containsMatchIn(value) -> "match"
            else -> FitLevel.fromScore(score).raw
        }
    }

    private fun normalizeAction(raw: String?, fitLevel: String): String {
        // 档位优先：不推荐一律跳过；谨慎档只进人工复核，不允许自动发送。
        if (fitLevel == "unfit") return "skip"
        if (fitLevel == "cautious") return "review"
        val value = raw.orEmpty().trim().lowercase()
        return when {
            value == "skip" || value == "reject" -> "skip"
            else -> "deliver"
        }
    }

    private fun normalizeOutcome(raw: String?): String {
        val value = raw.orEmpty().trim().lowercase()
        return when {
            Regex("hr_rejected|hrrejected|rejected|reject|拒绝|不合适|已招满").containsMatchIn(value) -> "hr_rejected"
            Regex("declined|decline|婉拒").containsMatchIn(value) -> "declined"
            value == "handoff" -> "handoff"
            else -> "continue"
        }
    }

    private fun JsonObject.bool(vararg keys: String): Boolean {
        for (key in keys) {
            val element = get(key) ?: continue
            if (!element.isJsonNull && element.isJsonPrimitive) {
                return runCatching { element.asBoolean }.getOrDefault(false)
            }
        }
        return false
    }

    private fun JsonObject.string(vararg keys: String): String? {
        for (key in keys) {
            val element = get(key) ?: continue
            if (!element.isJsonNull && element.isJsonPrimitive) return element.asString
        }
        return null
    }

    private fun JsonObject.int(vararg keys: String): Int? {
        for (key in keys) {
            val element = get(key) ?: continue
            if (!element.isJsonNull && element.isJsonPrimitive) {
                return runCatching { element.asInt }.getOrNull()
            }
        }
        return null
    }

    private fun JsonObject.stringList(vararg keys: String): List<String> {
        for (key in keys) {
            val element = get(key) ?: continue
            if (element.isJsonArray) {
                return element.asJsonArray.mapNotNull { item ->
                    if (item.isJsonPrimitive) item.asString.trim().takeIf { it.isNotBlank() } else null
                }
            }
            if (element.isJsonPrimitive) {
                val raw = element.asString
                if (raw.isNotBlank()) {
                    runCatching {
                        JsonCodec.gson.fromJson<List<String>>(raw, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type)
                    }.getOrNull()?.let { return it }
                }
            }
        }
        return emptyList()
    }
}
