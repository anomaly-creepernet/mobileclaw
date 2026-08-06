package dev.cmobile.agent.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.cmobile.agent.AgentApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as? AgentApp ?: return
        val pendingResult = goAsync()

        app.scope.launch {
            try {
                val settings = app.prefs.settings.first()
                if (settings.startOnBoot && settings.serviceEnabled) {
                    // Android 12+ can refuse a foreground service started from the background;
                    // AgentService.start swallows that, and the user can start it from the app.
                    AgentService.start(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
