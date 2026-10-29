package com.bossclaw.android.llm

enum class LlmProtocol(val raw: String, val label: String) {
    OPENAI_CHAT("openai_chat", "OpenAI 兼容"),
    ANTHROPIC_MESSAGES("anthropic_messages", "Anthropic Messages");
}

data class LlmConfig(
    val provider: String = "deepseek",
    val protocol: LlmProtocol = LlmProtocol.OPENAI_CHAT,
    val baseUrl: String = "https://api.deepseek.com",
    val apiKey: String = "",
    val model: String = "deepseek-flash",
    val temperature: Float = 0.3f,
    val maxTokens: Int = 4096,
    /** 模型实际上下文窗口（token），影响每次请求能投喂多少简历与 JD */
    val contextWindowTokens: Int = ContextBudget.DEFAULT_WINDOW,
    /** 上下文用量档位：full 吃满窗口，compact 只用 40% */
    val contextUsage: String = ContextUsage.FULL.raw,
    /** 是否开启思考模式（仅对已识别支持的模型生效） */
    val thinkingEnabled: Boolean = false,
    /** 思考强度档位（取值随模型能力而定，空表示使用模型默认档） */
    val thinkingEffort: String = ""
) {
    val isReady: Boolean
        get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()

    val endpoint: String
        get() = baseUrl.trim().trimEnd('/')

    fun maskedKey(): String = when {
        apiKey.isBlank() -> "未配置"
        apiKey.length <= 8 -> "已配置"
        else -> "${apiKey.take(4)}••••${apiKey.takeLast(4)}"
    }
}

data class ProviderPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val protocol: LlmProtocol = LlmProtocol.OPENAI_CHAT,
    val note: String = ""
)

object LlmPresets {
    // 模型名以官方当前口径为准，换模型只改这里。
    val all: List<ProviderPreset> = listOf(
        ProviderPreset(
            "deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-flash",
            note = "DeepSeek-V4.1-Flash，性价比高"
        ),
        ProviderPreset(
            "openai", "OpenAI", "https://api.openai.com/v1", "gpt-5.6-luna",
            note = "需要海外网络"
        ),
        ProviderPreset(
            "qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3.8-max",
            note = "阿里云百炼"
        ),
        ProviderPreset(
            "zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-5.3",
            note = "国内直连"
        ),
        ProviderPreset(
            "siliconflow", "硅基流动", "https://api.siliconflow.cn/v1", "Qwen/Qwen3.8-2.4T-A95B",
            note = "多模型聚合，模型名用「组织/模型」全名"
        ),
        ProviderPreset(
            "volces", "火山方舟", "https://ark.cn-beijing.volces.com/api/v3", "doubao-seed-2.1-pro",
            note = "字节豆包"
        ),
        ProviderPreset("custom", "自定义兼容接口", "", "", note = "任意 OpenAI 兼容网关")
    )

    fun find(id: String): ProviderPreset = all.firstOrNull { it.id == id } ?: all.first()
}
