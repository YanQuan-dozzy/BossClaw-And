package com.bossclaw.android.llm

/** AI 技能作用域：仅覆盖安卓端已实现的调用点。 */
object SkillScope {
    const val PROFILE = "profile"
    const val JOB_ANALYSIS = "job-analysis"
    const val GREETINGS = "greetings"
    const val ASSISTANT = "assistant"

    val all = listOf(PROFILE, JOB_ANALYSIS, GREETINGS, ASSISTANT)

    private val labels = mapOf(
        PROFILE to "职业画像",
        JOB_ANALYSIS to "岗位评估",
        GREETINGS to "打招呼",
        ASSISTANT to "定制/助手"
    )

    fun label(scope: String?): String = labels[scope] ?: "其他"
}
