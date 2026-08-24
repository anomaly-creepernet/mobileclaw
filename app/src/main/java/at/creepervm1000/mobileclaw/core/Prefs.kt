package at.creepervm1000.mobileclaw.core

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import at.creepervm1000.mobileclaw.llm.LlmConfig
import at.creepervm1000.mobileclaw.llm.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "agent_settings")

data class AgentSettings(
    val provider: Provider = Provider.OPENAI,
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4o",
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
    /** Safety valve on the tool-calling loop, per turn. */
    val maxToolIterations: Int = 12,
    /** The agent renames itself by writing this; seeds the notification title. */
    val agentName: String = "MobileClaw",
    val serviceEnabled: Boolean = false,
    val startOnBoot: Boolean = false,
    val useWakeLock: Boolean = false,
) {
    fun toLlmConfig() = LlmConfig(
        provider = provider,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        maxTokens = maxTokens,
        temperature = temperature,
    )

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank()
}

class Prefs(private val context: Context) {

    private object Keys {
        val PROVIDER = stringPreferencesKey("provider")
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val TEMPERATURE = doublePreferencesKey("temperature")
        val MAX_ITERATIONS = intPreferencesKey("max_iterations")
        val AGENT_NAME = stringPreferencesKey("agent_name")
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val USE_WAKE_LOCK = booleanPreferencesKey("use_wake_lock")
    }

    val settings: Flow<AgentSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings(): AgentSettings {
        val defaults = AgentSettings()
        return AgentSettings(
            provider = this[Keys.PROVIDER]?.let { name ->
                runCatching { Provider.valueOf(name) }.getOrNull()
            } ?: defaults.provider,
            baseUrl = this[Keys.BASE_URL] ?: defaults.baseUrl,
            apiKey = this[Keys.API_KEY] ?: defaults.apiKey,
            model = this[Keys.MODEL] ?: defaults.model,
            maxTokens = this[Keys.MAX_TOKENS] ?: defaults.maxTokens,
            temperature = this[Keys.TEMPERATURE] ?: defaults.temperature,
            maxToolIterations = this[Keys.MAX_ITERATIONS] ?: defaults.maxToolIterations,
            agentName = this[Keys.AGENT_NAME] ?: defaults.agentName,
            serviceEnabled = this[Keys.SERVICE_ENABLED] ?: defaults.serviceEnabled,
            startOnBoot = this[Keys.START_ON_BOOT] ?: defaults.startOnBoot,
            useWakeLock = this[Keys.USE_WAKE_LOCK] ?: defaults.useWakeLock,
        )
    }

    suspend fun update(transform: (AgentSettings) -> AgentSettings) {
        context.dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[Keys.PROVIDER] = updated.provider.name
            prefs[Keys.BASE_URL] = updated.baseUrl
            prefs[Keys.API_KEY] = updated.apiKey
            prefs[Keys.MODEL] = updated.model
            prefs[Keys.MAX_TOKENS] = updated.maxTokens
            prefs[Keys.TEMPERATURE] = updated.temperature
            prefs[Keys.MAX_ITERATIONS] = updated.maxToolIterations
            prefs[Keys.AGENT_NAME] = updated.agentName
            prefs[Keys.SERVICE_ENABLED] = updated.serviceEnabled
            prefs[Keys.START_ON_BOOT] = updated.startOnBoot
            prefs[Keys.USE_WAKE_LOCK] = updated.useWakeLock
        }
    }
}
