package com.bossclaw.android.domain

import com.bossclaw.android.core.JsonCodec
import com.bossclaw.android.core.TimeUtils
import com.bossclaw.android.data.db.JobEntity
import com.bossclaw.android.data.db.ProfileEntity
import com.bossclaw.android.data.pref.AppSettings
import com.bossclaw.android.data.repo.BossClawRepository
import java.util.Calendar
import kotlin.math.roundToInt

data class FilterVerdict(val rejected: Boolean, val reason: String = "") {
    companion object {
        val PASS = FilterVerdict(false)
        fun reject(reason: String) = FilterVerdict(true, reason)
    }
}

/**
 * 确定性岗位过滤：公司、HR 活跃度、地点、JD 关键词与截止日期全部本地判定，
 * 不消耗 AI Token，也不占用投递配额。
 */
class JobFilter(private val repository: BossClawRepository) {

    suspend fun check(job: JobEntity, profile: ProfileEntity?, settings: AppSettings): FilterVerdict {
        val companies = repository.blacklistedCompanyNames()
        val companyHit = matchEitherWay(job.company, companies)
        if (companyHit != null) {
            return FilterVerdict.reject("公司「${job.company}」命中公司黑名单「$companyHit」，已跳过")
        }
        val recruiterHit = matchContains(job.hrName, splitList(settings.excludedRecruitersCsv))
        if (recruiterHit != null) {
            return FilterVerdict.reject("招聘方「${job.hrName}」命中招聘方黑名单「$recruiterHit」，已跳过")
        }

        val companyAndJd = "${job.company} ${job.title} ${job.jd}"
        if (settings.filterOutsourcing && OUTSOURCING.any { companyAndJd.contains(it) }) {
            return FilterVerdict.reject("命中外包/驻场关键词")
        }
        if (settings.filterHeadhunter && HEADHUNTER.any { "${job.company} ${job.title}".contains(it) }) {
            return FilterVerdict.reject("疑似猎头/人力服务岗位")
        }
        if (!HrActivity.meets(job.hrActive, settings.hrActivityFilter)) {
            return FilterVerdict.reject(
                "HR 活跃度「${job.hrActive.ifBlank { "未识别" }}」低于筛选要求「${HrActivity.label(settings.hrActivityFilter)}」"
            )
        }

        val expectedK = maxOf(settings.minSalaryK, profile?.minSalaryK ?: 0)
        if (expectedK > 0) {
            val jobMinK = salaryMinK(job.salaryText)
            if (jobMinK != null && jobMinK < expectedK) {
                return FilterVerdict.reject("薪资下限 ${jobMinK}K 低于期望 ${expectedK}K")
            }
        }

        val excludedCities = splitList(settings.excludedCitiesCsv)
        if (excludedCities.isNotEmpty() && job.city.isNotBlank()) {
            val normalizedCity = job.city.replace("市", "")
            val hit = excludedCities.firstOrNull { normalizedCity.contains(it.replace("市", "")) }
            if (hit != null) return FilterVerdict.reject("城市「${job.city}」命中排除名单「$hit」")
        }

        if (settings.filterCity && profile != null) {
            val targets = JsonCodec.decode(profile.targetCitiesJson, emptyList<String>())
            if (targets.isNotEmpty() && job.city.isNotBlank()) {
                val normalizedJobCity = job.city.replace("市", "")
                val hit = targets.any { target ->
                    val city = target.replace("市", "").trim()
                    city.isNotBlank() && (normalizedJobCity.contains(city) || city.contains(normalizedJobCity))
                }
                val remote = normalizedJobCity.contains("远程") || normalizedJobCity.contains("全国")
                if (!hit && !remote) {
                    return FilterVerdict.reject("城市 ${job.city} 不在目标城市内")
                }
            }
        }

        val degreeVerdict = degreeCheck(profile, job)
        if (degreeVerdict != null) return FilterVerdict.reject(degreeVerdict)

        val keywords = splitList(settings.excludedJobKeywordsCsv)
        if (keywords.isNotEmpty()) {
            val text = normalize("${job.title} ${job.company} ${job.jd}")
            val hit = keywords.firstOrNull { text.contains(normalize(it)) }
            if (hit != null) return FilterVerdict.reject("岗位描述命中排除关键字「$hit」，已跳过")
        }

        val expiryVerdict = expiryCheck("${job.title}\n${job.jd}\n${job.company}")
        if (expiryVerdict != null) return FilterVerdict.reject(expiryVerdict)

        return FilterVerdict.PASS
    }

    /** 公司名双向子串匹配：黑名单「腾讯」命中「腾讯科技（深圳）有限公司」，反之亦然。 */
    private fun matchEitherWay(value: String, entries: List<String>): String? {
        val target = normalize(value)
        if (target.isBlank()) return null
        for (raw in entries) {
            val entry = normalize(raw)
            if (entry.isBlank()) continue
            if (target.contains(entry) || entry.contains(target)) return raw.trim()
        }
        return null
    }

    /** 招聘方姓名单向子串匹配：黑名单「王老师」命中「王老师（HR）」。 */
    private fun matchContains(value: String, entries: List<String>): String? {
        val target = normalize(value)
        if (target.isBlank()) return null
        for (raw in entries) {
            val entry = normalize(raw)
            if (entry.isBlank()) continue
            if (target.contains(entry)) return raw.trim()
        }
        return null
    }

    private fun normalize(value: String): String = value.replace(Regex("""\s+"""), "").lowercase()

    private fun splitList(csv: String): List<String> =
        csv.split('、', '，', ',', ' ', '/', '|', '\n').map { it.trim() }.filter { it.isNotBlank() }

    private fun salaryMinK(text: String): Int? {
        if (text.isBlank()) return null
        val rangeK = Regex("""(\d+(?:\.\d+)?)\s*[-~—至]\s*(\d+(?:\.\d+)?)\s*[kK千]""").find(text)
        if (rangeK != null) return rangeK.groupValues[1].toDouble().roundToInt()
        val singleK = Regex("""(\d+(?:\.\d+)?)\s*[kK]""").find(text)
        if (singleK != null) return singleK.groupValues[1].toDouble().roundToInt()
        val rangeWan = Regex("""(\d+(?:\.\d+)?)\s*[-~—至]\s*(\d+(?:\.\d+)?)\s*[wW万]""").find(text)
        if (rangeWan != null) return (rangeWan.groupValues[1].toDouble() * 10).roundToInt()
        return null
    }

    private fun degreeCheck(profile: ProfileEntity?, job: JobEntity): String? {
        if (profile == null) return null
        val candidate = degreeRank(profile.education) ?: return null
        val required = degreeRank(job.degree) ?: return null
        if (job.degree.contains("不限") || job.degree.contains("无要求")) return null
        if (candidate < required) return "学历要求 ${job.degree}，高于你的 ${profile.education}"
        return null
    }

    private fun degreeRank(text: String): Int? = when {
        text.isBlank() -> null
        text.contains("博士") -> 5
        text.contains("硕士") || text.contains("研究生") -> 4
        text.contains("本科") -> 3
        text.contains("大专") || text.contains("专科") -> 2
        text.contains("高中") || text.contains("中专") -> 1
        else -> null
    }

    // ===== JD 截止日期 =====
    // 只在 JD 显式写了「截止/有效期」标签时判定；日期不可解析一律放行。
    private fun expiryCheck(text: String): String? {
        val deadline = parseDeadline(text) ?: return null
        if (!deadline.expired) return null
        val calendar = Calendar.getInstance().apply { timeInMillis = deadline.ts }
        val ymd = "%04d-%02d-%02d".format(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        return "岗位 JD 标注截止日期 $ymd，已过期"
    }

    private data class Deadline(val ts: Long, val expired: Boolean)

    private fun parseDeadline(text: String): Deadline? {
        if (text.isBlank()) return null
        var match = DEADLINE_LABEL.find(text)
        while (match != null) {
            val start = match.range.first
            val windowEnd = minOf(text.length, start + match.value.length + 24)
            val window = text.substring(start, windowEnd)
            val full = FULL_DATE.find(window)
            if (full != null) {
                val ts = makeDayTs(full.groupValues[1].toInt(), full.groupValues[2].toInt(), full.groupValues[3].toInt())
                if (ts != null) {
                    return Deadline(ts, ts < TimeUtils.todayStart())
                }
            }
            val md = MONTH_DAY.find(window)
            if (md != null) {
                val month = md.groupValues[1].toInt()
                val day = md.groupValues[2].toInt()
                val year = inferYear(month, day)
                val ts = makeDayTs(year, month, day)
                if (ts != null) {
                    return Deadline(ts, ts < TimeUtils.todayStart())
                }
            }
            val next = match.range.last + 1
            if (next <= start) break
            match = DEADLINE_LABEL.find(text, next)
        }
        return null
    }

    private fun makeDayTs(year: Int, month: Int, day: Int): Long? {
        if (year < 2000 || year > 2100) return null
        if (month !in 1..12 || day !in 1..31) return null
        val calendar = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }
        if (calendar.get(Calendar.YEAR) != year ||
            calendar.get(Calendar.MONTH) != month - 1 ||
            calendar.get(Calendar.DAY_OF_MONTH) != day
        ) return null
        return calendar.timeInMillis
    }

    private fun inferYear(month: Int, day: Int): Int {
        val today = TimeUtils.todayStart()
        val thisYear = Calendar.getInstance().apply { timeInMillis = today }.get(Calendar.YEAR)
        val ts = makeDayTs(thisYear, month, day) ?: return thisYear
        val halfYear = 180L * 24 * 3600 * 1000
        return if (today - ts > halfYear) thisYear + 1 else thisYear
    }

    private companion object {
        val OUTSOURCING = listOf("外包", "驻场", "派遣", "外派", "劳务", "BPO", "人力外包", "项目外包")
        val HEADHUNTER = listOf("猎头", "人力资源服务", "人才服务", "劳务派遣")

        val DEADLINE_LABEL = Regex(
            """(?:投递|申请|报名|招聘|简历投递)?(?:截止|截至)\s*(?:日期|时间|日)?|""" +
                """(?<!证书|合同|保修|营业|执照|签证|护照|身份证|统一社会信用代码)有效(?:期|至)|失效(?:日|期)?"""
        )
        val FULL_DATE = Regex("""(\d{4})\s*[年\-/.]\s*(\d{1,2})\s*[月\-/.]\s*(\d{1,2})\s*日?""")
        val MONTH_DAY = Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*日""")
    }
}
