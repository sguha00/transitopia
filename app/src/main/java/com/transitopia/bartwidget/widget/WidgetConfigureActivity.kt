package com.transitopia.bartwidget.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import com.transitopia.bartwidget.ui.BartTheme
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.transitopia.bartwidget.data.BartRepository
import com.transitopia.bartwidget.data.Station

@OptIn(ExperimentalMaterial3Api::class)
class WidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val isEditing = WidgetPrefs.origin(this, appWidgetId) != null &&
            WidgetPrefs.terminal(this, appWidgetId) != null
        setResult(Activity.RESULT_CANCELED)
        val stations = BartRepository(this).loadStations()
        val terminals = stations // any station can be a destination (trains pass through intermediate stops)
        setContent {
            BartTheme {
                var origin by remember { mutableStateOf(WidgetPrefs.origin(this, appWidgetId) ?: stations.firstOrNull { it.id == "24TH" }?.id ?: stations.firstOrNull()?.id) }
                var terminal by remember { mutableStateOf(WidgetPrefs.terminal(this, appWidgetId) ?: terminals.firstOrNull { it.id == "EMBR" }?.id ?: terminals.firstOrNull()?.id) }
                var expanded by remember { mutableStateOf<String?>(null) }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                scrolledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            title = { Text(getString(com.transitopia.bartwidget.R.string.widget_setup_title)) },
                            navigationIcon = {
                                IconButton(onClick = {
                                    setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                    finish()
                                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                            }
                        )
                    }
                ) { padding ->
                Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Pick where you board and where you want to go. " +
                        "The widget shows the next GTFS-Realtime departure matching both.", style = MaterialTheme.typography.bodyMedium)
                    StationPicker("Station (board here)", stations, origin, expanded == "o",
                        { expanded = if (expanded == "o") null else "o" }) { origin = it }
                    StationPicker("Destination station (e.g. 24th St)", terminals, terminal, expanded == "t",
                        { expanded = if (expanded == "t") null else "t" }) { terminal = it }
                    Button(
                        onClick = {
                            if (origin != null && terminal != null && origin != terminal) {
                                WidgetPrefs.save(this@WidgetConfigureActivity, appWidgetId, origin!!, terminal!!)
                                BartWidgetProvider().startLive(this@WidgetConfigureActivity, appWidgetId)
                                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                finish()
                            }
                        },
                        enabled = origin != null && terminal != null && origin != terminal,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (isEditing) "Update Widget" else "Add Widget") }
                    if (origin == terminal) Text("Station and terminal must be different.", color = MaterialTheme.colorScheme.error)
                }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationPicker(label: String, options: List<Station>, selectedId: String?, expanded: Boolean, onToggle: () -> Unit, onPick: (String) -> Unit) {
    val selected = options.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { onToggle() }) {
        OutlinedTextField(value = selected?.name ?: "", onValueChange = {}, readOnly = true,
            label = { Text(label) }, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = onToggle) {
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                options.forEach { s ->
                    DropdownMenuItem(text = { Text(s.name) }, onClick = { onPick(s.id); onToggle() })
                }
            }
        }
    }
}
