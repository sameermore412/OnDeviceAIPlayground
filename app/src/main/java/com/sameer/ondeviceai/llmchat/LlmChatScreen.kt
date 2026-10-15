package com.sameer.ondeviceai.llmchat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sameer.ondeviceai.AppState
import com.sameer.ondeviceai.DeviceInfo

@Composable
fun LlmChatScreen(viewModel: LlmChatViewModel = viewModel()) {
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().imePadding()) {
        ModelPicker(
            models = models,
            selected = selected,
            enabled = !isGenerating && modelState != ModelState.Loading,
            onSelect = viewModel::select,
            onRefresh = viewModel::refreshModels,
        )
        Box(Modifier.weight(1f)) {
            ModelContent(viewModel, modelState, selected)
        }
    }
}

@Composable
private fun ModelContent(viewModel: LlmChatViewModel, modelState: ModelState, selected: ModelSpec) {
    when (val state = modelState) {
        ModelState.NotDownloaded -> DownloadPrompt(selected, onDownload = viewModel::download)
        is ModelState.Downloading -> DownloadProgress(state, onCancel = viewModel::cancelDownload)
        ModelState.Loading -> Centered {
            CircularProgressIndicator()
            Text("Loading ${selected.displayName}…")
            Text(
                "The first load can take a while.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is ModelState.Failed -> Centered {
            Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
            Text(state.message, textAlign = TextAlign.Center)
            Button(onClick = viewModel::retry) { Text("Try again") }
        }
        is ModelState.Ready -> Chat(viewModel, backend = state.backend)
    }
}

@Composable
private fun ModelPicker(
    models: List<ModelSpec>,
    selected: ModelSpec,
    enabled: Boolean,
    onSelect: (ModelSpec) -> Unit,
    onRefresh: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 4.dp)) {
        TextButton(onClick = { expanded = true }, enabled = enabled) {
            Text(selected.displayName)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose model")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(model.displayName)
                            Text(
                                "%.1f GB · %s".format(
                                    model.approxSizeGb,
                                    if (model.downloadUrl != null) "built in" else "copied in",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(model)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Look for new model files") },
                onClick = {
                    expanded = false
                    onRefresh()
                },
            )
        }
    }
}

@Composable
private fun DownloadPrompt(model: ModelSpec, onDownload: () -> Unit) {
    val context = LocalContext.current
    val ramGb = remember { DeviceInfo.read(context).totalRamGb }
    Centered {
        Text(model.displayName, style = MaterialTheme.typography.titleLarge)
        Text(
            "Runs fully on this phone with LiteRT-LM and can call tools inside the app. " +
                "It's a one-time download of about ${model.approxSizeGb} GB.",
            textAlign = TextAlign.Center,
        )
        model.recommendedRamGb?.let { recommended ->
            Text(
                "Recommended: $recommended GB of RAM. This phone has %.1f GB.".format(ramGb),
                style = MaterialTheme.typography.bodySmall,
                color = if (ramGb < recommended - 0.5) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = onDownload) { Text("Download model") }
        Text(
            "Or copy any .litertlm model into Android/data/com.sameer.ondeviceai/files/models/ " +
                "and pick it from the menu above.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DownloadProgress(state: ModelState.Downloading, onCancel: () -> Unit) {
    val mb = 1024.0 * 1024
    Centered {
        Text("Downloading model", style = MaterialTheme.typography.titleMedium)
        val total = state.totalBytes
        if (total != null) {
            LinearProgressIndicator(
                progress = { (state.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("%.0f of %.0f MB".format(state.downloadedBytes / mb, total / mb))
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("%.0f MB".format(state.downloadedBytes / mb))
        }
        Text(
            "You can leave this screen; the download keeps going while the app is open.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onCancel) { Text("Pause") }
    }
}

private val SUGGESTIONS = listOf(
    "What's my battery level?",
    "Turn on the flashlight",
    "Switch the app to dark mode",
    "Add a note: buy milk",
    "What day is it?",
    "Search the web for the latest Android version",
)

@Composable
private fun Chat(viewModel: LlmChatViewModel, backend: String) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val stats by viewModel.lastStats.collectAsStateWithLifecycle()
    val notes by AppState.notes.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Follow the newest message, including while a reply streams in.
    val lastText = (items.lastOrNull() as? ChatItem.Model)?.text?.length
    LaunchedEffect(items.size, lastText) {
        if (items.isNotEmpty()) listState.scrollToItem(items.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        StatusRow(
            backend = backend,
            stats = stats,
            noteCount = notes.size,
            canReset = items.isNotEmpty() && !isGenerating,
            onNewChat = viewModel::newChat,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    Text(
                        "Ask anything, or try one of these to see the model call the app's tools:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(items, key = { it.id }) { item ->
                when (item) {
                    is ChatItem.User -> Bubble(item.text, fromUser = true)
                    is ChatItem.Model -> Bubble(item.text.ifEmpty { "…" }, fromUser = false)
                    is ChatItem.Tool -> ToolCallCard(item.call)
                    is ChatItem.Error -> Text(item.message, color = MaterialTheme.colorScheme.error)
                }
            }
            if (isGenerating && items.lastOrNull() !is ChatItem.Model) {
                item { Text("Thinking…", style = MaterialTheme.typography.bodySmall) }
            }
        }

        if (items.isEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SUGGESTIONS.forEach { suggestion ->
                    SuggestionChip(onClick = { viewModel.send(suggestion) }, label = { Text(suggestion) })
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                maxLines = 4,
            )
            if (isGenerating) {
                FilledIconButton(onClick = viewModel::stop) {
                    Icon(Icons.Filled.Close, contentDescription = "Stop")
                }
            } else {
                FilledIconButton(
                    onClick = {
                        viewModel.send(input)
                        input = ""
                    },
                    enabled = input.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    backend: String,
    stats: ReplyStats?,
    noteCount: Int,
    canReset: Boolean,
    onNewChat: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val parts = buildList {
            add(backend)
            if (stats != null) {
                add("%.1f tok/s".format(stats.tokensPerSecond))
                add("first token %.1f s".format(stats.timeToFirstTokenMs / 1000.0))
            }
            if (noteCount > 0) add("$noteCount notes")
        }
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onNewChat, enabled = canReset) { Text("New chat") }
    }
}

@Composable
private fun Bubble(text: String, fromUser: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    if (fromUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (fromUser) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolCallCard(call: ToolCallEvent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Filled.Build,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${call.name}(${call.arguments})",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "→ ${call.result}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}
