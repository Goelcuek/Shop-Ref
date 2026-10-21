package com.shopref.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shopref.core.material.IsoGroup
import com.shopref.core.material.Material
import com.shopref.core.material.MaterialLibrary

/** Card showing the selected workpiece material; tap to change. */
@Composable
fun MaterialSelector(material: Material, onSelect: (Material) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    ElevatedCard(modifier.fillMaxWidth().clickable { open = true }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IsoBadge(material.group)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Workpiece material", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(material.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${material.examples} · ${material.hardness}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Search, contentDescription = "Change material")
        }
    }
    if (open) {
        MaterialPickerDialog(
            current = material,
            onDismiss = { open = false },
            onSelect = {
                open = false
                onSelect(it)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialPickerDialog(current: Material, onDismiss: () -> Unit, onSelect: (Material) -> Unit) {
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<IsoGroup?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(12.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Select material", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search: 6061, 304, Inconel, PEEK…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("materialSearch"),
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(selected = group == null, onClick = { group = null }, label = { Text("All") })
                    IsoGroup.entries.forEach { g ->
                        FilterChip(
                            selected = group == g,
                            onClick = { group = if (group == g) null else g },
                            label = { Text("${g.label} · ${g.description}") },
                        )
                    }
                }
                val results = MaterialLibrary.search(query, group)
                LazyColumn(Modifier.weight(1f)) {
                    items(results, key = { it.id }) { m ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(m) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IsoBadge(m.group)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    m.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (m.id == current.id) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text("${m.examples} · ${m.hardness}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
