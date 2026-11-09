package com.bossclaw.android.data.pref

import android.content.Context
import com.bossclaw.android.model.RunMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val runMode: RunMode = RunMode.SEMI,
    /** 自动运行门槛：评分达到该值才自动发送，低于则进入待确认队列；下限固定为匹配档，避免自动发送谨慎或不推荐岗位 */
    val autoSendMinScore: Int = DEFAULT_AUTO_SEND_SCORE,
    /** 岗位投递间隔（秒），由用户自定义；执行时自动加入随机抖动 */
    val jobIntervalSeconds: Int = 20,
    val maxDailyGreetings: Int = 120,
    val minSalaryK: Int = 0,
    /** 薪资口径：false 按月薪（K）比较，true 按日薪（元/天）比较；实习与兼职岗位多为日薪 */
    val salaryDaily: Boolean = false,
    val minDailySalary: Int = 0,
    /** 实习时长下限（月，0=不限），可自由填写任意月数 */
    val internshipMinMonths: Int = 0,
    /** 每周出勤下限（天，0=不限），可自由填写 1–7 天 */
    val weeklyMinDays: Int = 0,
    /** 月薪档位（对齐 BOSS「薪资待遇」单选档；any=全部，选中档位时按档位区间比较） */
    val salaryTier: String = "any",
    /** 学历要求（auto=跟随画像，none=不限，其余为 BOSS 面板学历档，按「岗位要求不高于所选」判定） */
    val degreeRequirement: String = "auto",
    /** 经验要求上限（any=不限，其余为 BOSS 面板经验档，高于所选档的岗位跳过） */
    val experienceCeiling: String = "any",
    val filterOutsourcing: Boolean = true,
    val filterHeadhunter: Boolean = true,
    val hrActivityFilter: String = "any",
    val excludedRecruitersCsv: String = "",
    val excludedJobKeywordsCsv: String = "",
    val excludedCitiesCsv: String = "",
    val filterCity: Boolean = true,
    /** 无关键字匹配：直接读取 BOSS「推荐」流，不再按岗位关键词翻找，避免同词反复重试 */
    val collectWithoutKeyword: Boolean = true,
    val autoReply: Boolean = true,
    /** 打招呼方式：true 用 AI 生成的自定义开场白，false 直接使用 BOSS 官方招呼语 */
    val customGreeting: Boolean = false,
    /** 用户愿意提供给 HR 的真实信息（薪资期望、可面试时间、到岗时间等），AI 跟聊只引用这里的内容 */
    val communicationInfo: String = "",
    val floatingHud: Boolean = true,
    val notifications: Boolean = true,
    val themeMode: String = "system",
    val paused: Boolean = false,
    val firstDeliveryDone: Boolean = false,
    /** 首次使用引导是否已看过；关闭后不再自动弹出，可随时从侧栏打开使用文档 */
    val onboardingSeen: Boolean = false
) {
    companion object {
        const val DEFAULT_AUTO_SEND_SCORE = 70
        /** 旧版本默认门槛，升级时用于识别未手动修改过的安装 */
        const val LEGACY_AUTO_SEND_SCORE = 81
        /** 低于匹配档（65 分）属于谨慎或不推荐，不允许自动发送 */
        const val MIN_AUTO_SEND_SCORE = 65
        const val MAX_AUTO_SEND_SCORE = 100
    }
}

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("bossclaw_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings {
        // 悬浮球改为默认开启：旧安装已写入 false 时只在首次升级时纠正一次，之后尊重用户手动关闭
        if (!prefs.getBoolean(KEY_FLOATING_HUD_MIGRATED, false)) {
            prefs.edit()
                .putBoolean(KEY_FLOATING_HUD, true)
                .putBoolean(KEY_FLOATING_HUD_MIGRATED, true)
                .apply()
        }
        // 自动发送门槛默认值下调到匹配档：未手动改过的旧安装一次性迁移，避免岗位一直停在待确认
        if (!prefs.getBoolean(KEY_AUTO_SEND_SCORE_MIGRATED, false)) {
            val stored = prefs.getInt(KEY_AUTO_SEND_SCORE, AppSettings.LEGACY_AUTO_SEND_SCORE)
            if (stored == AppSettings.LEGACY_AUTO_SEND_SCORE) {
                prefs.edit().putInt(KEY_AUTO_SEND_SCORE, AppSettings.DEFAULT_AUTO_SEND_SCORE).apply()
            }
            prefs.edit().putBoolean(KEY_AUTO_SEND_SCORE_MIGRATED, true).apply()
        }
        return AppSettings(
        runMode = RunMode.from(prefs.getString(KEY_RUN_MODE, null)),
        autoSendMinScore = prefs.getInt(KEY_AUTO_SEND_SCORE, AppSettings.DEFAULT_AUTO_SEND_SCORE)
            .coerceIn(AppSettings.MIN_AUTO_SEND_SCORE, AppSettings.MAX_AUTO_SEND_SCORE),
        jobIntervalSeconds = prefs.getInt(KEY_JOB_INTERVAL, 20),
        maxDailyGreetings = prefs.getInt(KEY_DAILY_GREETINGS, 120),
        minSalaryK = prefs.getInt(KEY_MIN_SALARY, 0),
        salaryDaily = prefs.getBoolean(KEY_SALARY_DAILY, false),
        minDailySalary = prefs.getInt(KEY_MIN_DAILY_SALARY, 0),
        internshipMinMonths = prefs.getInt(KEY_INTERN_MONTHS, 0),
        weeklyMinDays = prefs.getInt(KEY_WEEKLY_DAYS, 0),
        salaryTier = prefs.getString(KEY_SALARY_TIER, "any") ?: "any",
        degreeRequirement = prefs.getString(KEY_DEGREE_REQUIREMENT, "auto") ?: "auto",
        experienceCeiling = prefs.getString(KEY_EXPERIENCE_CEILING, "any") ?: "any",
        filterOutsourcing = prefs.getBoolean(KEY_FILTER_OUTSOURCING, true),
        filterHeadhunter = prefs.getBoolean(KEY_FILTER_HEADHUNTER, true),
        hrActivityFilter = prefs.getString(KEY_HR_ACTIVITY, "any") ?: "any",
        excludedRecruitersCsv = prefs.getString(KEY_EXCLUDED_RECRUITERS, "") ?: "",
        excludedJobKeywordsCsv = prefs.getString(KEY_EXCLUDED_JOB_KEYWORDS, "") ?: "",
        excludedCitiesCsv = prefs.getString(KEY_EXCLUDED_CITIES, "") ?: "",
        filterCity = prefs.getBoolean(KEY_FILTER_CITY, true),
        collectWithoutKeyword = prefs.getBoolean(KEY_COLLECT_WITHOUT_KEYWORD, true),
        autoReply = prefs.getBoolean(KEY_AUTO_REPLY, true),
        customGreeting = prefs.getBoolean(KEY_CUSTOM_GREETING, false),
        communicationInfo = prefs.getString(KEY_COMMUNICATION_INFO, "") ?: "",
        floatingHud = prefs.getBoolean(KEY_FLOATING_HUD, true),
        notifications = prefs.getBoolean(KEY_NOTIFICATIONS, true),
        themeMode = prefs.getString(KEY_THEME, "system") ?: "system",
        paused = prefs.getBoolean(KEY_PAUSED, false),
        firstDeliveryDone = prefs.getBoolean(KEY_FIRST_DELIVERY, false),
        onboardingSeen = prefs.getBoolean(KEY_ONBOARDING_SEEN, false)
        )
    }

    fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        val next = transform(_settings.value)
        persist(next)
        _settings.value = next
        return next
    }

    fun setPaused(paused: Boolean) = update { it.copy(paused = paused) }

    fun markFirstDelivery() = update { it.copy(firstDeliveryDone = true) }

    private fun persist(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_RUN_MODE, settings.runMode.raw)
            .putInt(KEY_AUTO_SEND_SCORE, settings.autoSendMinScore)
            .putInt(KEY_JOB_INTERVAL, settings.jobIntervalSeconds)
            .putInt(KEY_DAILY_GREETINGS, settings.maxDailyGreetings)
            .putInt(KEY_MIN_SALARY, settings.minSalaryK)
            .putBoolean(KEY_SALARY_DAILY, settings.salaryDaily)
            .putInt(KEY_MIN_DAILY_SALARY, settings.minDailySalary)
            .putInt(KEY_INTERN_MONTHS, settings.internshipMinMonths)
            .putInt(KEY_WEEKLY_DAYS, settings.weeklyMinDays)
            .putString(KEY_SALARY_TIER, settings.salaryTier)
            .putString(KEY_DEGREE_REQUIREMENT, settings.degreeRequirement)
            .putString(KEY_EXPERIENCE_CEILING, settings.experienceCeiling)
            .putBoolean(KEY_FILTER_OUTSOURCING, settings.filterOutsourcing)
            .putBoolean(KEY_FILTER_HEADHUNTER, settings.filterHeadhunter)
            .putString(KEY_HR_ACTIVITY, settings.hrActivityFilter)
            .putString(KEY_EXCLUDED_RECRUITERS, settings.excludedRecruitersCsv)
            .putString(KEY_EXCLUDED_JOB_KEYWORDS, settings.excludedJobKeywordsCsv)
            .putString(KEY_EXCLUDED_CITIES, settings.excludedCitiesCsv)
            .putBoolean(KEY_FILTER_CITY, settings.filterCity)
            .putBoolean(KEY_COLLECT_WITHOUT_KEYWORD, settings.collectWithoutKeyword)
            .putBoolean(KEY_AUTO_REPLY, settings.autoReply)
            .putBoolean(KEY_CUSTOM_GREETING, settings.customGreeting)
            .putString(KEY_COMMUNICATION_INFO, settings.communicationInfo)
            .putBoolean(KEY_FLOATING_HUD, settings.floatingHud)
            .putBoolean(KEY_NOTIFICATIONS, settings.notifications)
            .putString(KEY_THEME, settings.themeMode)
            .putBoolean(KEY_PAUSED, settings.paused)
            .putBoolean(KEY_FIRST_DELIVERY, settings.firstDeliveryDone)
            .putBoolean(KEY_ONBOARDING_SEEN, settings.onboardingSeen)
            .apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
        _settings.value = AppSettings()
    }

    private companion object {
        const val KEY_RUN_MODE = "run_mode"
        const val KEY_AUTO_SEND_SCORE = "auto_send_min_score"
        const val KEY_AUTO_SEND_SCORE_MIGRATED = "auto_send_score_default_migrated"
        const val KEY_JOB_INTERVAL = "job_interval_seconds"
        const val KEY_DAILY_GREETINGS = "daily_greetings"
        const val KEY_MIN_SALARY = "min_salary"
        const val KEY_SALARY_DAILY = "salary_daily"
        const val KEY_MIN_DAILY_SALARY = "min_daily_salary"
        const val KEY_INTERN_MONTHS = "internship_min_months"
        const val KEY_WEEKLY_DAYS = "weekly_min_days"
        const val KEY_SALARY_TIER = "salary_tier"
        const val KEY_DEGREE_REQUIREMENT = "degree_requirement"
        const val KEY_EXPERIENCE_CEILING = "experience_ceiling"
        const val KEY_FILTER_OUTSOURCING = "filter_outsourcing"
        const val KEY_FILTER_HEADHUNTER = "filter_headhunter"
        const val KEY_HR_ACTIVITY = "hr_activity"
        const val KEY_EXCLUDED_RECRUITERS = "excluded_recruiters"
        const val KEY_EXCLUDED_JOB_KEYWORDS = "excluded_job_keywords"
        const val KEY_EXCLUDED_CITIES = "excluded_cities"
        const val KEY_FILTER_CITY = "filter_city"
        const val KEY_COLLECT_WITHOUT_KEYWORD = "collect_without_keyword"
        const val KEY_AUTO_REPLY = "auto_reply"
        const val KEY_CUSTOM_GREETING = "custom_greeting"
        const val KEY_COMMUNICATION_INFO = "communication_info"
        const val KEY_FLOATING_HUD = "floating_hud"
        const val KEY_FLOATING_HUD_MIGRATED = "floating_hud_default_migrated"
        const val KEY_NOTIFICATIONS = "notifications"
        const val KEY_THEME = "theme"
        const val KEY_PAUSED = "paused"
        const val KEY_FIRST_DELIVERY = "first_delivery"
        const val KEY_ONBOARDING_SEEN = "onboarding_seen"
    }
}
