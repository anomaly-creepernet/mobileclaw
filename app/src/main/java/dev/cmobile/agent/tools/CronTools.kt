package dev.cmobile.agent.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTime(ms: Long): String =
    if (ms <= 0) "never" else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

object CreateCron : AgentTool {
    override val name = "create_cron"
    override val description =
        "Schedule yourself to wake up on a repeating interval. Every interval_minutes, the text " +
            "in `prompt` is delivered to you as an event and you get a full turn — you can call " +
            "tools, notify the user, or do nothing. This is how you act without being spoken to. " +
            "Typical values: 30 for a half-hourly check, 60 hourly, 1440 daily. " +
            "interval_minutes=0 means 'every scheduler tick' (~10 seconds) — this is allowed but " +
            "STRONGLY discouraged: it burns battery and API credits fast. The agent service must " +
            "be running for crons to fire."
    override val schema = objectSchema {
        string("name", "A short label so you can recognise this job later.", required = true)
        string(
            "prompt",
            "What to tell yourself when it fires. Write it as an instruction to your future self, " +
                "e.g. \"Check the battery; if it's below 30% and discharging, notify the user.\"",
            required = true,
        )
        integer(
            "interval_minutes",
            "Minutes between runs. 0 fires every ~10 seconds and is strongly discouraged.",
            required = true,
        )
    }

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val name = args.str("name")?.trim()?.takeIf { it.isNotBlank() }
            ?: return err("Missing required argument: name")
        val prompt = args.str("prompt")?.trim()?.takeIf { it.isNotBlank() }
            ?: return err("Missing required argument: prompt")
        val interval = args.int("interval_minutes", -1)
        if (interval < 0) {
            return err("interval_minutes must be 0 or greater.")
        }

        val now = System.currentTimeMillis()
        val result = ctx.crons.add(name, prompt, interval, now)

        return result.fold(
            onSuccess = { job ->
                ok {
                    put("created", true)
                    put("id", job.id)
                    put("name", job.name)
                    put("interval_minutes", job.intervalMinutes)
                    put(
                        "first_run_at",
                        if (job.intervalMinutes <= 0) "within ~10 seconds"
                        else formatTime(now + job.intervalMinutes * 60_000L),
                    )
                    if (job.intervalMinutes == 0) {
                        put(
                            "warning",
                            "interval_minutes=0 fires roughly every 10 seconds. This will drain " +
                                "the battery and consume API credits quickly. Delete it as soon " +
                                "as you no longer need it.",
                        )
                    }
                    if (job.intervalMinutes in 1..4) {
                        put(
                            "warning",
                            "An interval under 5 minutes is aggressive and will noticeably affect " +
                                "battery life.",
                        )
                    }
                }
            },
            onFailure = { err(it.message ?: "Could not create the cron.") },
        )
    }
}

object ListCrons : AgentTool {
    override val name = "list_crons"
    override val description =
        "List the recurring wake-ups you've scheduled for yourself, with their intervals, when " +
            "each last ran and when each runs next."
    override val schema = NO_ARGS

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val jobs = ctx.crons.jobs.value
        return ok {
            put("count", jobs.size)
            put("jobs", buildJsonArray {
                jobs.forEach { job ->
                    addJsonObject {
                        put("id", job.id)
                        put("name", job.name)
                        put("prompt", job.prompt)
                        put("interval_minutes", job.intervalMinutes)
                        put("enabled", job.enabled)
                        put("run_count", job.runCount)
                        put("last_run", formatTime(job.lastRunAtMs))
                        put(
                            "next_run",
                            if (job.intervalMinutes <= 0) "every tick" else formatTime(job.nextRunAtMs()),
                        )
                    }
                }
            })
            if (jobs.isEmpty()) {
                put("detail", "You have no scheduled wake-ups. Use create_cron to add one.")
            }
        }
    }
}

object DeleteCron : AgentTool {
    override val name = "delete_cron"
    override val description =
        "Delete one of your scheduled wake-ups permanently. Accepts either the job's id or its " +
            "name. Use set_cron_enabled instead if you only want to pause it."
    override val schema = objectSchema {
        string("id", "The cron's id, or its name.", required = true)
    }

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val id = args.str("id")?.trim()?.takeIf { it.isNotBlank() }
            ?: return err("Missing required argument: id")

        val deleted = ctx.crons.delete(id)
        return if (deleted) {
            ok {
                put("deleted", true)
                put("id", id)
            }
        } else {
            err("No cron found with id or name \"$id\". Call list_crons to see what exists.")
        }
    }
}

object SetCronEnabled : AgentTool {
    override val name = "set_cron_enabled"
    override val description =
        "Pause or resume one of your scheduled wake-ups without deleting it."
    override val schema = objectSchema {
        string("id", "The cron's id, or its name.", required = true)
        boolean("enabled", "true to resume, false to pause.", required = true)
    }

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val id = args.str("id")?.trim()?.takeIf { it.isNotBlank() }
            ?: return err("Missing required argument: id")
        val enabled = args.bool("enabled", true)

        val updated = ctx.crons.setEnabled(id, enabled)
        return if (updated) {
            ok {
                put("id", id)
                put("enabled", enabled)
            }
        } else {
            err("No cron found with id or name \"$id\".")
        }
    }
}
