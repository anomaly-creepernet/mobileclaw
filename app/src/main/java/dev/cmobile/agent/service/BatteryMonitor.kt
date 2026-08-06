package dev.cmobile.agent.service

import android.content.Context
import dev.cmobile.agent.agent.AgentEngine
import dev.cmobile.agent.core.Notifier
import dev.cmobile.agent.tools.BatteryReader
import dev.cmobile.agent.tools.BatterySnapshot

/**
 * Watches the charge level and hands the agent an event when it crosses a threshold.
 *
 * Each threshold fires once per discharge cycle. A level is re-armed only after the battery
 * climbs clear of it (plus a margin) or the phone goes on charge, so a battery hovering at 15%
 * doesn't produce an alert every five minutes.
 */
class BatteryMonitor(
    private val context: Context,
    private val engine: AgentEngine,
) {

    /** Descending, so the most severe matching threshold is handled first. */
    private val thresholds = listOf(
        Threshold(15, Severity.NOTICE),
        Threshold(5, Severity.WARNING),
        Threshold(2, Severity.CRITICAL),
        Threshold(1, Severity.CRITICAL),
        Threshold(0, Severity.CRITICAL),
    ).sortedByDescending { it.percent }

    private val fired = mutableSetOf<Int>()

    /** Previous charging state, so a plug/unplug transition can be spotted. Null until first read. */
    private var wasCharging: Boolean? = null

    enum class Severity { NOTICE, WARNING, CRITICAL }

    data class Threshold(val percent: Int, val severity: Severity)

    companion object {
        const val CHECK_INTERVAL_MS = 5 * 60 * 1000L

        /** Level at or below which plugging in is itself worth telling the agent about. */
        const val LOW_BATTERY_PERCENT = 15

        /** How far above a threshold the battery must climb before that alert re-arms. */
        private const val REARM_MARGIN = 3

        private const val CRITICAL_NOTIFICATION_ID = 42
    }

    fun currentPercent(): Int = BatteryReader.read(context).percent

    /** Returns the events to deliver to the agent, if any. */
    suspend fun check(): List<String> {
        val battery = BatteryReader.read(context)
        if (battery.percent < 0) return emptyList()

        val plugTransition = plugTransition(battery)
        rearm(battery)

        // Charging phones aren't in trouble, however low they are — but the moment of being
        // plugged in while low is worth reporting, since it resolves an alert already sent.
        if (battery.charging) return listOfNotNull(plugTransition)

        val crossed = thresholds.filter { battery.percent <= it.percent && it.percent !in fired }
        if (crossed.isEmpty()) return listOfNotNull(plugTransition)

        // Only announce the lowest (most severe) threshold crossed this round, but mark all of
        // them fired so a fast drain doesn't produce a burst of stacked alerts.
        val worst = crossed.minByOrNull { it.percent } ?: return listOfNotNull(plugTransition)
        crossed.forEach { fired += it.percent }

        if (worst.severity == Severity.CRITICAL) {
            // Post directly too — a 1% warning must not depend on an API round-trip succeeding.
            Notifier.notify(
                context = context,
                title = "Battery critical — ${battery.percent}%",
                body = "The phone is about to shut down. Plug it in now.",
                urgent = true,
                id = CRITICAL_NOTIFICATION_ID,
            )
        }

        return listOfNotNull(plugTransition, describe(battery, worst))
    }

    /**
     * Detects the phone being plugged in while low, and returns an event describing it.
     *
     * This is the counterpart to the low-battery alerts: having warned the user, the agent
     * should also learn that the situation resolved, rather than being left believing the
     * phone is still about to die.
     */
    private fun plugTransition(battery: BatterySnapshot): String? {
        val previous = wasCharging
        wasCharging = battery.charging

        // First reading establishes a baseline; it isn't a transition.
        if (previous == null || previous == battery.charging) return null
        if (!battery.charging) return null
        if (battery.percent > LOW_BATTERY_PERCENT) return null

        // Clear the critical alert: it said "plug it in now", and they have.
        Notifier.cancel(context, CRITICAL_NOTIFICATION_ID)

        return "Phone plugged in at ${battery.percent}% and now charging (plugged=" +
            "${battery.plugged}). This resolves the low-battery situation you were told about; " +
            "any critical alert has been dismissed. Don't notify the user about this unless you " +
            "have a reason to — they are holding the phone, so they know. Update MEMORY.md if " +
            "their charging habits are worth recording."
    }

    private fun rearm(battery: BatterySnapshot) {
        if (battery.charging) {
            fired.clear()
            return
        }
        fired.removeAll { threshold -> battery.percent > threshold + REARM_MARGIN }
    }

    private fun describe(battery: BatterySnapshot, threshold: Threshold): String = buildString {
        when (threshold.severity) {
            Severity.NOTICE -> append(
                "Battery low: ${battery.percent}% and discharging. This is a heads-up, not an " +
                    "emergency."
            )

            Severity.WARNING -> append(
                "Battery very low: ${battery.percent}% and discharging. The phone will die before " +
                    "long."
            )

            Severity.CRITICAL -> append(
                "BATTERY CRITICAL: ${battery.percent}% and discharging. Shutdown is imminent. A " +
                    "high-priority system notification has already been shown to the user, so do " +
                    "not duplicate it — act only if you have something more useful to add."
            )
        }
        append(" (status=${battery.status}, plugged=${battery.plugged}")
        append(", temp=${battery.temperatureC}°C")
        battery.currentNowMa?.let { append(", current=${it}mA") }
        append(".) ")
        append(
            "Decide what to do. If the user should be told something beyond the raw number, use " +
                "send_notification. If anything here is worth remembering, write it to MEMORY.md."
        )
    }
}
