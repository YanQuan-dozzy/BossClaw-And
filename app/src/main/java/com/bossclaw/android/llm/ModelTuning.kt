package com.bossclaw.android.llm

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * 模型侧的两个可调项：思考模式与上下文预算。
 * 这里集中声明「哪个模型能不能思考、开关字段叫什么、强度档有哪些」以及
 * 「一次请求能投喂多少上下文」，设置页与请求构造共用同一份结论，避免各写一套。
 */

// ==================== 思考模式 ====================

enum class ThinkingMode { UNSUPPORTED, TOGGLEABLE, ALWAYS_ON }

data class ThinkingProfile(
    val mode: ThinkingMode,
    val on: Map<String, Any> = emptyMap(),
    /** 关闭思考时并入请求体的字段；null 表示服务端固定开启、没有可用的关闭写法 */
    val off: Map<String, Any>? = null,
    val effortField: String? = null,
    val efforts: List<String> = emptyList(),
    val defaultEffort: String = "",
    val labels: Map<String, String> = emptyMap(),
    val note: String = ""
) {
    val supported: Boolean get() = mode != ThinkingMode.UNSUPPORTED

    /** 当前实际生效的思考状态：不支持的模型恒为关闭，固定开启的模型恒为开启。 */
    fun isActive(preference: Boolean): Boolean = when (mode) {
        ThinkingMode.UNSUPPORTED -> false
        ThinkingMode.ALWAYS_ON -> true
        ThinkingMode.TOGGLEABLE -> preference
    }

    fun resolveEffort(preference: String): String =
        if (preference in efforts) preference else defaultEffort.ifBlank { efforts.firstOrNull().orEmpty() }

    fun effortLabel(raw: String): String = labels[raw] ?: raw
}

object ThinkingProfiles {

    private val UNSUPPORTED = ThinkingProfile(
        mode = ThinkingMode.UNSUPPORTED,
        note = "当前模型未验证支持思考模式，不会附加任何思考参数"
    )

    private val DEEPSEEK = ThinkingProfile(
        mode = ThinkingMode.TOGGLEABLE,
        on = mapOf("thinking" to mapOf("type" to "enabled")),
        off = mapOf("thinking" to mapOf("type" to "disabled")),
        effortField = "reasoning_effort",
        efforts = listOf("low", "high", "max"),
        defaultEffort = "high",
        labels = mapOf("low" to "快速", "high" to "标准", "max" to "最高"),
        note = "开启后思维链计入输出 token；思考模式下 temperature 不生效"
    )

    private val GLM = ThinkingProfile(
        mode = ThinkingMode.TOGGLEABLE,
        on = mapOf("thinking" to mapOf("type" to "enabled")),
        off = mapOf("thinking" to mapOf("type" to "disabled")),
        note = "GLM 系列思考开关由 thinking 字段控制，本应用不调整其强度档"
    )

    private val GLM_ALWAYS_ON = ThinkingProfile(
        mode = ThinkingMode.ALWAYS_ON,
        on = mapOf("thinking" to mapOf("type" to "enabled")),
        off = null,
        note = "GLM-5.3 思考固定开启，传 disabled 会请求失败"
    )

    private val QWEN = ThinkingProfile(
        mode = ThinkingMode.TOGGLEABLE,
        on = mapOf("enable_thinking" to true),
        off = mapOf("enable_thinking" to false),
        effortField = "reasoning_effort",
        efforts = listOf("low", "medium", "xhigh"),
        defaultEffort = "medium",
        labels = mapOf("low" to "快速", "medium" to "标准", "xhigh" to "最高"),
        note = "通过 enable_thinking 开关，强度用 reasoning_effort"
    )

    private val DOUBAO = ThinkingProfile(
        mode = ThinkingMode.TOGGLEABLE,
        on = mapOf("thinking" to mapOf("type" to "enabled")),
        off = mapOf("thinking" to mapOf("type" to "disabled")),
        effortField = "reasoning_effort",
        efforts = listOf("minimal", "low", "medium", "high"),
        defaultEffort = "high",
        labels = mapOf("minimal" to "最低", "low" to "快速", "medium" to "标准", "high" to "最高"),
        note = "thinking 开关 + reasoning_effort 强度"
    )

    private val OPENAI = ThinkingProfile(
        mode = ThinkingMode.TOGGLEABLE,
        on = emptyMap(),
        off = mapOf("reasoning_effort" to "none"),
        effortField = "reasoning_effort",
        efforts = listOf("low", "medium", "high", "xhigh", "max"),
        defaultEffort = "medium",
        labels = mapOf(
            "low" to "快速", "medium" to "标准", "high" to "高",
            "xhigh" to "更高", "max" to "最高"
        ),
        note = "通过 reasoning_effort 控制，none 即关闭思考"
    )

    /**
     * 未知模型一律按不支持处理（fail-safe）：宁可少发一个可选参数，
     * 也不能凭猜测发出一个可能让请求失败（400）的字段。
     * Anthropic 协议需要额外的思考预算字段，同样不做猜测。
     */
    fun resolve(provider: String, model: String, baseUrl: String, protocol: LlmProtocol): ThinkingProfile {
        if (protocol != LlmProtocol.OPENAI_CHAT) return UNSUPPORTED
        val name = model.trim().lowercase()
        if (name.isBlank()) return UNSUPPORTED
        val url = baseUrl.lowercase()
        val deepSeekEndpoint = provider == "deepseek" || url.contains("deepseek")

        if (deepSeekEndpoint && name.startsWith("deepseek")) return DEEPSEEK
        if (name.startsWith("gpt-")) return OPENAI
        if (name.startsWith("glm-5.3-flash")) return GLM
        if (name.startsWith("glm-5.3")) return GLM_ALWAYS_ON
        if (name.startsWith("glm-4") || name.startsWith("glm-5")) return GLM
        if (name.startsWith("qwen3")) return QWEN
        if (name.startsWith("doubao-seed")) return DOUBAO
        return UNSUPPORTED
    }

    fun resolve(config: LlmConfig): ThinkingProfile =
        resolve(config.provider, config.model, config.baseUrl, config.protocol)

    /** 按模型能力把思考参数并入请求体；不支持的模型保持原样。 */
    fun applyToPayload(config: LlmConfig, payload: MutableMap<String, Any>) {
        val profile = resolve(config)
        if (!profile.supported) return
        if (profile.isActive(config.thinkingEnabled)) {
            payload.putAll(profile.on)
            val field = profile.effortField
            if (field != null && profile.efforts.isNotEmpty()) {
                payload[field] = profile.resolveEffort(config.thinkingEffort)
            }
        } else {
            profile.off?.let { payload.putAll(it) }
        }
    }
}

// ==================== 上下文预算 ====================

enum class ContextUsage(val raw: String, val label: String, val ratio: Float) {
    FULL("full", "全满", 1f),
    COMPACT("compact", "40%", 0.4f);

    companion object {
        fun from(raw: String?): ContextUsage = entries.firstOrNull { it.raw == raw } ?: FULL
    }
}

/**
 * 由用户声明的模型窗口驱动：可投喂输入 = 窗口 × 用量档位 − 输出预留 − 安全边际。
 * 调用方只声明「这段文本占预算的多少」，不再各自写死截断字数。
 */
class ContextBudget(val windowTokens: Int, val usage: ContextUsage) {

    val inputTokens: Int = max(
        MIN_INPUT_TOKENS,
        floor(windowTokens * usage.ratio).toInt() - OUTPUT_RESERVE - SAFETY_MARGIN
    )

    val note: String
        get() = "${format(windowTokens)} 窗口 × ${usage.label}" +
            "（${(usage.ratio * 100).toInt()}%）− 输出预留 ${format(OUTPUT_RESERVE)}" +
            " − 安全边际 ${format(SAFETY_MARGIN)}" +
            " → 可投喂约 ${format(inputTokens)} tokens"

    /** 按份额裁剪一段上下文；估算一律偏高，宁可少喂也不超窗。 */
    fun fit(text: String, share: Float = 1f): String {
        if (text.isBlank()) return text
        val limit = max(1, (inputTokens * share.coerceIn(0f, 1f)).toInt())
        if (estimateTokens(text) <= limit) return text
        val markTokens = estimateTokens(MARK)
        val bodyLimit = limit - markTokens
        if (bodyLimit <= 0) return ""
        val cut = alignToBoundary(text, cutIndexByTokens(text, bodyLimit))
        return if (cut <= 0) "" else text.substring(0, cut) + MARK
    }

    companion object {
        const val DEFAULT_WINDOW = 128_000
        const val MIN_WINDOW = 32_000
        const val MAX_WINDOW = 2_000_000
        const val OUTPUT_RESERVE = 4_096
        const val SAFETY_MARGIN = 2_000
        private const val MIN_INPUT_TOKENS = 1_500
        private const val MARK = "\n…（上下文超出当前窗口预算，后续内容已省略）"

        /** 设置页可选窗口预设（token 数 → 展示名）。 */
        val WINDOW_PRESETS: List<Pair<Int, String>> = listOf(
            1_000_000 to "1M",
            512_000 to "512K",
            256_000 to "256K",
            128_000 to "128K",
            64_000 to "64K",
            32_000 to "32K"
        )

        val DEFAULT: ContextBudget = ContextBudget(DEFAULT_WINDOW, ContextUsage.FULL)

        fun of(config: LlmConfig): ContextBudget =
            ContextBudget(normalizeWindow(config.contextWindowTokens), ContextUsage.from(config.contextUsage))

        fun normalizeWindow(raw: Int): Int =
            if (raw <= 0) DEFAULT_WINDOW else raw.coerceIn(MIN_WINDOW, MAX_WINDOW)

        fun format(tokens: Int): String = when {
            tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
            tokens >= 1_000 -> "${tokens / 1_000}K"
            else -> tokens.toString()
        }

        /** 宽字符按 1 字 1 token，其余约 0.34 token/字符，整体偏高估算。 */
        fun estimateTokens(text: String): Int {
            if (text.isEmpty()) return 0
            var wide = 0
            for (char in text) {
                if (char.code >= 0x2E80) wide++
            }
            val narrow = max(0, text.length - wide)
            return wide + ceil(narrow * 0.34).toInt()
        }

        private fun cutIndexByTokens(text: String, limit: Int): Int {
            val total = estimateTokens(text)
            if (total <= limit) return text.length
            var cut = (text.length * (limit.toDouble() / total)).toInt()
            repeat(6) {
                if (cut <= 0) return 0
                if (estimateTokens(text.substring(0, cut)) <= limit) return cut
                cut = (cut * 0.92).toInt()
            }
            return cut.coerceIn(0, text.length)
        }

        /** 截断点回退到最近的换行/句末，避免把半句话当完整事实。 */
        private fun alignToBoundary(text: String, cut: Int): Int {
            if (cut >= text.length) return cut
            val floor = max(0, cut - (cut * 0.15).toInt())
            for (i in cut downTo floor + 1) {
                when (text[i - 1]) {
                    '\n', '。', '；', '！', '？' -> return i
                }
            }
            return cut
        }
    }
}
