package com.bossclaw.android.data.pref

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bossclaw.android.llm.LlmConfig
import com.bossclaw.android.llm.LlmProtocol

class SecureStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "bossclaw_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun loadLlmConfig(): LlmConfig {
        val presetDefault = LlmConfig()
        return LlmConfig(
            provider = prefs.getString(KEY_PROVIDER, presetDefault.provider) ?: presetDefault.provider,
            protocol = LlmProtocol.entries.firstOrNull { it.raw == prefs.getString(KEY_PROTOCOL, null) }
                ?: presetDefault.protocol,
            baseUrl = prefs.getString(KEY_BASE_URL, presetDefault.baseUrl) ?: presetDefault.baseUrl,
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            model = prefs.getString(KEY_MODEL, presetDefault.model) ?: presetDefault.model,
            temperature = prefs.getFloat(KEY_TEMPERATURE, presetDefault.temperature),
            maxTokens = prefs.getInt(KEY_MAX_TOKENS, presetDefault.maxTokens),
            contextWindowTokens = prefs.getInt(KEY_CONTEXT_WINDOW, presetDefault.contextWindowTokens),
            contextUsage = prefs.getString(KEY_CONTEXT_USAGE, presetDefault.contextUsage)
                ?: presetDefault.contextUsage,
            thinkingEnabled = prefs.getBoolean(KEY_THINKING_ENABLED, presetDefault.thinkingEnabled),
            thinkingEffort = prefs.getString(KEY_THINKING_EFFORT, presetDefault.thinkingEffort)
                ?: presetDefault.thinkingEffort
        )
    }

    fun saveLlmConfig(config: LlmConfig) {
        prefs.edit()
            .putString(KEY_PROVIDER, config.provider)
            .putString(KEY_PROTOCOL, config.protocol.raw)
            .putString(KEY_BASE_URL, config.baseUrl)
            .putString(KEY_API_KEY, config.apiKey)
            .putString(KEY_MODEL, config.model)
            .putFloat(KEY_TEMPERATURE, config.temperature)
            .putInt(KEY_MAX_TOKENS, config.maxTokens)
            .putInt(KEY_CONTEXT_WINDOW, config.contextWindowTokens)
            .putString(KEY_CONTEXT_USAGE, config.contextUsage)
            .putBoolean(KEY_THINKING_ENABLED, config.thinkingEnabled)
            .putString(KEY_THINKING_EFFORT, config.thinkingEffort)
            .apply()
    }

    fun resumeText(): String = prefs.getString(KEY_RESUME, "") ?: ""

    fun saveResumeText(text: String) {
        prefs.edit().putString(KEY_RESUME, text).apply()
    }

    fun resumeFileName(): String = prefs.getString(KEY_RESUME_FILE, "") ?: ""

    fun saveResumeFileName(name: String) {
        prefs.edit().putString(KEY_RESUME_FILE, name).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_PROVIDER = "llm_provider"
        const val KEY_PROTOCOL = "llm_protocol"
        const val KEY_BASE_URL = "llm_base_url"
        const val KEY_API_KEY = "llm_api_key"
        const val KEY_MODEL = "llm_model"
        const val KEY_TEMPERATURE = "llm_temperature"
        const val KEY_MAX_TOKENS = "llm_max_tokens"
        const val KEY_CONTEXT_WINDOW = "llm_context_window"
        const val KEY_CONTEXT_USAGE = "llm_context_usage"
        const val KEY_THINKING_ENABLED = "llm_thinking_enabled"
        const val KEY_THINKING_EFFORT = "llm_thinking_effort"
        const val KEY_RESUME = "resume_text"
        const val KEY_RESUME_FILE = "resume_file_name"
    }
}
