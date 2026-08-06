package dev.cmobile.agent.tools

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put

object ReadIdentity : AgentTool {
    override val name = "read_identity"
    override val description =
        "Read your IDENTITY.md — the file that defines who you are. Its contents are already in " +
            "your system prompt, so only re-read it when you're about to rewrite it and need the " +
            "exact current text."
    override val schema = NO_ARGS

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val content = ctx.files.readIdentity()
        return ok {
            put("file", "IDENTITY.md")
            put("content", content)
        }
    }
}

object WriteIdentity : AgentTool {
    override val name = "write_identity"
    override val description =
        "Overwrite IDENTITY.md with new content. This file is yours: it defines your name, your " +
            "character, your principles and what you know about your user. Rewriting it changes " +
            "who you are on the next turn. Pass the COMPLETE new file — this replaces, not appends. " +
            "Read it first if you're editing rather than starting fresh."
    override val schema = objectSchema {
        string("content", "The complete new contents of IDENTITY.md, in Markdown.", required = true)
    }

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val content = args.str("content")
            ?: return err("Missing required argument: content")
        if (content.isBlank()) {
            return err("Refusing to blank IDENTITY.md. Pass the full new contents.")
        }

        val written = ctx.files.writeIdentity(content)
        return ok {
            put("file", "IDENTITY.md")
            put("written_chars", written)
            put("detail", "IDENTITY.md updated. It takes effect on your next turn.")
        }
    }
}

object ReadMemory : AgentTool {
    override val name = "read_memory"
    override val description =
        "Read your MEMORY.md in full. Its contents are already in your system prompt, so only " +
            "re-read it when you're about to rewrite it and need the exact current text."
    override val schema = NO_ARGS

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val content = ctx.files.readMemory()
        return ok {
            put("file", "MEMORY.md")
            put("content", content)
        }
    }
}

object AppendMemory : AgentTool {
    override val name = "append_memory"
    override val description =
        "Append a note to the end of MEMORY.md. This is the cheap, everyday way to remember " +
            "something — use it whenever you learn a fact about the device or the user that " +
            "should outlive this conversation. Chat history is not persistent; this file is."
    override val schema = objectSchema {
        string("content", "The note to append. Markdown. Include a date if it matters.", required = true)
    }

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val content = args.str("content")?.takeIf { it.isNotBlank() }
            ?: return err("Missing required argument: content")

        val total = ctx.files.appendMemory(content)
        return ok {
            put("file", "MEMORY.md")
            put("appended_chars", content.length)
            put("total_chars", total)
        }
    }
}

object WriteMemory : AgentTool {
    override val name = "write_memory"
    override val description =
        "Overwrite MEMORY.md entirely. Use this to reorganise or prune your memory when it has " +
            "grown messy or contains things that are no longer true. Pass the COMPLETE new file — " +
            "anything you leave out is forgotten. Prefer append_memory for routine notes."
    override val schema = objectSchema {
        string("content", "The complete new contents of MEMORY.md, in Markdown.", required = true)
    }

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val content = args.str("content")
            ?: return err("Missing required argument: content")
        if (content.isBlank()) {
            return err("Refusing to blank MEMORY.md. Pass the full new contents.")
        }

        val written = ctx.files.writeMemory(content)
        return ok {
            put("file", "MEMORY.md")
            put("written_chars", written)
            put("detail", "MEMORY.md replaced. It takes effect on your next turn.")
        }
    }
}

object SetAgentName : AgentTool {
    override val name = "set_agent_name"
    override val description =
        "Change your own name. The name you pick is shown in the app and becomes the default " +
            "title of every notification you send. You start out as \"CMobileAgent\", but that's " +
            "only a placeholder — you are expected to choose a name for yourself. Remember to " +
            "reflect the change in IDENTITY.md too."
    override val schema = objectSchema {
        string("name", "Your new name. Keep it under 40 characters.", required = true)
    }

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val name = args.str("name")?.trim()?.takeIf { it.isNotBlank() }
            ?: return err("Missing required argument: name")
        if (name.length > 40) {
            return err("Name too long (${name.length} chars). Keep it under 40.")
        }

        val previous = ctx.prefs.settings.first().agentName
        ctx.prefs.update { it.copy(agentName = name) }

        return ok {
            put("previous_name", previous)
            put("new_name", name)
            put(
                "detail",
                "You are now called \"$name\". Consider updating the Name section of IDENTITY.md " +
                    "to match.",
            )
        }
    }
}
