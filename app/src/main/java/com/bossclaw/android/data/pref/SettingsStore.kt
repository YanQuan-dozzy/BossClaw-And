package com.bossclaw.android.data.pref

import android.content.Context
import com.bossclaw.android.model.RunMode
import com.bossclaw.android.model.Speed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val runMode: RunMode = RunMode.SEMI,
    val speed: Speed = Speed.NORMAL,
    val maxDailyGreetings: Int = 120,
    val minSalaryK: Int = 0,
    val filterOutsourcing: Boolean = true,
    val filterHeadhunter: Boolean = true,
    val hrActivityFilter: String = "any",
    val excludedRecruitersCsv: String = "",
    val excludedJobKeywordsCsv: String = "",
    val excludedCitiesCsv: String = "",
    val filterCity: Boolean = true,
    val autoReply: Boolean = true,
    /** 用户愿意提供给 HR 的真实信息（薪资期望、可面试时间、到岗时间等），AI 跟聊只引用这里的内容 */
    val communicationInfo: String = "",
    val floatingHud: Boolean = false,
    val notifications: Boolean = true,
    val themeMode: String = "system",
    val dailyTokenBudget: Int = 300_000,
    val paused: Boolean = false,
    val firstDeliveryDone: Boolean = false
)

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("bossclaw_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings = AppSettings(
        runMode = RunMode.from(prefs.getString(KEY_RUN_MODE, null)),
        speed = Speed.from(prefs.getString(KEY_SPEED, null)),
        maxDailyGreetings = prefs.getInt(KEY_DAILY_GREETINGS, 120),
        minSalaryK = prefs.getInt(KEY_MIN_SALARY, 0),
        filterOutsourcing = prefs.getBoolean(KEY_FILTER_OUTSOURCING, true),
        filterHeadhunter = prefs.getBoolean(KEY_FILTER_HEADHUNTER, true),
        hrActivityFilter = prefs.getString(KEY_HR_ACTIVITY, "any") ?: "any",
        excludedRecruitersCsv = prefs.getString(KEY_EXCLUDED_RECRUITERS, "") ?: "",
        excludedJobKeywordsCsv = prefs.getString(KEY_EXCLUDED_JOB_KEYWORDS, "") ?: "",
        excludedCitiesCsv = prefs.getString(KEY_EXCLUDED_CITIES, "") ?: "",
        filterCity = prefs.getBoolean(KEY_FILTER_CITY, true),
        autoReply = prefs.getBoolean(KEY_AUTO_REPLY, true),
        communicationInfo = prefs.getString(KEY_COMMUNICATION_INFO, "") ?: "",
        floatingHud = prefs.getBoolean(KEY_FLOATING_HUD, false),
        notifications = prefs.getBoolean(KEY_NOTIFICATIONS, true),
        themeMode = prefs.getString(KEY_THEME, "system") ?: "system",
        dailyTokenBudget = prefs.getInt(KEY_TOKEN_BUDGET, 300_000),
        paused = prefs.getBoolean(KEY_PAUSED, false),
        firstDeliveryDone = prefs.getBoolean(KEY_FIRST_DELIVERY, false)
    )

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
            .putString(KEY_SPEED, settings.speed.raw)
            .putInt(KEY_DAILY_GREETINGS, settings.maxDailyGreetings)
            .putInt(KEY_MIN_SALARY, settings.minSalaryK)
            .putBoolean(KEY_FILTER_OUTSOURCING, settings.filterOutsourcing)
            .putBoolean(KEY_FILTER_HEADHUNTER, settings.filterHeadhunter)
            .putString(KEY_HR_ACTIVITY, settings.hrActivityFilter)
            .putString(KEY_EXCLUDED_RECRUITERS, settings.excludedRecruitersCsv)
            .putString(KEY_EXCLUDED_JOB_KEYWORDS, settings.excludedJobKeywordsCsv)
            .putString(KEY_EXCLUDED_CITIES, settings.excludedCitiesCsv)
            .putBoolean(KEY_FILTER_CITY, settings.filterCity)
            .putBoolean(KEY_AUTO_REPLY, settings.autoReply)
            .putString(KEY_COMMUNICATION_INFO, settings.communicationInfo)
            .putBoolean(KEY_FLOATING_HUD, settings.floatingHud)
            .putBoolean(KEY_NOTIFICATIONS, settings.notifications)
            .putString(KEY_THEME, settings.themeMode)
            .putInt(KEY_TOKEN_BUDGET, settings.dailyTokenBudget)
            .putBoolean(KEY_PAUSED, settings.paused)
            .putBoolean(KEY_FIRST_DELIVERY, settings.firstDeliveryDone)
            .apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
        _settings.value = AppSettings()
    }

    private companion object {
        const val KEY_RUN_MODE = "run_mode"
        const val KEY_SPEED = "speed"
        const val KEY_DAILY_GREETINGS = "daily_greetings"
        const val KEY_MIN_SALARY = "min_salary"
        const val KEY_FILTER_OUTSOURCING = "filter_outsourcing"
        const val KEY_FILTER_HEADHUNTER = "filter_headhunter"
        const val KEY_HR_ACTIVITY = "hr_activity"
        const val KEY_EXCLUDED_RECRUITERS = "excluded_recruiters"
        const val KEY_EXCLUDED_JOB_KEYWORDS = "excluded_job_keywords"
        const val KEY_EXCLUDED_CITIES = "excluded_cities"
        const val KEY_FILTER_CITY = "filter_city"
        const val KEY_AUTO_REPLY = "auto_reply"
        const val KEY_COMMUNICATION_INFO = "communication_info"
        const val KEY_FLOATING_HUD = "floating_hud"
        const val KEY_NOTIFICATIONS = "notifications"
        const val KEY_THEME = "theme"
        const val KEY_TOKEN_BUDGET = "token_budget"
        const val KEY_PAUSED = "paused"
        const val KEY_FIRST_DELIVERY = "first_delivery"
    }
}
