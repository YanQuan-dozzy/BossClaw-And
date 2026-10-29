package com.bossclaw.android.data.repo

import com.bossclaw.android.data.pref.AppSettings
import com.bossclaw.android.data.pref.SecureStore
import com.bossclaw.android.data.pref.SettingsStore
import com.bossclaw.android.llm.LlmConfig
import kotlinx.coroutines.flow.StateFlow

class ConfigRepository(
    private val secureStore: SecureStore,
    private val settingsStore: SettingsStore
) {
    val settings: StateFlow<AppSettings> = settingsStore.settings

    fun llmConfig(): LlmConfig = secureStore.loadLlmConfig()
    fun saveLlmConfig(config: LlmConfig) = secureStore.saveLlmConfig(config)

    fun resumeText(): String = secureStore.resumeText()
    fun saveResume(text: String, fileName: String) {
        secureStore.saveResumeText(text)
        secureStore.saveResumeFileName(fileName)
    }
    fun resumeFileName(): String = secureStore.resumeFileName()

    fun updateSettings(transform: (AppSettings) -> AppSettings): AppSettings =
        settingsStore.update(transform)

    fun setPaused(paused: Boolean) = settingsStore.setPaused(paused)
    fun markFirstDelivery() = settingsStore.markFirstDelivery()

    fun resetAll() {
        secureStore.clearAll()
        settingsStore.reset()
    }
}
