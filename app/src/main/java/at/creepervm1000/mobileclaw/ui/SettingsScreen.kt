package at.creepervm1000.mobileclaw.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import at.creepervm1000.mobileclaw.llm.Provider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AgentViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateSafe()
    val crons by viewModel.crons.collectAsStateSafe()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        // TopAppBar applies its own status-bar inset; the scroll content below applies the
        // bottom one. Leaving the default here would count both twice.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                // Applied inside the scroll container so the content scrolls under the nav bar
                // rather than the whole list being inset from it.
                .windowInsetsPadding(
                    WindowInsets.safeDrawing
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle("Model")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.provider == Provider.OPENAI,
                    onClick = {
                        viewModel.updateSettings {
                            it.copy(
                                provider = Provider.OPENAI,
                                baseUrl = "https://api.openai.com/v1",
                                model = "gpt-4o",
                            )
                        }
                    },
                    label = { Text("OpenAI-compatible") },
                )
                FilterChip(
                    selected = settings.provider == Provider.ANTHROPIC,
                    onClick = {
                        viewModel.updateSettings {
                            it.copy(
                                provider = Provider.ANTHROPIC,
                                baseUrl = "https://api.anthropic.com/v1",
                                model = "claude-opus-4-20250514",
                            )
                        }
                    },
                    label = { Text("Anthropic") },
                )
            }

            Text(
                text = when (settings.provider) {
                    Provider.OPENAI ->
                        "Calls POST {base}/chat/completions. Works with OpenAI, OpenRouter, Groq, " +
                            "Together, Ollama, LM Studio and vLLM."

                    Provider.ANTHROPIC ->
                        "Calls POST {base}/messages with the anthropic-version header."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val baseUrlField = rememberWriteThrough(settings.baseUrl) { value ->
                viewModel.updateSettings { it.copy(baseUrl = value) }
            }
            OutlinedTextField(
                value = baseUrlField.value,
                onValueChange = baseUrlField.onValueChange,
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Password fields suppress the long-press text toolbar, so pasting a key that way is
            // unreliable. An explicit reveal toggle and paste button work regardless.
            var keyVisible by remember { mutableStateOf(false) }
            val clipboard = LocalClipboardManager.current

            val apiKeyField = rememberWriteThrough(settings.apiKey) { value ->
                viewModel.updateSettings { it.copy(apiKey = value) }
            }
            OutlinedTextField(
                value = apiKeyField.value,
                onValueChange = apiKeyField.onValueChange,
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (keyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                ),
                trailingIcon = {
                    Row {
                        IconButton(onClick = {
                            val pasted = clipboard.getText()?.text?.trim().orEmpty()
                            if (pasted.isNotEmpty()) {
                                apiKeyField.onValueChange(pasted)
                            } else {
                                scope.launch { snackbar.showSnackbar("Clipboard is empty") }
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste API key")
                        }
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                if (keyVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = if (keyVisible) "Hide key" else "Show key",
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            val modelField = rememberWriteThrough(settings.model) { value ->
                viewModel.updateSettings { it.copy(model = value) }
            }
            OutlinedTextField(
                value = modelField.value,
                onValueChange = modelField.onValueChange,
                label = { Text("Model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            LabelledSlider(
                label = "Temperature",
                value = settings.temperature.toFloat(),
                range = 0f..2f,
                steps = 19,
                display = String.format(java.util.Locale.US, "%.1f", settings.temperature),
            ) { value ->
                viewModel.updateSettings { it.copy(temperature = value.toDouble()) }
            }

            LabelledSlider(
                label = "Max tokens per reply",
                value = settings.maxTokens.toFloat(),
                range = 512f..16384f,
                steps = 0,
                display = settings.maxTokens.toString(),
            ) { value ->
                viewModel.updateSettings { it.copy(maxTokens = value.toInt()) }
            }

            LabelledSlider(
                label = "Max tool rounds per turn",
                value = settings.maxToolIterations.toFloat(),
                range = 1f..40f,
                steps = 38,
                display = settings.maxToolIterations.toString(),
            ) { value ->
                viewModel.updateSettings { it.copy(maxToolIterations = value.toInt()) }
            }

            HorizontalDivider()
            SectionTitle("Background")

            SwitchRow(
                title = "Keep the agent awake",
                subtitle = "Runs a foreground service so battery checks and scheduled tasks fire " +
                    "while the app is closed. Shows a persistent notification.",
                checked = settings.serviceEnabled,
                onCheckedChange = viewModel::setServiceEnabled,
            )

            SwitchRow(
                title = "Start on boot",
                subtitle = "Re-launch the agent after the phone restarts. Android may block this " +
                    "on newer versions; open the app once if it doesn't come back.",
                checked = settings.startOnBoot,
                onCheckedChange = { value ->
                    viewModel.updateSettings { it.copy(startOnBoot = value) }
                },
            )

            Text(
                text = "Battery alerts fire at 15%, 5%, and critically at 2%, 1% and 0%. Each level " +
                    "alerts once per discharge and re-arms after charging.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Re-read on every resume: the user grants this in Android's settings app and comes
            // back, so the state changes while this screen is stopped.
            val notificationsAllowed = rememberOnResume { viewModel.hasNotificationPermission() }

            OutlinedButton(
                onClick = { viewModel.openNotificationSettings() },
                enabled = !notificationsAllowed,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (notificationsAllowed) "Notifications: allowed"
                    else "Allow notifications in Android settings"
                )
            }

            if (!notificationsAllowed) {
                Text(
                    "This opens Android's settings page — the app never shows its own permission " +
                        "dialog. Without this the agent can't reach you when the app is closed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()
            SectionTitle("Identity")

            val agentNameField = rememberWriteThrough(settings.agentName) { value ->
                viewModel.updateSettings { it.copy(agentName = value) }
            }
            OutlinedTextField(
                value = agentNameField.value,
                onValueChange = agentNameField.onValueChange,
                label = { Text("Agent name") },
                supportingText = {
                    Text("The agent can rename itself with set_agent_name. This is the notification title.")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(
                onClick = {
                    viewModel.exportFiles { message ->
                        scope.launch { snackbar.showSnackbar(message) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export IDENTITY.md & MEMORY.md to Downloads")
            }

            HorizontalDivider()
            SectionTitle("Scheduled tasks (${crons.size})")

            if (crons.isEmpty()) {
                Text(
                    "The agent hasn't scheduled anything for itself yet. It can do so with " +
                        "create_cron — ask it to check something periodically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                crons.forEach { job ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(job.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (job.intervalMinutes == 0) {
                                        "every tick (~10s) · ${job.runCount} runs"
                                    } else {
                                        "every ${job.intervalMinutes} min · ${job.runCount} runs"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = job.prompt,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.deleteCron(job.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete task")
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            SectionTitle("Conversation")

            Button(
                onClick = { viewModel.clearConversation() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear conversation history")
            }

            Text(
                "Clearing the chat does not erase MEMORY.md — that's the point of it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(display, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
        )
    }
}
