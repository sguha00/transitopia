package com.transitopia.bartwidget

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import com.transitopia.bartwidget.ui.BartTheme
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.transitopia.bartwidget.data.BartLogic
import com.transitopia.bartwidget.data.BartRepository
import com.transitopia.bartwidget.widget.BartWidgetProvider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = BartRepository(this)
        val stations = repo.loadStations()
        val terminals = stations
        setContent {
            BartTheme {
                var origin by remember { mutableStateOf(stations.firstOrNull { it.id == "24TH" }?.id ?: stations.first().id) }
                var terminal by remember { mutableStateOf(terminals.firstOrNull { it.id == "EMBR" }?.id ?: terminals.first().id) }
                var result by remember { mutableStateOf<String>("Tap Check now") }
                val scope = rememberCoroutineScope()
                Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(getString(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    Text("Add the widget to your home screen, then pick a station and a terminal station. " +
                        "It shows the next train (GTFS-Realtime) from that station that stops at the chosen destination. " +
                        "Example: 24th Street / Mission → Embarcadero.", style = MaterialTheme.typography.bodyMedium)
                    Text("Station: ${stations.firstOrNull { it.id == origin }?.name ?: origin}")
                    Text("Destination: ${terminals.firstOrNull { it.id == terminal }?.name ?: terminal}")
                    Text("Preview (first station → first terminal) — configure the real widget from the home screen.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        scope.launch {
                            result = "Checking…"
                            result = repo.nextTrains(origin, terminal, 3).fold(
                                onSuccess = { list -> if (list.isEmpty()) "No upcoming trains to that destination." else list.mapIndexed { i, t -> "${i + 1}. ${BartLogic.formatEta(t.etaMinutes)} at ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(t.departureEpochSec * 1000))}" }.joinToString("\n") },
                                onFailure = { "Error: ${it.message}" })
                        }
                    }) { Text("Check now") }
                    Text(result)
                    Button(onClick = { BartWidgetProvider.refreshAll(this@MainActivity) }) { Text("Refresh all widgets") }
                }
            }
        }
    }
}
