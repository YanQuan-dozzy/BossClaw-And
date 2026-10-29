package com.bossclaw.android.llm

import com.bossclaw.android.core.JsonCodec
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class LlmException(
    message: String,
    cause: Throwable? = null,
    /** 429 / 5xx / 408 等瞬时错误可安全重试，最多 3 次退避重试 */
    val retryable: Boolean = false
) : IOException(message, cause)

data class LlmCallResult(
    val content: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val durationMs: Long
)

class OpenAiCompatibleTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(140, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private companion object {
        const val MAX_ATTEMPTS = 3
    }

    suspend fun chat(
        config: LlmConfig,
        systemPrompt: String,
        userPrompt: String,
        temperature: Float = config.temperature,
        maxTokens: Int = config.maxTokens
    ): LlmCallResult {
        if (!config.isReady) throw LlmException("LLM 配置不完整，请先在设置中填写 API Key / Base URL / 模型")
        var lastError: Exception? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            val started = System.currentTimeMillis()
            try {
                return when (config.protocol) {
                    LlmProtocol.OPENAI_CHAT -> callOpenAi(config, systemPrompt, userPrompt, temperature, maxTokens, started)
                    LlmProtocol.ANTHROPIC_MESSAGES -> callAnthropic(config, systemPrompt, userPrompt, temperature, maxTokens, started)
                }
            } catch (error: LlmException) {
                if (!error.retryable || attempt == MAX_ATTEMPTS - 1) throw error
                lastError = error
            } catch (error: IOException) {
                if (attempt == MAX_ATTEMPTS - 1) throw error
                lastError = error
            }
            delay((attempt + 1) * 1000L)
        }
        throw lastError ?: LlmException("模型调用失败")
    }

    private fun callOpenAi(
        config: LlmConfig,
        systemPrompt: String,
        userPrompt: String,
        temperature: Float,
        maxTokens: Int,
        started: Long
    ): LlmCallResult {
        val payload = mutableMapOf<String, Any>(
            "model" to config.model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userPrompt)
            ),
            "temperature" to temperature,
            "max_tokens" to maxTokens,
            "stream" to false
        )
        // 只对已识别支持的模型并入思考参数，未知模型保持原样，避免 400
        ThinkingProfiles.applyToPayload(config, payload)
        val request = Request.Builder()
            .url("${config.endpoint}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(JsonCodec.encode(payload).toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw LlmException(
                errorMessage(response.code, raw),
                retryable = isRetryableStatus(response.code)
            )
            val root = parseObject(raw)
            val content = root.getAsJsonArray("choices")
                ?.firstOrNull()
                ?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")
                ?.asString
                ?: throw LlmException("模型返回格式异常：缺少 choices[0].message.content")
            val usage = root.getAsJsonObject("usage")
            val promptTokens = usage?.get("prompt_tokens")?.asInt ?: 0
            val completionTokens = usage?.get("completion_tokens")?.asInt ?: 0
            val total = usage?.get("total_tokens")?.asInt ?: (promptTokens + completionTokens)
            return LlmCallResult(content, promptTokens, completionTokens, total, System.currentTimeMillis() - started)
        }
    }

    private fun callAnthropic(
        config: LlmConfig,
        systemPrompt: String,
        userPrompt: String,
        temperature: Float,
        maxTokens: Int,
        started: Long
    ): LlmCallResult {
        val base = config.endpoint
        val url = if (base.endsWith("/v1")) "$base/messages" else "$base/v1/messages"
        val payload = mapOf(
            "model" to config.model,
            "system" to systemPrompt,
            "messages" to listOf(mapOf("role" to "user", "content" to userPrompt)),
            "temperature" to temperature,
            "max_tokens" to maxTokens
        )
        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(JsonCodec.encode(payload).toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw LlmException(
                errorMessage(response.code, raw),
                retryable = isRetryableStatus(response.code)
            )
            val root = parseObject(raw)
            val content = root.getAsJsonArray("content")
                ?.firstOrNull()
                ?.asJsonObject
                ?.get("text")
                ?.asString
                ?: throw LlmException("模型返回格式异常：缺少 content[0].text")
            val usage = root.getAsJsonObject("usage")
            val promptTokens = usage?.get("input_tokens")?.asInt ?: 0
            val completionTokens = usage?.get("output_tokens")?.asInt ?: 0
            return LlmCallResult(content, promptTokens, completionTokens, promptTokens + completionTokens, System.currentTimeMillis() - started)
        }
    }

    private fun parseObject(raw: String): JsonObject {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return runCatching { JsonParser.parseString(cleaned).asJsonObject }
            .getOrElse { throw LlmException("无法解析模型返回：${raw.take(120)}") }
    }

    private fun isRetryableStatus(code: Int): Boolean = code == 408 || code == 429 || code in 500..599

    private fun errorMessage(code: Int, raw: String): String {
        val detail = runCatching {
            JsonParser.parseString(raw).asJsonObject
                .getAsJsonObject("error")?.get("message")?.asString
        }.getOrNull() ?: raw.take(180)
        return when (code) {
            401, 403 -> "鉴权失败（HTTP $code），请检查 API Key 与 Base URL"
            404 -> "接口地址不存在（HTTP 404），请检查 Base URL 是否需要 /v1"
            429 -> "请求过于频繁或额度不足（HTTP 429）"
            in 500..599 -> "模型服务暂时不可用（HTTP $code）"
            else -> "模型调用失败（HTTP $code）：$detail"
        }
    }
}
