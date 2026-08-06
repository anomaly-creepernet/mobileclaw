package dev.cmobile.agent

import android.app.Application
import dev.cmobile.agent.agent.AgentEngine
import dev.cmobile.agent.core.AgentFiles
import dev.cmobile.agent.core.CronStore
import dev.cmobile.agent.core.Notifier
import dev.cmobile.agent.core.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AgentApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var files: AgentFiles
        private set
    lateinit var crons: CronStore
        private set
    lateinit var engine: AgentEngine
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this

        prefs = Prefs(this)
        files = AgentFiles(this)
        crons = CronStore(this)
        engine = AgentEngine(this, prefs, files, crons)

        Notifier.ensureChannels(this)

        scope.launch { engine.load() }
    }

    companion object {
        @Volatile
        lateinit var instance: AgentApp
            private set
    }
}
