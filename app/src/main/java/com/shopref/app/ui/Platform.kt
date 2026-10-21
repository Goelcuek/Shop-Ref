package com.shopref.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/** Platform back-press hook (kept in one file so the rest of the UI is platform neutral). */
@Composable
fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
