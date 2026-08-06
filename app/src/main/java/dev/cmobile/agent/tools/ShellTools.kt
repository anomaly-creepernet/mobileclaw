package dev.cmobile.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.TimeUnit

/** Shared command runner used by both the sandbox and the Shizuku shell. */
object ShellRunner {

    const val MAX_OUTPUT_CHARS = 12_000

    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean,
    )

    suspend fun run(
        process: Process,
        timeoutSeconds: Int,
    ): Result = withContext(Dispatchers.IO) {
        val stdout = StringBuilder()
        val stderr = StringBuilder()

        // Drain both pipes concurrently, otherwise a chatty command deadlocks on a full buffer.
        val outThread = Thread {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { stdout.appendLine(it) }
            }
        }
        val errThread = Thread {
            runCatching {
                process.errorStream.bufferedReader().forEachLine { stderr.appendLine(it) }
            }
        }
        outThread.start()
        errThread.start()

        val finished = process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            outThread.join(1000)
            errThread.join(1000)
            return@withContext Result(-1, stdout.toString(), stderr.toString(), timedOut = true)
        }

        outThread.join(2000)
        errThread.join(2000)

        Result(process.exitValue(), stdout.toString(), stderr.toString(), timedOut = false)
    }

    fun formatResult(result: Result, command: String, shell: String): String = ok {
        put("command", command)
        put("shell", shell)
        put("exit_code", result.exitCode)
        put("timed_out", result.timedOut)
        put("stdout", result.stdout.trimEnd().truncate(MAX_OUTPUT_CHARS))
        put("stderr", result.stderr.trimEnd().truncate(MAX_OUTPUT_CHARS))
        if (result.timedOut) {
            put("note", "The command exceeded its timeout and was killed.")
        }
    }
}

object RunCmd : AgentTool {
    override val name = "run_cmd"
    override val description =
        "Run a shell command inside this app's own Android sandbox (unprivileged, running as the " +
            "app's UID). Good for: reading /proc and /sys, getprop, ping, ls, cat of world-readable " +
            "files, and anything scoped to the app's own data directory. You CANNOT read other " +
            "apps' data, most of /data, or run privileged commands here — use run_shizuku_cmd for " +
            "that. The command is passed to 'sh -c', so pipes and redirects work."
    override val schema = objectSchema {
        string("command", "The shell command line to execute, e.g. \"getprop ro.product.model\".", required = true)
        integer("timeout_seconds", "How long to wait before killing it. Default 20, max 120.")
    }

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val command = args.str("command")?.takeIf { it.isNotBlank() }
            ?: return err("Missing required argument: command")
        val timeout = args.int("timeout_seconds", 20).coerceIn(1, 120)

        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .directory(File(ctx.app.filesDir.absolutePath))
                .start()
            val result = ShellRunner.run(process, timeout)
            ShellRunner.formatResult(result, command, "app_sandbox")
        } catch (e: Exception) {
            err("Failed to execute in sandbox: ${e.message}")
        }
    }
}
