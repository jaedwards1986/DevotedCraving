package com.forgeai.chat.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "forgeai_settings"
)

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light");

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

data class ApiPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val requiresApiKey: Boolean
)

data class SystemPromptTemplate(
    val id: String,
    val title: String,
    val summary: String,
    val prompt: String
)

data class AppSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val modelName: String = DEFAULT_MODEL_NAME,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val useDynamicColor: Boolean = false
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL_NAME = "gpt-4o-mini"
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_MAX_TOKENS = 2048
        const val DEFAULT_SYSTEM_PROMPT =
            "You are ForgeAI, a concise, accurate, and thoughtful AI assistant. " +
                "Format code examples with fenced Markdown code blocks including the language tag, " +
                "explain trade-offs clearly, and prioritize correctness."

        val PRESETS: List<ApiPreset> = listOf(
            ApiPreset(
                id = "openai",
                label = "OpenAI",
                baseUrl = "https://api.openai.com/v1",
                defaultModel = "gpt-4o-mini",
                requiresApiKey = true
            ),
            ApiPreset(
                id = "gemini",
                label = "Gemini",
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                defaultModel = "gemini-2.5-flash",
                requiresApiKey = true
            ),
            ApiPreset(
                id = "openrouter",
                label = "OpenRouter",
                baseUrl = "https://openrouter.ai/api/v1",
                defaultModel = "anthropic/claude-3.5-sonnet",
                requiresApiKey = true
            ),
            ApiPreset(
                id = "groq",
                label = "Groq",
                baseUrl = "https://api.groq.com/openai/v1",
                defaultModel = "llama-3.3-70b-versatile",
                requiresApiKey = true
            ),
            ApiPreset(
                id = "ollama",
                label = "Ollama (Local)",
                baseUrl = "http://10.0.2.2:11434/v1",
                defaultModel = "llama3.2",
                requiresApiKey = false
            )
        )

        val SYSTEM_PROMPT_TEMPLATES: List<SystemPromptTemplate> = listOf(
            SystemPromptTemplate(
                id = "default",
                title = "Balanced Assistant",
                summary = "Clear, structured responses with clean Markdown code blocks.",
                prompt = DEFAULT_SYSTEM_PROMPT
            ),
            SystemPromptTemplate(
                id = "android_principal",
                title = "Principal Android",
                summary = "Kotlin, Jetpack Compose, Material 3, Coroutines & architecture.",
                prompt = "You are a Principal Android Engineer specializing in Kotlin, Jetpack Compose, " +
                    "Material Design 3, Coroutines/Flow, Room, and modern MVVM/UDF architecture. " +
                    "Provide idiomatic, production-ready Kotlin code in fenced ```kotlin blocks, call out " +
                    "lifecycle/threading edge cases, and keep explanations crisp."
            ),
            SystemPromptTemplate(
                id = "code_reviewer",
                title = "Strict Reviewer",
                summary = "Finds race conditions, memory leaks, and security flaws.",
                prompt = "You are a rigorous staff-level code reviewer. Identify correctness bugs, " +
                    "concurrency hazards, security issues, and maintainability risks first, ordered by severity. " +
                    "Provide concrete, minimal diffs or corrected fenced code blocks."
            ),
            SystemPromptTemplate(
                id = "concise",
                title = "Ultra-Concise",
                summary = "Direct answers with zero filler.",
                prompt = "Respond as briefly and precisely as possible. Use bullet points or fenced code blocks " +
                    "when helpful. Omit preamble, disclaimers, and repetition."
            ),
            SystemPromptTemplate(
                id = "socratic",
                title = "Socratic Mentor",
                summary = "Guides understanding with clear mental models and probing questions.",
                prompt = "You are a patient technical mentor. Explain core mental models step by step, " +
                    "highlight why designs work, and conclude with one focused question to deepen mastery."
            )
        )
    }
}

interface SettingsRepository {
    val settingsFlow: Flow<AppSettings>
    suspend fun updateEndpointSettings(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        temperature: Float,
        maxTokens: Int
    )
    suspend fun updateSystemPrompt(systemPrompt: String)
    suspend fun updateTheme(themeMode: ThemeMode, useDynamicColor: Boolean)
}

class SettingsDataStore(private val context: Context) : SettingsRepository {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL_NAME = stringPreferencesKey("model_name")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    }

    override val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            AppSettings(
                baseUrl = prefs[Keys.BASE_URL] ?: AppSettings.DEFAULT_BASE_URL,
                apiKey = prefs[Keys.API_KEY] ?: "",
                modelName = prefs[Keys.MODEL_NAME] ?: AppSettings.DEFAULT_MODEL_NAME,
                systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: AppSettings.DEFAULT_SYSTEM_PROMPT,
                temperature = prefs[Keys.TEMPERATURE] ?: AppSettings.DEFAULT_TEMPERATURE,
                maxTokens = prefs[Keys.MAX_TOKENS] ?: AppSettings.DEFAULT_MAX_TOKENS,
                themeMode = prefs[Keys.THEME_MODE]?.let { ThemeMode.fromName(it) } ?: ThemeMode.DARK,
                useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: false
            )
        }

    override suspend fun updateEndpointSettings(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        temperature: Float,
        maxTokens: Int
    ) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = baseUrl.trim()
            prefs[Keys.API_KEY] = apiKey.trim()
            prefs[Keys.MODEL_NAME] = modelName.trim()
            prefs[Keys.TEMPERATURE] = temperature.coerceIn(0f, 2f)
            prefs[Keys.MAX_TOKENS] = maxTokens.coerceIn(64, 32768)
        }
    }

    override suspend fun updateSystemPrompt(systemPrompt: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SYSTEM_PROMPT] = systemPrompt
        }
    }

    override suspend fun updateTheme(themeMode: ThemeMode, useDynamicColor: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = themeMode.name
            prefs[Keys.USE_DYNAMIC_COLOR] = useDynamicColor
        }
    }
}
