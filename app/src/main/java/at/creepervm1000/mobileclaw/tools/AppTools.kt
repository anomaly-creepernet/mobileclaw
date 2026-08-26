package at.creepervm1000.mobileclaw.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put

object OpenUrl : AgentTool {
    override val name = "open_url"
    override val description =
        "Open a URL in the user's default browser or matching app. Use this when the user asks " +
            "you to show them a web page, map, deep link or other URI on the device."
    override val schema = objectSchema {
        string("url", "The URL or URI to open, including the scheme such as https://.", required = true)
    }

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val url = args.str("url")?.trim().orEmpty()
        if (url.isBlank()) return err("url is required")
        val uri = runCatching { Uri.parse(url) }.getOrElse { return err("Invalid URL: ${it.message}") }
        if (uri.scheme.isNullOrBlank()) return err("URL must include a scheme, for example https://")

        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            ctx.app.startActivity(intent)
            ok {
                put("opened", true)
                put("url", url)
            }
        }.getOrElse { err("No app could open this URL: ${it.message}") }
    }
}

object LaunchApp : AgentTool {
    override val name = "launch_app"
    override val description =
        "Launch an installed app by package name. Use list_installed_apps first if you need to " +
            "find the package name."
    override val schema = objectSchema {
        string("package", "Android package name of the app to launch, for example com.android.settings.", required = true)
    }

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val packageName = args.str("package")?.trim().orEmpty()
        if (packageName.isBlank()) return err("package is required")

        val intent = ctx.app.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return err("No launchable app found for package: $packageName")

        return runCatching {
            ctx.app.startActivity(intent)
            ok {
                put("launched", true)
                put("package", packageName)
            }
        }.getOrElse { err("Could not launch $packageName: ${it.message}") }
    }
}

object OpenAppSettings : AgentTool {
    override val name = "open_app_settings"
    override val description =
        "Open Android's settings page for an installed app, where the user can review permissions, " +
            "notifications, battery usage and storage. Defaults to MobileClaw itself."
    override val schema = objectSchema {
        string("package", "Android package name to show settings for. Defaults to this app.")
    }

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val packageName = args.str("package")?.trim().takeUnless { it.isNullOrBlank() } ?: ctx.app.packageName
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching {
            ctx.app.startActivity(intent)
            ok {
                put("opened", true)
                put("package", packageName)
            }
        }.getOrElse { err("Could not open app settings for $packageName: ${it.message}") }
    }
}

object SetClipboard : AgentTool {
    override val name = "set_clipboard"
    override val description = "Copy text to the Android clipboard so the user can paste it elsewhere."
    override val schema = objectSchema {
        string("text", "Text to copy to the clipboard.", required = true)
        string("label", "Human-readable clipboard label. Default MobileClaw.")
    }

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val text = args.str("text") ?: return err("text is required")
        val label = args.str("label", "MobileClaw")
        val clipboard = ctx.app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        return ok {
            put("copied", true)
            put("label", label)
            put("chars", text.length)
        }
    }
}

object GetClipboard : AgentTool {
    override val name = "get_clipboard"
    override val description =
        "Read plain text from the Android clipboard when available. Android may block clipboard " +
            "reads unless MobileClaw is foreground or the active input method."
    override val schema = NO_ARGS

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val clipboard = ctx.app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val item = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        val text = item?.coerceToText(ctx.app)?.toString()
        return ok {
            put("has_text", !text.isNullOrEmpty())
            put("text", text ?: "")
            put("chars", text?.length ?: 0)
            put("note", "Clipboard reads may be empty when Android privacy rules deny background access.")
        }
    }
}
