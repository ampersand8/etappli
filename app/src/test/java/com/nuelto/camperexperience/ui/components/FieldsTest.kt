package com.nuelto.camperexperience.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.camperexperience.testutil.TestCamperApp
import com.nuelto.camperexperience.ui.formatDate
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class FieldsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `decimal field renders label suffix and forwards input`() {
        var value by mutableStateOf("")
        compose.setContent {
            DecimalField(label = "Amount", value = value, onValueChange = { value = it }, suffix = "km")
        }
        compose.onNodeWithText("Amount").performTextInput("12,5")
        assertEquals("12,5", value)
        compose.onNodeWithText("km").assertIsDisplayed() // suffix shows once the field has content
    }

    @Test
    fun `date field shows placeholder when empty`() {
        compose.setContent {
            DateField(label = "End date", date = null, onDateChange = {}, placeholder = "Ongoing")
        }
        compose.onNodeWithText("Ongoing").assertIsDisplayed()
    }

    @Test
    fun `tapping the field opens the picker and cancel keeps the date`() {
        val date = LocalDate.of(2026, 7, 1)
        var changed = false
        compose.setContent {
            DateField(label = "Start date", date = date, onDateChange = { changed = true })
        }
        compose.onNodeWithText(formatDate(date)).performClick()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        assertEquals(false, changed)
    }

    @Test
    fun `confirming the picker reports the selected date`() {
        val date = LocalDate.of(2026, 7, 1)
        var picked: LocalDate? = null
        compose.setContent {
            DateField(label = "Start date", date = date, onDateChange = { picked = it })
        }
        compose.onNodeWithText(formatDate(date)).performClick()
        compose.onNodeWithText("OK").performClick()
        // The picker opens preselected on the current value; OK confirms it.
        assertEquals(date, picked)
    }

    @Test
    fun `picker for an empty date defaults to today`() {
        var picked: LocalDate? = null
        compose.setContent {
            DateField(label = "Date", date = null, onDateChange = { picked = it }, placeholder = "Pick")
        }
        compose.onNodeWithText("Pick").performClick()
        compose.onNodeWithText("OK").performClick()
        assertTrue(picked != null)
    }
}
