package com.shopref.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.shopref.core.i18n.Lang

/** Language used by the UI and passed to the calculators for their advice text. */
val LocalLang = staticCompositionLocalOf { Lang.EN }
