package com.sameer.ondeviceai.experiments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sameer.ondeviceai.DeviceInfo
import com.sameer.ondeviceai.ui.theme.PlaygroundTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (Experiment) -> Unit) {
    val context = LocalContext.current
    val device = remember { DeviceInfo.read(context) }

    Scaffold(topBar = { TopAppBar(title = { Text("On-Device AI Playground") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { DeviceCard(device) }
            items(Experiment.entries) { experiment ->
                ExperimentCard(experiment, onClick = { onOpen(experiment) })
            }
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(device.model, style = MaterialTheme.typography.titleMedium)
            Text(device.androidVersion, style = MaterialTheme.typography.bodyMedium)
            Text(
                "RAM %.1f GB total, %.1f GB free · %d CPU cores".format(
                    device.totalRamGb, device.availableRamGb, device.cpuCores,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ExperimentCard(experiment: Experiment, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(experiment.title, style = MaterialTheme.typography.titleMedium)
            Text(experiment.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                experiment.engine,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview
@Composable
private fun ExperimentCardPreview() {
    PlaygroundTheme { ExperimentCard(Experiment.LlmChat, onClick = {}) }
}
