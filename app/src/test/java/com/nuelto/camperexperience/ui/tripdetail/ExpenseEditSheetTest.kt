package com.nuelto.camperexperience.ui.tripdetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.camperexperience.data.model.Expense
import com.nuelto.camperexperience.data.model.ExpenseType
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.UserSettings
import com.nuelto.camperexperience.testutil.TestCamperApp
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class ExpenseEditSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val saved = mutableListOf<Expense>()
    private var dismissed = 0

    private val locatedStops = listOf(
        Stop(id = "s1", tripId = "t1", location = LatLng(47.0, 7.0), orderIndex = 0),
        Stop(id = "s2", tripId = "t1", location = LatLng(47.5, 7.5), orderIndex = 1),
    )

    private fun setContent(initial: Expense? = null, stops: List<Stop> = locatedStops) {
        compose.setContent {
            ExpenseEditSheet(
                initial = initial,
                stops = stops,
                settings = UserSettings(),
                onSave = { saved += it },
                onDismiss = { dismissed++ },
            )
        }
    }

    @Test
    fun `new expense defaults to fuel with a disabled save`() {
        setContent()
        compose.onNodeWithText("New expense").assertIsDisplayed()
        compose.onNodeWithText("Estimate from distance…").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `save passes the parsed amount type and trimmed label`() {
        setContent()
        compose.onNodeWithText("Road tax").performClick()
        compose.onNodeWithText("Amount").performTextInput("61,40")
        compose.onNodeWithText("Label (optional)").performTextInput(" Vignette ")
        compose.onNodeWithText("Save").performClick()
        val expense = saved.single()
        assertEquals(ExpenseType.ROAD_TAX, expense.type)
        assertEquals(61.40, expense.amount, 1e-9)
        assertEquals("Vignette", expense.label)
        assertFalse(expense.isEstimate)
    }

    @Test
    fun `estimator link is only offered for fuel`() {
        setContent()
        compose.onNodeWithText("Other").performClick()
        compose.onNodeWithText("Estimate from distance…").assertDoesNotExist()
        compose.onNodeWithText("Fuel").performClick()
        compose.onNodeWithText("Estimate from distance…").assertIsDisplayed()
    }

    @Test
    fun `estimator prefills distance and fills the amount as estimate`() {
        setContent()
        compose.onNodeWithText("Estimate from distance…").performClick()
        compose.onNodeWithText("Fuel estimate").assertIsDisplayed()
        compose.onNodeWithText("Use this amount").assertIsEnabled()
        compose.onNodeWithText("Use this amount").performClick()
        compose.onNodeWithText("Save").performClick()
        val expense = saved.single()
        assertTrue(expense.isEstimate)
        assertTrue(expense.amount > 0.0)
    }

    @Test
    fun `editing the amount by hand clears the estimate flag`() {
        setContent()
        compose.onNodeWithText("Estimate from distance…").performClick()
        compose.onNodeWithText("Use this amount").performClick()
        compose.onNodeWithText("Amount").performTextReplacement("99")
        compose.onNodeWithText("Save").performClick()
        assertFalse(saved.single().isEstimate)
        assertEquals(99.0, saved.single().amount, 1e-9)
    }

    @Test
    fun `estimator without a route offers no amount`() {
        setContent(stops = emptyList())
        compose.onNodeWithText("Estimate from distance…").performClick()
        compose.onNodeWithText("—").assertExists()
        compose.onNodeWithText("Use this amount").assertIsNotEnabled()
    }

    @Test
    fun `broken estimator input disables the use button`() {
        setContent()
        compose.onNodeWithText("Estimate from distance…").performClick()
        compose.onNodeWithText("Distance").performTextReplacement("abc")
        compose.onNodeWithText("—").assertExists()
        compose.onNodeWithText("Use this amount").assertIsNotEnabled()
    }

    @Test
    fun `existing expense is prefilled for editing`() {
        setContent(
            Expense(
                id = "e1", tripId = "t1", type = ExpenseType.OTHER, amount = 24.0,
                date = LocalDate.of(2026, 5, 16), label = "Seilbahn",
            ),
        )
        compose.onNodeWithText("Edit expense").assertIsDisplayed()
        compose.onNodeWithText("24.0").assertIsDisplayed()
        compose.onNodeWithText("Seilbahn").assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        assertEquals("e1", saved.single().id)
        assertEquals(24.0, saved.single().amount, 1e-9)
    }

    @Test
    fun `zero amount on an existing expense shows an empty field`() {
        setContent(Expense(id = "e1", tripId = "t1", amount = 0.0))
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }
}
