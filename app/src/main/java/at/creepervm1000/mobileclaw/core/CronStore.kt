package at.creepervm1000.mobileclaw.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import at.creepervm1000.mobileclaw.llm.AgentJson
import java.io.File

/**
 * A self-scheduled wake-up. The agent creates these for itself: every [intervalMinutes]
 * minutes, [prompt] is delivered to it as an event and it gets a turn to act.
 */
@Serializable
data class CronJob(
    val id: String,
    val name: String,
    val prompt: String,
    val intervalMinutes: Int,
    val createdAtMs: Long,
    val lastRunAtMs: Long = 0L,
    val runCount: Int = 0,
    val enabled: Boolean = true,
) {
    fun isDue(nowMs: Long): Boolean {
        if (!enabled) return false
        if (intervalMinutes <= 0) return true
        val elapsed = nowMs - lastRunAtMs
        return elapsed >= intervalMinutes * 60_000L
    }

    fun nextRunAtMs(): Long =
        if (intervalMinutes <= 0) 0L else lastRunAtMs + intervalMinutes * 60_000L
}

@Serializable
private data class CronFile(val jobs: List<CronJob> = emptyList())

class CronStore(context: Context) {

    private val file = File(context.filesDir, "crons.json")
    private val lock = Mutex()

    private val _jobs = MutableStateFlow<List<CronJob>>(emptyList())
    val jobs: StateFlow<List<CronJob>> = _jobs.asStateFlow()

    companion object {
        /** How often the scheduler wakes to look for due jobs. */
        const val TICK_MS = 10_000L
        const val MAX_JOBS = 32
    }

    suspend fun load() = withContext(Dispatchers.IO) {
        lock.withLock {
            _jobs.value = readFile()
        }
    }

    private fun readFile(): List<CronJob> {
        if (!file.exists()) return emptyList()
        return runCatching {
            AgentJson.decodeFromString<CronFile>(file.readText()).jobs
        }.getOrDefault(emptyList())
    }

    private fun writeFile(jobs: List<CronJob>) {
        runCatching { file.writeText(AgentJson.encodeToString(CronFile(jobs))) }
    }

    suspend fun add(name: String, prompt: String, intervalMinutes: Int, nowMs: Long): Result<CronJob> =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val current = _jobs.value
                if (current.size >= MAX_JOBS) {
                    return@withContext Result.failure(
                        IllegalStateException("Cron limit reached ($MAX_JOBS). Delete one first.")
                    )
                }
                val job = CronJob(
                    id = "cron_${nowMs}_${current.size}",
                    name = name,
                    prompt = prompt,
                    intervalMinutes = intervalMinutes.coerceAtLeast(0),
                    createdAtMs = nowMs,
                    // Start the clock now so a 30-minute job first fires in 30 minutes.
                    lastRunAtMs = nowMs,
                )
                val updated = current + job
                _jobs.value = updated
                writeFile(updated)
                Result.success(job)
            }
        }

    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            val current = _jobs.value
            val updated = current.filterNot { it.id == id || it.name == id }
            if (updated.size == current.size) return@withContext false
            _jobs.value = updated
            writeFile(updated)
            true
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            val current = _jobs.value
            var found = false
            val updated = current.map {
                if (it.id == id || it.name == id) {
                    found = true; it.copy(enabled = enabled)
                } else it
            }
            if (!found) return@withContext false
            _jobs.value = updated
            writeFile(updated)
            true
        }
    }

    suspend fun markRun(id: String, nowMs: Long) = withContext(Dispatchers.IO) {
        lock.withLock {
            val updated = _jobs.value.map {
                if (it.id == id) it.copy(lastRunAtMs = nowMs, runCount = it.runCount + 1) else it
            }
            _jobs.value = updated
            writeFile(updated)
        }
    }

    fun due(nowMs: Long): List<CronJob> = _jobs.value.filter { it.isDue(nowMs) }
}
