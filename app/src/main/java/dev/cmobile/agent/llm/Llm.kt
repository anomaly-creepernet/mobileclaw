package dev.cmobile.agent.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

val AgentJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    explicitNulls = false
}

/** A tool the model asked us to run. [arguments] is a raw JSON object string. */
@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

/** Conversation entry, provider-agnostic. */
@Serializable
sealed class Msg {
    @Serializable
    @SerialName("user")
    data class User(val text: String, val isEvent: Boolean = false) : Msg()

    @Serializable
    @SerialName("assistant")
    data class Assistant(
        val text: String = "",
        val toolCalls: List<ToolCall> = emptyList(),
    ) : Msg()

    @Serializable
    @SerialName("tool")
    data class ToolResult(
        val id: String,
        val name: String,
        val content: String,
    ) : Msg()
}

/** A tool exposed to the model. [schema] is a JSON-Schema object. */
data class ToolSpec(
    val name: String,
    val description: String,
    val schema: JsonObject,
)

data class LlmReply(
    val text: String,
    val toolCalls: List<ToolCall>,
)

class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface LlmClient {
    suspend fun send(
        system: String,
        messages: List<Msg>,
        tools: List<ToolSpec>,
    ): LlmReply
}

enum class Provider { OPENAI, ANTHROPIC }

data class LlmConfig(
    val provider: Provider,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
)
