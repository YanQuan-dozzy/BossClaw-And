package com.bossclaw.android.domain

import com.bossclaw.android.core.JsonCodec
import com.bossclaw.android.data.db.DirectionEntity
import com.bossclaw.android.data.db.JobEntity
import com.bossclaw.android.data.db.ProfileEntity

data class LocalScore(val score: Int, val fitLevel: String, val reason: String)

class JobScorer {

    fun score(profile: ProfileEntity?, job: JobEntity, directions: List<DirectionEntity> = emptyList()): LocalScore {
        if (profile == null) return LocalScore(50, "cautious", "尚未生成候选人画像，仅按岗位完整度评估")

        var total = 0
        val notes = mutableListOf<String>()
        val skills = JsonCodec.decode(profile.skillsJson, emptyList<String>())
        val targets = JsonCodec.decode(profile.targetRolesJson, emptyList<String>())
        val cities = JsonCodec.decode(profile.targetCitiesJson, emptyList<String>())
        val haystack = "${job.title} ${job.jd}"

        val hitSkills = skills.filter { skill -> skill.length >= 2 && haystack.contains(skill, ignoreCase = true) }
        val skillScore = if (skills.isEmpty()) 20 else (hitSkills.size.toFloat() / skills.size * 45f).toInt()
        total += skillScore.coerceIn(0, 45)
        if (hitSkills.isNotEmpty()) notes += "技能命中 ${hitSkills.take(4).joinToString("、")}"

        val targetHit = targets.any { target -> target.length >= 2 && job.title.contains(target, ignoreCase = true) }
        if (targetHit) {
            total += 20
            notes += "岗位方向匹配"
        }

        if (cities.isNotEmpty() && cities.any { city -> job.city.contains(city.replace("市", "")) }) {
            total += 10
        }

        val expectedK = maxOf(profile.minSalaryK, 0)
        val salaryMinK = parseSalaryMinK(job.salaryText)
        if (expectedK == 0 || salaryMinK == null || salaryMinK >= expectedK) {
            total += 15
        }

        if (job.degree.contains("不限") || profile.education.isBlank()) {
            total += 10
        } else {
            total += if (profile.education.contains(job.degree.take(2))) 10 else 5
        }

        val directionHit = directions.any { direction ->
            val keywords = JsonCodec.decode(direction.keywordsJson, emptyList<String>())
            keywords.any { keyword -> haystack.contains(keyword, ignoreCase = true) }
        }
        if (directionHit) {
            total += 5
            notes += "命中求职方向关键词"
        }

        if (job.jd.isBlank()) {
            total -= 10
            notes += "JD 信息不完整"
        }
        if (job.hrName.isNotBlank()) total += 2

        val score = total.coerceIn(0, 100)
        val fit = when {
            score >= 81 -> "strong"
            score >= 65 -> "match"
            score >= 50 -> "cautious"
            else -> "unfit"
        }
        val reason = (if (notes.isEmpty()) listOf("按岗位完整度与画像字段粗略估算") else notes).joinToString("；")
        return LocalScore(score, fit, reason)
    }

    private fun parseSalaryMinK(text: String): Int? {
        val rangeK = Regex("""(\d+(?:\.\d+)?)\s*[-~—至]\s*(\d+(?:\.\d+)?)\s*[kK千]""").find(text)
        if (rangeK != null) return rangeK.groupValues[1].toDouble().toInt()
        val singleK = Regex("""(\d+(?:\.\d+)?)\s*[kK]""").find(text)
        if (singleK != null) return singleK.groupValues[1].toDouble().toInt()
        return null
    }
}
