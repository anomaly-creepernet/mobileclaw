package dev.cmobile.agent.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import java.util.Locale

data class BatterySnapshot(
    val percent: Int,
    val status: String,
    val charging: Boolean,
    val plugged: String,
    val health: String,
    val temperatureC: Double,
    val voltageV: Double,
    val technology: String,
    val currentNowMa: Int?,
    val capacityMah: Int?,
)

/** Single source of truth for battery state — used by the tool and the background monitor. */
object BatteryReader {

    fun read(context: Context): BatterySnapshot {
        val intent: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1

        val statusRaw = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val status = when (statusRaw) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
            else -> "unknown"
        }

        val pluggedRaw = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val plugged = when (pluggedRaw) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            0 -> "unplugged"
            else -> "other"
        }

        val healthRaw = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val health = when (healthRaw) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
            else -> "unknown"
        }

        val batteryManager =
            context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        val currentNow = runCatching {
            // Reported in microamps; negative means discharging on most devices.
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.div(1000)
        }.getOrNull()

        val capacity = runCatching {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.div(1000)
        }.getOrNull()

        return BatterySnapshot(
            percent = percent,
            status = status,
            charging = statusRaw == BatteryManager.BATTERY_STATUS_CHARGING ||
                statusRaw == BatteryManager.BATTERY_STATUS_FULL ||
                pluggedRaw != 0,
            plugged = plugged,
            health = health,
            temperatureC = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0,
            voltageV = (intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000.0,
            technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "unknown",
            currentNowMa = currentNow?.takeIf { it != 0 && it != Int.MIN_VALUE },
            capacityMah = capacity?.takeIf { it > 0 },
        )
    }
}

object GetBatteryInfo : AgentTool {
    override val name = "get_battery_info"
    override val description =
        "Get the current battery state: charge percentage, charging status, what it's plugged " +
            "into, health, temperature, voltage, cell technology and instantaneous current draw."
    override val schema = NO_ARGS

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val battery = BatteryReader.read(ctx.app)

        return ok {
            put("percent", battery.percent)
            put("status", battery.status)
            put("charging", battery.charging)
            put("plugged_into", battery.plugged)
            put("health", battery.health)
            put("temperature_c", String.format(Locale.US, "%.1f", battery.temperatureC))
            put("voltage_v", String.format(Locale.US, "%.3f", battery.voltageV))
            put("technology", battery.technology)
            battery.currentNowMa?.let { put("current_now_ma", it) }
            battery.capacityMah?.let { put("charge_counter_mah", it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                put("power_save_mode", isPowerSaveMode(ctx.app))
            }
            put("summary", summarize(battery))
        }
    }

    private fun isPowerSaveMode(context: Context): Boolean = runCatching {
        val power = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        power.isPowerSaveMode
    }.getOrDefault(false)

    private fun summarize(battery: BatterySnapshot): String = buildString {
        append("Battery at ${battery.percent}%, ${battery.status}")
        if (battery.plugged != "unplugged") append(" via ${battery.plugged}")
        append(". Health ${battery.health}, ${String.format(Locale.US, "%.1f", battery.temperatureC)}°C.")
        if (!battery.charging && battery.percent in 0..15) {
            append(" This is low — the device may shut down soon.")
        }
    }
}
