package com.sameer.ondeviceai

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.sameer.ondeviceai.experiments.Experiment
import com.sameer.ondeviceai.experiments.ExperimentScreen
import com.sameer.ondeviceai.experiments.HomeScreen

/**
 * Top-level navigation: the home list, or one open experiment.
 * Plain state instead of a navigation library while there is only one level.
 */
@Composable
fun PlaygroundApp() {
    var openExperiment by rememberSaveable { mutableStateOf<Experiment?>(null) }

    val current = openExperiment
    if (current == null) {
        HomeScreen(onOpen = { openExperiment = it })
    } else {
        BackHandler { openExperiment = null }
        ExperimentScreen(experiment = current, onBack = { openExperiment = null })
    }
}
