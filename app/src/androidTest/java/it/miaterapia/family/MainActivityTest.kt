package it.miaterapia.family

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun mainNavigationExposesRequiredAreas() {
        rule.onAllNodesWithText("Oggi", useUnmergedTree = true).onFirst().assertIsDisplayed()
        rule.onNodeWithText("Terapie", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Aggiungi terapia").assertIsDisplayed()
        rule.onNodeWithText("Scorte", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Registra rifornimento").assertIsDisplayed()
        rule.onNodeWithText("Registro", useUnmergedTree = true).performClick()
        rule.onAllNodesWithText("Registro", useUnmergedTree = true).onFirst().assertIsDisplayed()
        rule.onNodeWithText("Altro", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Aggiungi profilo").assertIsDisplayed()
    }

    @Test fun settingsExposeReportsPermissionsAndDrive() {
        rule.onNodeWithText("Altro", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Notifiche").assertIsDisplayed()
        rule.onNodeWithText("Report aderenza PDF").assertIsDisplayed()
        rule.onNodeWithText("Richiesta farmaci PDF").assertIsDisplayed()
        rule.onNodeWithText("Collega e verifica Drive").assertIsDisplayed()
    }
}
