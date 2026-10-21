package com.shopref.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shopref.app.ui.reference.ReferencePage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches the app and walks every screen so a crash anywhere fails CI. */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun allCalculatorTabsRender() {
        rule.onNodeWithText("Milling").assertIsDisplayed()
        for (tab in listOf("DRILL", "TAP", "TURN", "REFERENCE", "MILL")) {
            rule.onNodeWithTag("tab_$tab").performClick()
            rule.waitForIdle()
        }
    }

    @Test
    fun everyMillingToolTypeCalculates() {
        var current = "Square end mill"
        for (next in listOf("Ball end mill", "Corner-radius (bull nose)", "Chamfer mill / spot", "Indexable face / shoulder mill", "Square end mill")) {
            rule.onNodeWithText(current).performClick()
            rule.onNodeWithText(next).performClick()
            rule.waitForIdle()
            current = next
        }
    }

    @Test
    fun unitToggleAndMaterialPicker() {
        rule.onNodeWithTag("unitsToggle").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Change material").performClick()
        rule.onNodeWithTag("materialSearch").performTextInput("Inconel 718")
        rule.onNodeWithText("Nickel alloy, aged").performClick()
        rule.waitForIdle()
        for (tab in listOf("DRILL", "TAP", "TURN", "MILL")) {
            rule.onNodeWithTag("tab_$tab").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("unitsToggle").performClick()
    }

    @Test
    fun everyReferencePageOpens() {
        rule.onNodeWithTag("tab_REFERENCE").performClick()
        for (page in ReferencePage.entries) {
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(page.title))
            rule.onNodeWithText(page.title).performClick()
            rule.waitForIdle()
            rule.onNodeWithContentDescription("Back").performClick()
            rule.waitForIdle()
        }
    }

    @Test
    fun settingsOpen() {
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Milling machine").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Milling").assertIsDisplayed()
    }
}
