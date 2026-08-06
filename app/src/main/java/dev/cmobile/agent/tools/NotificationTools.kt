package dev.cmobile.agent.tools

import android.os.Build
import dev.cmobile.agent.core.Notifier
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put

object CheckNotificationPermission : AgentTool {
    override val name = "check_notification_permission"
    override val description =
        "Check whether you are currently allowed to post notifications. This never shows anything " +
            "to the user. Call this before send_notification if you want to know in advance " +
            "whether it will land."
    override val schema = NO_ARGS

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val hasRuntimePermission = Notifier.hasPostPermission(ctx.app)
        val channelsEnabled = Notifier.areNotificationsEnabled(ctx.app)
        val granted = hasRuntimePermission && channelsEnabled

        return ok {
            put("granted", granted)
            put("runtime_permission", hasRuntimePermission)
            put("notifications_enabled", channelsEnabled)
            put("needs_runtime_request", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            put(
                "detail",
                when {
                    granted -> "Notifications are allowed. send_notification will work."
                    !hasRuntimePermission ->
                        "The POST_NOTIFICATIONS permission has not been granted. Call " +
                            "request_notification_permission to send the user to the Android " +
                            "settings page where they can turn it on."

                    else ->
                        "Notifications are switched off for this app in system settings. Call " +
                            "request_notification_permission to open that page for the user."
                },
            )
        }
    }
}

object RequestNotificationPermission : AgentTool {
    override val name = "request_notification_permission"
    override val description =
        "Open Android's notification settings page for this app so the user can allow " +
            "notifications. This app deliberately never shows an in-app permission dialog — " +
            "notifications are configured in Android itself. This returns immediately; it does " +
            "NOT tell you what the user decided, so re-check with check_notification_permission " +
            "on a later turn rather than assuming. Don't call this repeatedly."
    override val schema = NO_ARGS

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        if (Notifier.hasPostPermission(ctx.app) && Notifier.areNotificationsEnabled(ctx.app)) {
            return ok {
                put("result", "already_granted")
                put("detail", "Permission was already granted; nothing was opened.")
            }
        }

        Notifier.openNotificationSettings(ctx.app)
        return ok {
            put("result", "opened_settings")
            put(
                "detail",
                "Android's notification settings page for this app has been opened. Tell the " +
                    "user what to switch on, in one sentence. You will not be told the outcome " +
                    "directly — call check_notification_permission next time you need to know.",
            )
        }
    }
}

object SendNotification : AgentTool {
    override val name = "send_notification"
    override val description =
        "Post an Android notification to the user. You choose the title — it defaults to your own " +
            "name, but you can override it per notification. Use urgent=true only for things that " +
            "genuinely warrant interrupting them (e.g. a critically low battery); it makes the " +
            "notification buzz and pop up over other apps."
    override val schema = objectSchema {
        string("body", "The notification text. Keep it short — this is a phone screen.", required = true)
        string("title", "Notification title. Defaults to your current name.")
        boolean("urgent", "Use the high-priority alert channel (vibrates, heads-up). Default false.")
    }

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val body = args.str("body")?.takeIf { it.isNotBlank() }
            ?: return err("Missing required argument: body")
        val urgent = args.bool("urgent", false)

        val agentName = ctx.prefs.settings.first().agentName
        val title = args.str("title")?.takeIf { it.isNotBlank() } ?: agentName

        if (!Notifier.hasPostPermission(ctx.app) || !Notifier.areNotificationsEnabled(ctx.app)) {
            return ok {
                put("sent", false)
                put("reason", "permission_denied")
                put(
                    "detail",
                    "Notifications are not permitted, so nothing was shown. Call " +
                        "request_notification_permission to open Android's settings page.",
                )
            }
        }

        val id = Notifier.notify(ctx.app, title, body, urgent = urgent)

        return ok {
            put("sent", id != null)
            if (id != null) {
                put("notification_id", id)
                put("title", title)
                put("urgent", urgent)
            } else {
                put("reason", "rejected_by_system")
                put("detail", "The OS refused to post the notification.")
            }
        }
    }
}
