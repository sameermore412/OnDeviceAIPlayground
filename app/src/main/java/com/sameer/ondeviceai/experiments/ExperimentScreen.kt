package com.sameer.ondeviceai.experiments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sameer.ondeviceai.llmchat.LlmChatScreen

/** Shell around each experiment; routes to the experiment's own content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentScreen(experiment: Experiment, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(experiment.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        // consumeWindowInsets stops screens that add imePadding from double-counting the nav bar.
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when (experiment) {
                // Each experiment replaces its branch with a real screen as it gets built.
                Experiment.LlmChat -> LlmChatScreen()
                Experiment.TextRecognition,
                Experiment.ImageLabeling,
                Experiment.SmartReply -> ComingSoon(experiment)
            }
        }
    }
}

@Composable
private fun ComingSoon(experiment: Experiment) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Not built yet", style = MaterialTheme.typography.titleMedium)
        Text(experiment.description, textAlign = TextAlign.Center)
    }
}
