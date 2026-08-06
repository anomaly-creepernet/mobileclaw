package dev.cmobile.agent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.cmobile.agent.AgentApp
import dev.cmobile.agent.core.AgentSettings
import dev.cmobile.agent.core.Notifier
import dev.cmobile.agent.service.AgentService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AgentViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AgentApp

    val messages = app.engine.messages
    val status = app.engine.status
    val crons = app.crons.jobs

    val settings = app.prefs.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AgentSettings(),
    )

    fun send(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { app.engine.sendUserMessage(text.trim()) }
    }

    fun clearConversation() = app.engine.clearConversation()

    fun updateSettings(transform: (AgentSettings) -> AgentSettings) {
        viewModelScope.launch { app.prefs.update(transform) }
    }

    fun setServiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            app.prefs.update { it.copy(serviceEnabled = enabled) }
            if (enabled) AgentService.start(app) else AgentService.stop(app)
        }
    }

    fun deleteCron(id: String) {
        viewModelScope.launch { app.crons.delete(id) }
    }

    fun exportFiles(onResult: (String) -> Unit) {
        viewModelScope.launch { onResult(app.files.exportToDownloads()) }
    }

    /**
     * Hands the user to Android's own notification settings. No in-app permission dialog: the
     * system page is the only surface that can also re-enable notifications that were granted
     * and later switched off.
     */
    fun openNotificationSettings() = Notifier.openNotificationSettings(app)

    fun hasNotificationPermission(): Boolean =
        Notifier.hasPostPermission(app) && Notifier.areNotificationsEnabled(app)
}
