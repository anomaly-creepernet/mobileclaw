package dev.cmobile.agent.tools

import android.content.Context
import dev.cmobile.agent.core.AgentFiles
import dev.cmobile.agent.core.CronStore
import dev.cmobile.agent.core.Prefs
import dev.cmobile.agent.llm.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Everything a tool is allowed to touch. */
class ToolContext(
    val app: Context,
    val files: AgentFiles,
    val crons: CronStore,
    val prefs: Prefs,
)

interface AgentTool {
    val name: String
    val description: String
    val schema: JsonObject

    /** Returns the string handed straight back to the model. */
    suspend fun run(args: JsonObject, ctx: ToolContext): String

    fun toSpec() = ToolSpec(name, description, schema)
}

// ---------------------------------------------------------------------------
// Schema helpers
// ---------------------------------------------------------------------------

val NO_ARGS: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") { }
}

class PropsBuilder {
    val entries = mutableListOf<Pair<String, JsonObject>>()
    val required = mutableListOf<String>()

    fun string(name: String, description: String, required: Boolean = false, enum: List<String>? = null) {
        entries += name to buildJsonObject {
            put("type", "string")
            put("description", description)
            if (enum != null) {
                put("enum", buildJsonArray { enum.forEach { add(JsonPrimitive(it)) } })
            }
        }
        if (required) this.required += name
    }

    fun integer(name: String, description: String, required: Boolean = false) {
        entries += name to buildJsonObject {
            put("type", "integer")
            put("description", description)
        }
        if (required) this.required += name
    }

    fun boolean(name: String, description: String, required: Boolean = false) {
        entries += name to buildJsonObject {
            put("type", "boolean")
            put("description", description)
        }
        if (required) this.required += name
    }
}

fun objectSchema(block: PropsBuilder.() -> Unit): JsonObject {
    val builder = PropsBuilder().apply(block)
    return buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            builder.entries.forEach { (key, value) -> put(key, value) }
        }
        put("required", buildJsonArray { builder.required.forEach { add(JsonPrimitive(it)) } })
    }
}

// ---------------------------------------------------------------------------
// Argument accessors — tolerant, because models send loosely typed JSON.
// ---------------------------------------------------------------------------

fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

fun JsonObject.str(key: String, default: String): String = str(key) ?: default

fun JsonObject.int(key: String, default: Int): Int {
    val prim = this[key]?.jsonPrimitive ?: return default
    return prim.intOrNull ?: prim.doubleOrNull?.toInt() ?: prim.contentOrNull?.trim()?.toIntOrNull() ?: default
}

fun JsonObject.bool(key: String, default: Boolean): Boolean {
    val prim = this[key]?.jsonPrimitive ?: return default
    return prim.booleanOrNull ?: prim.contentOrNull?.trim()?.lowercase()?.let {
        when (it) {
            "true", "yes", "1" -> true
            "false", "no", "0" -> false
            else -> null
        }
    } ?: default
}

// ---------------------------------------------------------------------------
// Result helpers
// ---------------------------------------------------------------------------

fun ok(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
    buildJsonObject(block).toString()

fun err(message: String): String = buildJsonObject { put("error", message) }.toString()

fun String.truncate(max: Int): String =
    if (length <= max) this else take(max) + "\n… [truncated, ${length - max} more chars]"
