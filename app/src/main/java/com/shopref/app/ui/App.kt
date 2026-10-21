package com.shopref.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.shopref.app.state.AppSettings
import com.shopref.app.state.DrillingState
import com.shopref.app.state.KeyValueStore
import com.shopref.app.state.MillingState
import com.shopref.app.state.Session
import com.shopref.app.state.TappingState
import com.shopref.app.state.TurningState
import com.shopref.app.ui.reference.ReferencePage
import com.shopref.app.ui.reference.ReferenceScreen
import com.shopref.app.ui.screens.DrillingScreen
import com.shopref.app.ui.screens.MillingScreen
import com.shopref.app.ui.screens.SettingsScreen
import com.shopref.app.ui.screens.TappingScreen
import com.shopref.app.ui.screens.TurningScreen
import com.shopref.core.units.UnitSystem

enum class Tab(val label: String, val title: String, val icon: ImageVector) {
    MILL("Mill", "Milling", AppIcons.Mill),
    DRILL("Drill", "Drilling & reaming", AppIcons.Drill),
    TAP("Tap", "Tapping & threads", AppIcons.Tap),
    TURN("Turn", "Turning", AppIcons.Turn),
    REFERENCE("Reference", "Reference", AppIcons.Book),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopRefApp(store: KeyValueStore) {
    val settings = remember { AppSettings(store) }
    val session = remember { Session(store) }
    val milling = remember { MillingState() }
    val drilling = remember { DrillingState() }
    val tapping = remember { TappingState() }
    val turning = remember { TurningState() }

    var tab by rememberSaveable { mutableStateOf(Tab.MILL) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var refPage by rememberSaveable { mutableStateOf<ReferencePage?>(null) }

    PlatformBackHandler(enabled = showSettings) { showSettings = false }
    PlatformBackHandler(enabled = !showSettings && tab == Tab.REFERENCE && refPage != null) { refPage = null }

    val canGoBack = showSettings || (tab == Tab.REFERENCE && refPage != null)
    val title = when {
        showSettings -> "Settings"
        tab == Tab.REFERENCE && refPage != null -> refPage!!.title
        else -> tab.title
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = { if (showSettings) showSettings = false else refPage = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!showSettings) {
                        TextButton(
                            onClick = {
                                settings.updateUnits(if (settings.units == UnitSystem.METRIC) UnitSystem.IMPERIAL else UnitSystem.METRIC)
                            },
                            modifier = Modifier.testTag("unitsToggle"),
                        ) {
                            Text(if (settings.units == UnitSystem.METRIC) "mm" else "inch")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!showSettings) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = {
                                if (tab == t && t == Tab.REFERENCE) refPage = null
                                tab = t
                            },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        if (showSettings) {
            SettingsScreen(settings, contentModifier)
        } else {
            when (tab) {
                Tab.MILL -> MillingScreen(settings, session, milling, contentModifier)
                Tab.DRILL -> DrillingScreen(settings, session, drilling, contentModifier)
                Tab.TAP -> TappingScreen(settings, session, tapping, contentModifier)
                Tab.TURN -> TurningScreen(settings, session, turning, contentModifier)
                Tab.REFERENCE -> ReferenceScreen(settings, refPage, { refPage = it }, contentModifier)
            }
        }
    }
}
