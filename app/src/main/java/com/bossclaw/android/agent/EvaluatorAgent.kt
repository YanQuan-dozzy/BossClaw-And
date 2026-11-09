package com.bossclaw.android.agent

import com.bossclaw.android.accessibility.model.ScrapedRawJob
import com.bossclaw.android.data.db.DirectionEntity
import com.bossclaw.android.data.db.JobEntity
import com.bossclaw.android.data.pref.AppSettings
import com.bossclaw.android.data.repo.BossClawRepository
import com.bossclaw.android.data.repo.ConfigRepository
import com.bossclaw.android.domain.FilterVerdict
import com.bossclaw.android.domain.JobFilter
import com.bossclaw.android.domain.JobScorer
import com.bossclaw.android.domain.LocalScore
import com.bossclaw.android.llm.JobEvaluation
import com.bossclaw.android.llm.LlmGateway
import com.bossclaw.android.model.FitLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EvaluationOutcome(
    val job: JobEntity,
    val verdict: FilterVerdict,
    val evaluation: JobEvaluation?,
    val localScore: LocalScore,
    val duplicate: Boolean = false
)

class EvaluatorAgent(
    private val repository: BossClawRepository,
    private val configRepository: ConfigRepository,
    private val filter: JobFilter,
    private val scorer: JobScorer,
    private val gateway: LlmGateway
) {

    suspend fun evaluate(
        raw: ScrapedRawJob,
        directions: List<DirectionEntity>,
        cardTags: List<String> = emptyList()
    ): EvaluationOutcome = withContext(Dispatchers.IO) {
        val jobId = raw.resolveJobId()
        val existing = repository.findJob(jobId)
            ?: repository.findSimilar(raw.title.trim(), raw.companyName.trim())
        if (existing != null) {
            return@withContext EvaluationOutcome(
                job = existing,
                verdict = FilterVerdict.PASS,
                evaluation = null,
                localScore = LocalScore(existing.score, existing.fitLevel, "岗位已在库中"),
                duplicate = true
            )
        }

        val settings = configRepository.settings.value
        val job = JobEntity(
            jobId = jobId,
            title = raw.title.trim(),
            company = raw.companyName.trim(),
            salaryText = raw.salaryText,
            city = raw.city,
            experience = raw.experience,
            degree = raw.degree,
            hrName = raw.hrName,
            hrActive = raw.hrActiveStatus,
            jd = JDCleaner.clean(raw.jobDescription),
            source = JobEntity.SOURCE_FEED,
            status = JobEntity.STATUS_DISCOVERED
        )
        repository.saveJob(job)

        val profile = repository.profile()
        val verdict = filter.check(job, profile, settings, cardTags)
        if (verdict.rejected) {
            repository.updateStatus(job.jobId, JobEntity.STATUS_FILTERED, verdict.reason)
            return@withContext EvaluationOutcome(
                job = job.copy(status = JobEntity.STATUS_FILTERED, reason = verdict.reason),
                verdict = verdict,
                evaluation = null,
                localScore = LocalScore(0, "unfit", verdict.reason)
            )
        }

        val localScore = scorer.score(profile, job, directions)
        val resume = configRepository.resumeText()
        val evaluation = runCatching { gateway.evaluateJob(job, profile, resume) }
            .onFailure { error ->
                AgentBus.log("评估", "AI 评估失败，使用本地评分：${error.message}", "warn")
            }
            .getOrNull()

        val fitLevel = evaluation?.fitLevel ?: localScore.fitLevel
        val score = normalizeScore(fitLevel, evaluation?.score ?: localScore.score)
        val reason = evaluation?.reason?.takeIf { it.isNotBlank() } ?: localScore.reason
        val status = if (evaluation?.action == "skip" || fitLevel == "unfit") {
            JobEntity.STATUS_CLOSED
        } else {
            JobEntity.STATUS_MATCHED
        }
        val finalJob = job.copy(
            fitLevel = fitLevel,
            score = score,
            reason = reason,
            status = status
        )
        repository.saveEvaluation(
            jobId = job.jobId,
            fitLevel = fitLevel,
            score = score,
            highlights = evaluation?.highlights ?: emptyList(),
            risks = evaluation?.risks ?: emptyList(),
            reason = reason,
            greeting = "",
            status = status
        )
        // 评估结论同步到悬浮球，让用户在 BOSS 页面就能看到当前岗位的分析。
        AgentBus.publishAnalysis(
            JobAnalysis(
                company = finalJob.company,
                title = finalJob.title,
                fitLabel = FitLevel.from(fitLevel).label,
                score = score,
                reason = reason,
                highlights = evaluation?.highlights ?: emptyList(),
                risks = evaluation?.risks ?: emptyList(),
                jobId = finalJob.jobId
            )
        )
        EvaluationOutcome(
            job = finalJob,
            verdict = FilterVerdict.PASS,
            evaluation = evaluation,
            localScore = localScore
        )
    }

    private fun normalizeScore(fitLevel: String, score: Int): Int {
        val level = FitLevel.from(fitLevel)
        val safe = score.coerceIn(0, 100)
        return safe.coerceIn(level.minScore, level.maxScore)
    }
}

object JDCleaner {
    private val disclaimers = listOf(
        "BOSS直聘严厉打击虚假招聘", "如遇招聘方以任何名义索要财物", "防骗指南",
        "根据国家相关法律法规", "联系我时请说明是在BOSS直聘上看到的", "求职谨防诈骗"
    )
    private val noise = listOf(
        "查看更多", "展开全部", "举报", "竞争力分析", "招聘信息", "公司介绍", "工商信息",
        "立即沟通", "继续沟通", "微信", "电话", "地址", "相似职位"
    )

    fun clean(raw: String): String {
        var text = raw.replace("\r", "")
        disclaimers.forEach { text = text.replace(it, "") }
        text = text.replace(Regex("""\n{3,}"""), "\n\n").trim()
        noise.forEach { token ->
            if (text.endsWith(token)) text = text.removeSuffix(token).trim()
        }
        return text.take(8000)
    }
}
