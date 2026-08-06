package at.creepervm1000.mobileclaw.tools

import android.content.pm.PackageManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Wrapper around the Shizuku client API.
 *
 * Shizuku gives an ordinary app an ADB-privileged (or root) shell without the app itself being
 * privileged — but only if the user is running the Shizuku service and has granted us permission.
 * Every entry point here degrades to a plain status token instead of throwing.
 */
object ShizukuManager {

    /** Literal tokens the agent is told to expect. */
    const val TOKEN_SUCCESS = "shizuku_conned_success"
    const val TOKEN_UNREACHABLE = "shizuku_notreachable"
    const val TOKEN_NOT_CONNECTED = "shizuku not connected"

    private const val PERMISSION_REQUEST_CODE = 4919

    enum class State {
        /** Service isn't running, or is too old / from another user profile. */
        UNREACHABLE,

        /** Service is alive but hasn't granted us API access. */
        NOT_PERMITTED,

        /** Alive and permitted — commands will run. */
        READY,
    }

    /** True when the Shizuku service process is alive and talking to us. */
    private fun binderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    private fun permitted(): Boolean = runCatching {
        if (Shizuku.isPreV11()) false
        else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun state(): State = when {
        !binderAlive() -> State.UNREACHABLE
        permitted() -> State.READY
        else -> State.NOT_PERMITTED
    }

    fun versionOrNull(): Int? = runCatching { Shizuku.getVersion() }.getOrNull()

    fun uidOrNull(): Int? = runCatching { Shizuku.getUid() }.getOrNull()

    /** "root" if Shizuku was started as root, "adb" for the wireless/USB debugging path. */
    fun privilegeLevel(): String = when (uidOrNull()) {
        0 -> "root"
        2000 -> "adb_shell"
        null -> "unknown"
        else -> "uid_${uidOrNull()}"
    }

    fun isPreV11(): Boolean = runCatching { Shizuku.isPreV11() }.getOrDefault(false)

    /**
     * Asks the user to grant API access. Suspends until they answer or [timeoutMs] elapses.
     * Returns true only on an explicit grant.
     */
    suspend fun requestPermission(timeoutMs: Long = 60_000): Boolean {
        if (!binderAlive()) return false
        if (permitted()) return true
        if (isPreV11()) return false

        return try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : Shizuku.OnRequestPermissionResultListener {
                        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                            if (requestCode != PERMISSION_REQUEST_CODE) return
                            Shizuku.removeRequestPermissionResultListener(this)
                            if (continuation.isActive) {
                                continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                            }
                        }
                    }
                    Shizuku.addRequestPermissionResultListener(listener)
                    continuation.invokeOnCancellation {
                        runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
                    }
                    runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
                        .onFailure {
                            Shizuku.removeRequestPermissionResultListener(listener)
                            if (continuation.isActive) continuation.resume(false)
                        }
                }
            }
        } catch (_: TimeoutCancellationException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Starts a privileged process. `Shizuku.newProcess` is marked @RestrictTo in the public
     * artifact, so it's reached reflectively rather than vendoring the AIDL.
     */
    fun newProcess(command: String): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
    }
}

object GetShizukuStatus : AgentTool {
    override val name = "get_shizuku_status"
    override val description =
        "Check whether Shizuku is reachable and whether this app has been granted access to it, " +
            "without prompting the user. Call this before run_shizuku_cmd if you want to know " +
            "your privilege level (adb_shell vs root) in advance."
    override val schema = NO_ARGS

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val state = ShizukuManager.state()
        return ok {
            put("state", state.name.lowercase())
            put("reachable", state != ShizukuManager.State.UNREACHABLE)
            put("permitted", state == ShizukuManager.State.READY)
            ShizukuManager.versionOrNull()?.let { put("server_version", it) }
            if (state != ShizukuManager.State.UNREACHABLE) {
                put("privilege_level", ShizukuManager.privilegeLevel())
            }
            put(
                "explanation",
                when (state) {
                    ShizukuManager.State.UNREACHABLE ->
                        "The Shizuku service is not running. The user must start Shizuku (via " +
                            "wireless debugging, ADB, or root) before privileged commands work."

                    ShizukuManager.State.NOT_PERMITTED ->
                        "Shizuku is running but has not granted this app access. Call " +
                            "connect_shizuku to prompt the user."

                    ShizukuManager.State.READY ->
                        "Shizuku is connected and permitted. run_shizuku_cmd is available."
                },
            )
        }
    }
}

object ConnectShizuku : AgentTool {
    override val name = "connect_shizuku"
    override val description =
        "Connect to Shizuku, prompting the user for permission if needed. Returns exactly " +
            "\"${ShizukuManager.TOKEN_SUCCESS}\" when privileged commands are available, or " +
            "\"${ShizukuManager.TOKEN_UNREACHABLE}\" if the service isn't running or the user " +
            "denied the request. Call this once before using run_shizuku_cmd."
    override val schema = NO_ARGS

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        return when (ShizukuManager.state()) {
            ShizukuManager.State.READY -> ok {
                put("result", ShizukuManager.TOKEN_SUCCESS)
                put("privilege_level", ShizukuManager.privilegeLevel())
                put("detail", "Already connected; run_shizuku_cmd is available.")
            }

            ShizukuManager.State.UNREACHABLE -> ok {
                put("result", ShizukuManager.TOKEN_UNREACHABLE)
                put(
                    "detail",
                    "The Shizuku service is not running. Ask the user to start the Shizuku app " +
                        "(via wireless debugging or ADB) and try again.",
                )
            }

            ShizukuManager.State.NOT_PERMITTED -> {
                val granted = ShizukuManager.requestPermission()
                if (granted) {
                    ok {
                        put("result", ShizukuManager.TOKEN_SUCCESS)
                        put("privilege_level", ShizukuManager.privilegeLevel())
                        put("detail", "The user granted access. run_shizuku_cmd is available.")
                    }
                } else {
                    ok {
                        put("result", ShizukuManager.TOKEN_UNREACHABLE)
                        put(
                            "detail",
                            "Permission was denied, dismissed, or timed out. Privileged commands " +
                                "are unavailable. Do not retry repeatedly — ask the user first.",
                        )
                    }
                }
            }
        }
    }
}

object RunShizukuCmd : AgentTool {
    override val name = "run_shizuku_cmd"
    override val description =
        "Run a shell command through Shizuku with elevated (ADB shell, or root) privileges. Use " +
            "this for things the app sandbox cannot do: pm/cmd/settings/dumpsys/svc, reading " +
            "protected paths, toggling system state. If Shizuku is not connected this returns " +
            "exactly \"${ShizukuManager.TOKEN_NOT_CONNECTED}\" and nothing runs — call " +
            "connect_shizuku first. Be careful: these commands can change the device."
    override val schema = objectSchema {
        string("command", "The shell command line to execute with elevated privileges.", required = true)
        integer("timeout_seconds", "How long to wait before killing it. Default 30, max 120.")
    }

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        if (ShizukuManager.state() != ShizukuManager.State.READY) {
            return ShizukuManager.TOKEN_NOT_CONNECTED
        }

        val command = args.str("command")?.takeIf { it.isNotBlank() }
            ?: return err("Missing required argument: command")
        val timeout = args.int("timeout_seconds", 30).coerceIn(1, 120)

        return try {
            val process = ShizukuManager.newProcess(command)
            val result = ShellRunner.run(process, timeout)
            ShellRunner.formatResult(result, command, "shizuku_${ShizukuManager.privilegeLevel()}")
        } catch (e: Exception) {
            // A binder death between the state check and the call lands here.
            if (ShizukuManager.state() != ShizukuManager.State.READY) {
                ShizukuManager.TOKEN_NOT_CONNECTED
            } else {
                err("Shizuku command failed: ${e.message}")
            }
        }
    }
}
