package dev.cmobile.agent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CMobileAgentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot()
                }
            }
        }
    }
}

private enum class Screen { CHAT, SETTINGS }

@Composable
private fun AppRoot(viewModel: AgentViewModel = viewModel()) {
    var screen by remember { mutableStateOf(Screen.CHAT) }

    when (screen) {
        Screen.CHAT -> ChatScreen(
            viewModel = viewModel,
            onOpenSettings = { screen = Screen.SETTINGS },
        )

        Screen.SETTINGS -> SettingsScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.CHAT },
        )
    }
}
