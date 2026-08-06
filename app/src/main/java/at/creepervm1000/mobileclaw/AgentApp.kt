package at.creepervm1000.mobileclaw

import android.app.Application
import at.creepervm1000.mobileclaw.agent.AgentEngine
import at.creepervm1000.mobileclaw.core.AgentFiles
import at.creepervm1000.mobileclaw.core.CronStore
import at.creepervm1000.mobileclaw.core.Notifier
import at.creepervm1000.mobileclaw.core.Prefs
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
