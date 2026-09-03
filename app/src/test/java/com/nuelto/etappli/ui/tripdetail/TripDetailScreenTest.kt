package com.nuelto.etappli.ui.tripdetail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.nuelto.etappli.domain.RoutedLeg
import org.robolectric.Shadows.shadowOf
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.nuelto.etappli.ui.tripedit.displayName
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.etappli.data.InMemorySettingsRepository
import com.nuelto.etappli.data.InMemoryTripRepository
import com.nuelto.etappli.data.model.Expense
import com.nuelto.etappli.data.model.ExpenseType
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.data.model.StopElevation
import com.nuelto.etappli.data.model.StopLeg
import com.nuelto.etappli.data.model.TransitRide
import com.nuelto.etappli.data.model.TravelMode
import com.nuelto.etappli.data.model.StopState
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.testutil.TestCamperApp
import com.nuelto.etappli.ui.map.LocalMapProvider
import com.nuelto.etappli.ui.map.PlaceholderMapProvider
import com.nuelto.etappli.ui.formatDate
import com.nuelto.etappli.ui.formatDriveFromHere
import java.time.LocalDateTime
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class TripDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val tripRepository = InMemoryTripRepository(seed = false)
    private val settingsRepository = InMemorySettingsRepository()
    private val events = mutableListOf<String>()

    private fun seed(withLocations: Boolean = false, withExpenses: Boolean = true) = runBlocking {
        tripRepository.upsertTrip(
            Trip(
                id = "t1", name = "Jura", startDate = LocalDate.of(2026, 7, 1),
                endDate = LocalDate.of(2026, 7, 4), notes = "Rainy but great", status = TripStatus.DONE,
            ),
        )
        tripRepository.upsertStop(
            Stop(
                id = "s1", tripId = "t1", name = "Camp A", nights = 2, campingCostTotal = 40.0,
                location = if (withLocations) LatLng(47.0, 7.0) else null, orderIndex = 0,
            ),
        )
        if (withExpenses) {
            tripRepository.upsertExpense(
                Expense(
                    id = "e1", tripId = "t1", type = ExpenseType.ROAD_TAX, amount = 30.0,
                    date = LocalDate.of(2026, 7, 2), label = "Vignette",
                ),
            )
            tripRepository.upsertExpense(
                Expense(
                    id = "e2", tripId = "t1", type = ExpenseType.OTHER, amount = 5.0,
                    date = LocalDate.of(2026, 7, 3), isEstimate = true,
                ),
            )
        }
    }

    private fun seedPlan() = runBlocking {
        tripRepository.upsertTrip(
            Trip(id = "p1", name = "Ticino-Tour", startDate = LocalDate.of(2027, 6, 10), status = TripStatus.PLANNED),
        )
        tripRepository.upsertStop(
            Stop(
                id = "s1", tripId = "p1", name = "Camping Lido", nights = 2, orderIndex = 0,
                costKnown = false, location = LatLng(47.05, 8.31), arrivalDate = LocalDate.of(2027, 6, 10),
            ),
        )
        tripRepository.upsertStop(
            Stop(
                id = "s2", tripId = "p1", name = "Camping Delta", nights = 3, campingCostTotal = 186.0,
                orderIndex = 1, location = LatLng(46.16, 8.79), arrivalDate = LocalDate.of(2027, 6, 12),
            ),
        )
    }

    private fun arrivalOf(stopId: String) = runBlocking {
        tripRepository.stops("p1").first().first { it.id == stopId }.arrivalDate
    }

    private fun seedActive() = runBlocking {
        tripRepository.upsertTrip(
            Trip(id = "a1", name = "Unterwegs", startDate = LocalDate.of(2026, 8, 20), status = TripStatus.ACTIVE),
        )
        tripRepository.upsertStop(
            Stop(
                id = "done", tripId = "a1", name = "Camp Done", nights = 2, campingCostTotal = 96.0,
                orderIndex = 0, state = StopState.DONE, arrivalDate = LocalDate.of(2026, 8, 20),
            ),
        )
        tripRepository.upsertStop(
            Stop(
                id = "cur", tripId = "a1", name = "Camp Current", nights = 3, orderIndex = 1,
                costKnown = false, location = LatLng(46.16, 8.79), arrivalDate = LocalDate.of(2026, 8, 22),
            ),
        )
        tripRepository.upsertStop(
            Stop(
                id = "up", tripId = "a1", name = "Camp Later", nights = 1, orderIndex = 2,
                costKnown = false, arrivalDate = LocalDate.of(2026, 8, 25),
            ),
        )
    }

    private fun setContent(
        tripId: String = "t1",
        viewModel: TripDetailViewModel = TripDetailViewModel(
            SavedStateHandle(mapOf("tripId" to tripId)),
            tripRepository,
            settingsRepository,
        ),
        // Answers any permission request with this, so the grant flow can be driven.
        permissionAnswer: Boolean? = null,
    ) {
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = answeringRegistry(permissionAnswer ?: false)
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalMapProvider provides PlaceholderMapProvider,
                LocalActivityResultRegistryOwner provides registryOwner,
            ) {
                TripDetailScreen(
                    onBack = { events += "back" },
                    onEditTrip = { events += "edit:$it" },
                    onAddStop = { _, insertBefore -> events += "addStop:${insertBefore ?: "end"}" },
                    onEditStop = { tripId2, stopId -> events += "editStop:$tripId2:$stopId" },
                    onOpenTripMap = { events += "map:$it" },
                    onOpenTrip = { events += "open:$it" },
                    viewModel = viewModel,
                )
            }
        }
    }

    @Test
    fun `shows trip header stops expenses and cost breakdown`() {
        seed()
        setContent()
        compose.onNodeWithText("Jura").assertIsDisplayed()
        compose.onNodeWithText("Rainy but great").assertIsDisplayed()
        compose.onNodeWithText("Camp A").assertIsDisplayed()
        compose.onNodeWithText("Vignette").assertIsDisplayed()
        compose.onNodeWithText("Total").assertIsDisplayed()
        compose.onNodeWithText("2 nights").assertIsDisplayed()
        // Camping 40 + road tax 30 + other 5
        compose.onNodeWithText("Camping").assertIsDisplayed()
        compose.onNodeWithText("Road tax").assertIsDisplayed()
        // The OTHER expense has no label -> falls back to type name; "(estimate)" tag shown.
        compose.onNodeWithText("  (estimate)").assertIsDisplayed()
        // Done chip in the top bar.
        compose.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test
    fun `empty trip prompts for stops and expenses`() {
        runBlocking {
            tripRepository.upsertTrip(
                Trip(id = "t1", name = "Empty", startDate = LocalDate.of(2026, 7, 1), status = TripStatus.DONE),
            )
        }
        setContent()
        compose.onNodeWithText("No stops yet — add where you camped.").assertIsDisplayed()
        compose.onNodeWithText("No expenses yet.").assertIsDisplayed()
    }

    @Test
    fun `unknown trip renders nothing but the scaffold`() {
        setContent("nope")
        compose.onNodeWithText("Stops").assertDoesNotExist()
    }

    @Test
    fun `mini map appears only when a stop has a location`() {
        seed(withLocations = false)
        setContent()
        compose.onNodeWithTag("map-placeholder").assertDoesNotExist()
    }

    @Test
    fun `tapping the mini map opens the fullscreen trip map`() {
        seed(withLocations = true)
        setContent()
        compose.onNodeWithTag("map-placeholder").assertExists()
        compose.onNodeWithTag("map-placeholder").performClick()
        assertEquals(listOf("map:t1"), events)
    }

    @Test
    fun `fuel estimate row and disclaimer show when no fuel is logged`() {
        runBlocking {
            seed(withLocations = true, withExpenses = false)
            tripRepository.upsertStop(
                Stop(id = "s2", tripId = "t1", name = "Camp B", location = LatLng(47.5, 7.5), orderIndex = 1),
            )
        }
        setContent()
        compose.onNodeWithText("Fuel (estimate)").assertIsDisplayed()
        compose.onNodeWithText(
            "Fuel is estimated by driving distance between stops — log a fuel expense to replace it.",
        ).assertIsDisplayed()
    }

    @Test
    fun `top bar actions navigate`() {
        seed()
        setContent()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Edit trip").performClick()
        assertEquals(listOf("back", "edit:t1"), events)
    }

    @Test
    fun `delete dialog cancel keeps the trip`() {
        seed()
        setContent()
        compose.onNodeWithContentDescription("Delete trip").performClick()
        compose.onNodeWithText("Delete trip?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete trip?").assertDoesNotExist()
        runBlocking { assertEquals(1, tripRepository.trips().first().size) }
    }

    @Test
    fun `delete dialog confirm deletes and navigates back`() {
        seed()
        setContent()
        compose.onNodeWithContentDescription("Delete trip").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("back"), events)
        runBlocking { assertTrue(tripRepository.trips().first().isEmpty()) }
    }

    @Test
    fun `fab menu adds a stop`() {
        seed()
        setContent()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Stop", useUnmergedTree = true).performClick()
        assertEquals(listOf("addStop:end"), events)
    }

    @Test
    fun `fab menu toggles closed again`() {
        seed()
        setContent()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithContentDescription("Close add menu").performClick()
        compose.onNodeWithContentDescription("Add").assertIsDisplayed()
    }

    @Test
    fun `fab menu opens the new expense sheet`() {
        seed()
        setContent()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Expense", useUnmergedTree = true).performClick()
        compose.onNodeWithText("New expense").assertIsDisplayed()
    }

    @Test
    fun `tapping a stop opens its editor`() {
        seed()
        setContent()
        compose.onNodeWithText("Camp A").performClick()
        assertEquals(listOf("editStop:t1:s1"), events)
    }

    @Test
    fun `tapping an expense opens the edit sheet`() {
        seed()
        setContent()
        compose.onNodeWithText("Vignette").performClick()
        compose.onNodeWithText("Edit expense").assertIsDisplayed()
    }

    @Test
    fun `expense delete icon removes the expense`() {
        seed()
        setContent()
        compose.onAllNodesWithContentDescription("Delete expense")[0].performClick()
        runBlocking {
            assertEquals(listOf("e2"), tripRepository.expenses("t1").first().map { it.id })
        }
    }

    @Test
    fun `saving an expense from the sheet stores it on the trip`() {
        seed()
        setContent()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Expense", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Amount").performClick()
        compose.onNodeWithText("Amount").performTextInput("42.50")
        compose.onNodeWithText("Save").performClick()
        runBlocking {
            val saved = tripRepository.expenses("t1").first().single { it.amount == 42.5 }
            assertEquals("t1", saved.tripId)
        }
    }

    // --- planned tours ---------------------------------------------------------------

    @Test
    fun `a planned tour shows the estimate card with the camping split`() {
        seedPlan()
        setContent("p1")
        compose.onNodeWithText("Estimated total").assertIsDisplayed()
        // Known 186 + estimated 2×45, plus fuel from the route.
        compose.onNodeWithText("CHF186.00 + ≈ CHF90.00").assertIsDisplayed()
        compose.onNodeWithText("Planned").assertIsDisplayed()
    }

    @Test
    fun `an unpriced planned stop shows its default-rate price`() {
        seedPlan()
        setContent("p1")
        // 2 nights × CHF 45 default: once in the camping split, once on the stop row.
        compose.onAllNodes(hasText("≈ CHF90.00", substring = true)).assertCountEquals(2)
    }

    @Test
    fun `a plan with only default-rate stops shows a single estimated camping value`() {
        runBlocking {
            tripRepository.upsertTrip(
                Trip(id = "p2", name = "Rough idea", startDate = LocalDate.of(2027, 7, 1), status = TripStatus.PLANNED),
            )
            tripRepository.upsertStop(Stop(id = "x1", tripId = "p2", name = "Somewhere", nights = 2, costKnown = false))
        }
        setContent("p2")
        // Total row, camping row, and bottom bar all read the same estimated 2×45.
        compose.onAllNodes(hasText("≈ CHF90.00")).assertCountEquals(3)
    }

    @Test
    fun `a fully priced plan shows known camping and other rows without the approx sign`() {
        runBlocking {
            tripRepository.upsertTrip(
                Trip(id = "p3", name = "Priced", startDate = LocalDate.of(2027, 7, 1), status = TripStatus.PLANNED),
            )
            tripRepository.upsertStop(
                Stop(id = "x1", tripId = "p3", name = "Booked", nights = 2, campingCostTotal = 100.0),
            )
            tripRepository.upsertExpense(
                Expense(id = "o1", tripId = "p3", type = ExpenseType.OTHER, amount = 12.0, label = "Museum"),
            )
        }
        setContent("p3")
        compose.onNodeWithText("CHF100.00").assertIsDisplayed() // known camping, no ≈
        compose.onNodeWithText("Other").assertIsDisplayed()
        compose.onAllNodes(hasText("CHF12.00")).assertCountEquals(2) // other row + expense list
        compose.onAllNodes(hasText("CHF112.00")).assertCountEquals(2) // total row + bottom bar
    }

    @Test
    fun `a vignette chip adds the road tax estimate`() {
        seedPlan()
        setContent("p1")
        compose.onNode(hasText("+ CH annual vignette", substring = true)).performClick()
        runBlocking {
            val expense = tripRepository.expenses("p1").first().single()
            assertEquals(ExpenseType.ROAD_TAX, expense.type)
            assertTrue(expense.isEstimate)
        }
    }

    @Test
    fun `long-press dragging a planned stop past its neighbour reorders the timeline`() {
        seedPlan()
        setContent("p1")
        compose.onNodeWithTag("timeline").performScrollToNode(hasTestTag("row-s1"))
        val row = compose.onNodeWithTag("row-s1")
        val height = row.fetchSemanticsNode().size.height.toFloat()
        // Manual clock: the long press has to time out before the drag is delivered.
        compose.mainClock.autoAdvance = false
        row.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        row.performTouchInput { moveBy(Offset(0f, height * 1.5f)) }
        compose.mainClock.advanceTimeBy(100)
        row.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        runBlocking {
            assertEquals(listOf("s2", "s1"), tripRepository.stops("p1").first().map { it.id })
        }
    }

    @Test
    fun `unplanned nights show as a row that can be resized and dropped`() {
        seedPlan()
        // s1 leaves on the 12th; push s2 out to the 14th, leaving two nights spare.
        runBlocking {
            val s2 = tripRepository.stops("p1").first().first { it.id == "s2" }
            tripRepository.upsertStop(s2.copy(arrivalDate = LocalDate.of(2027, 6, 14)))
        }
        setContent("p1")
        // One row past the gap, so its buttons sit clear of the floating action button.
        compose.onNodeWithTag("timeline").performScrollToNode(hasTestTag("row-s2"))
        compose.onNodeWithText("Nothing planned").assertIsDisplayed()

        compose.onNodeWithContentDescription("One unplanned night more").performClick()
        assertEquals(LocalDate.of(2027, 6, 15), arrivalOf("s2"))
        compose.onNodeWithContentDescription("One unplanned night less").performClick()
        assertEquals(LocalDate.of(2027, 6, 14), arrivalOf("s2"))

        compose.onNodeWithContentDescription("Remove the unplanned nights").performClick()
        assertEquals(LocalDate.of(2027, 6, 12), arrivalOf("s2")) // straight after s1's stay
        compose.onAllNodes(hasText("Nothing planned")).assertCountEquals(0)
    }

    @Test
    fun `the slot between two rows adds a stop there`() {
        seedPlan()
        setContent("p1")
        compose.onNodeWithTag("timeline").performScrollToNode(hasTestTag("insert-s2"))
        // Nothing goes in front of the first stop — the plan starts there.
        compose.onNodeWithTag("insert-s1").assertDoesNotExist()
        // It sits between the two rows, not on top of the one it belongs to.
        val slot = compose.onNodeWithTag("insert-s2").fetchSemanticsNode().boundsInRoot
        val stop = compose.onNodeWithTag("row-s2").fetchSemanticsNode().boundsInRoot
        assertTrue("$slot overlaps $stop", slot.bottom <= stop.top)

        compose.onNodeWithContentDescription("Insert here").performClick()
        compose.onNodeWithText("Stop").performClick()
        assertEquals(listOf("addStop:s2"), events)
    }

    @Test
    fun `a slot can be filled with nothing planned, moving the rest back`() {
        seedPlan()
        setContent("p1")
        compose.onNodeWithTag("timeline").performScrollToNode(hasTestTag("insert-s2"))
        compose.onNodeWithContentDescription("Insert here").performClick()
        compose.onNodeWithText("Nothing planned").performClick()
        assertEquals(LocalDate.of(2027, 6, 13), arrivalOf("s2"))
        // The night is now a row of its own, in front of the stop it pushed back.
        compose.onNodeWithTag("timeline").performScrollToNode(hasTestTag("row-gap-s2"))
        compose.onNodeWithText("Nothing planned").assertIsDisplayed()
    }

    @Test
    fun `nothing can be inserted in front of what has already happened`() {
        seedActive()
        setContent("a1")
        compose.onNodeWithTag("timeline").performScrollToNode(hasTestTag("insert-up"))
        compose.onNodeWithTag("insert-done").assertDoesNotExist()
        compose.onNodeWithTag("insert-cur").assertDoesNotExist()
    }

    @Test
    fun `start tour keeps the plan and opens the new trip`() {
        seedPlan()
        setContent("p1")
        compose.onNodeWithText("Start tour").performClick()
        compose.onNodeWithText("Keep plan as template").assertIsDisplayed()
        compose.onNodeWithText("Start").performClick()
        assertEquals(1, events.size)
        assertTrue(events[0].startsWith("open:"))
        assertTrue(events[0] != "open:p1")
        runBlocking {
            assertEquals(TripStatus.PLANNED, tripRepository.trip("p1").first()!!.status)
            assertEquals(2, tripRepository.trips().first().size)
        }
    }

    // --- active trips ----------------------------------------------------------------

    @Test
    fun `an active trip leads with tonight's stop`() {
        seedActive()
        setContent("a1")
        compose.onNodeWithText("TONIGHT").assertIsDisplayed()
        compose.onNodeWithText("Camp Current").assertIsDisplayed()
        compose.onNodeWithText("Projected total").assertIsDisplayed()
        compose.onNodeWithText("On the road").assertIsDisplayed()
    }

    @Test
    fun `the stepper extends the stay`() {
        seedActive()
        setContent("a1")
        compose.onNodeWithContentDescription("One night more").performClick()
        runBlocking {
            assertEquals(4, tripRepository.stops("a1").first().single { it.id == "cur" }.nights)
        }
    }

    @Test
    fun `arrived opens the price prompt and saves the real price`() {
        seedActive()
        setContent("a1")
        compose.onNodeWithText("✓ Arrived").performClick()
        compose.onNodeWithText("Arrived at Camp Current").assertIsDisplayed()
        compose.onNodeWithText("Price for the stay").performTextInput("120")
        compose.onNodeWithText("Save price").performClick()
        runBlocking {
            val stop = tripRepository.stops("a1").first().single { it.id == "cur" }
            assertEquals(StopState.DONE, stop.state)
            assertEquals(120.0, stop.campingCostTotal, 1e-9)
            assertTrue(stop.costKnown)
        }
    }

    @Test
    fun `arrived price prompt can be postponed`() {
        seedActive()
        setContent("a1")
        compose.onNodeWithText("✓ Arrived").performClick()
        compose.onNodeWithText("Later").performClick()
        compose.onNodeWithText("Arrived at Camp Current").assertDoesNotExist()
        runBlocking {
            val stop = tripRepository.stops("a1").first().single { it.id == "cur" }
            assertEquals(StopState.DONE, stop.state)
            assertTrue(!stop.costKnown)
        }
    }

    @Test
    fun `the arrival prompt drops the estimate hint once the price is known`() {
        runBlocking {
            seedActive()
            val cur = tripRepository.stops("a1").first().single { it.id == "cur" }
            tripRepository.upsertStop(cur.copy(costKnown = true, campingCostTotal = 90.0))
        }
        setContent("a1")
        compose.onNodeWithText("✓ Arrived").performClick()
        compose.onNodeWithText("Arrived at Camp Current").assertIsDisplayed()
        compose.onAllNodes(hasText("≈ CHF90.00 estimated")).assertCountEquals(0)
    }

    @Test
    fun `navigate hands the current stop to the maps app`() {
        seedActive()
        setContent("a1")
        compose.onNodeWithText("Navigate").performClick()
        // Fires a geo: intent — nothing to assert on the in-memory side; must not crash.
        compose.onNodeWithText("TONIGHT").assertIsDisplayed()
    }

    @Test
    fun `skipping an upcoming stop keeps it greyed with undo`() {
        seedActive()
        setContent("a1")
        compose.onNodeWithTag("timeline").performScrollToNode(hasContentDescription("Skip stop"))
        compose.onNodeWithContentDescription("Skip stop").performClick()
        runBlocking {
            assertEquals(StopState.SKIPPED, tripRepository.stops("a1").first().single { it.id == "up" }.state)
        }
        compose.onNodeWithText("Skipped Camp Later").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        runBlocking {
            assertEquals(StopState.PLANNED, tripRepository.stops("a1").first().single { it.id == "up" }.state)
        }
    }

    @Test
    fun `a skipped stop can be restored from its row`() {
        runBlocking {
            seedActive()
            val up = tripRepository.stops("a1").first().single { it.id == "up" }
            tripRepository.upsertStop(up.copy(state = StopState.SKIPPED))
        }
        setContent("a1")
        compose.onNodeWithTag("timeline").performScrollToNode(hasContentDescription("Restore stop"))
        compose.onNodeWithText("skipped").assertIsDisplayed()
        compose.onNodeWithContentDescription("Restore stop").performClick()
        runBlocking {
            assertEquals(StopState.PLANNED, tripRepository.stops("a1").first().single { it.id == "up" }.state)
        }
    }

    @Test
    fun `skipping tonight's stop moves on to the next`() {
        seedActive()
        setContent("a1")
        compose.onNodeWithText("Skip").performClick()
        runBlocking {
            assertEquals(StopState.SKIPPED, tripRepository.stops("a1").first().single { it.id == "cur" }.state)
        }
        compose.onNodeWithText("Camp Later").assertIsDisplayed()
    }

    @Test
    fun `a current visit reads next instead of tonight`() {
        runBlocking {
            seedActive()
            val cur = tripRepository.stops("a1").first().single { it.id == "cur" }
            tripRepository.upsertStop(cur.copy(kind = StopKind.VISIT, nights = 0))
        }
        setContent("a1")
        compose.onNodeWithText("NEXT").assertIsDisplayed()
        compose.onNodeWithContentDescription("One night more").assertDoesNotExist()
    }

    @Test
    fun `finish trip stamps the trip done`() {
        seedActive()
        setContent("a1")
        compose.onNodeWithText("Finish trip").performClick()
        runBlocking {
            assertEquals(TripStatus.DONE, tripRepository.trip("a1").first()!!.status)
        }
    }

    // --- done trips ------------------------------------------------------------------

    @Test
    fun `a done trip started from a plan shows the planned-vs-actual line`() {
        runBlocking {
            tripRepository.upsertTrip(
                Trip(
                    id = "t1", name = "Jura", startDate = LocalDate.of(2026, 7, 1),
                    endDate = LocalDate.of(2026, 7, 4), status = TripStatus.DONE,
                    plannedCost = 357.18, plannedNights = 6,
                ),
            )
        }
        setContent()
        compose.onNodeWithText("Planned: ≈ CHF357.18 · 6 nights").assertIsDisplayed()
    }

    @Test
    fun `plan again copies a done trip into a new plan`() {
        seed()
        setContent()
        compose.onNodeWithContentDescription("Plan again").performClick()
        assertEquals(1, events.size)
        assertTrue(events[0].startsWith("open:"))
        runBlocking { assertEquals(2, tripRepository.trips().first().size) }
    }

    // --- drive lines -----------------------------------------------------------------

    @Test
    fun `a stop whose leg still matches shows the drive arriving there`() {
        seedPlan()
        runBlocking {
            val s2 = tripRepository.stops("p1").first().first { it.id == "s2" }
            tripRepository.upsertStop(
                s2.copy(
                    leg = StopLeg(
                        from = LatLng(47.05, 8.31), to = LatLng(46.16, 8.79),
                        distanceMeters = 124_000, durationSeconds = 6_300,
                    ),
                ),
            )
        }
        setContent("p1")
        compose.onNodeWithTag("timeline").performScrollToNode(hasText("124 km \u00b7 1 h 45 min"))
        compose.onNodeWithText("124 km \u00b7 1 h 45 min", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("car", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a stop reached by cable car shows the ride with its icon`() {
        seedPlan()
        runBlocking {
            val s2 = tripRepository.stops("p1").first().first { it.id == "s2" }
            tripRepository.upsertStop(
                s2.copy(
                    leg = StopLeg(
                        from = LatLng(47.05, 8.31), to = LatLng(46.16, 8.79),
                        distanceMeters = 124_000, durationSeconds = 6_300,
                        rideAfter = TransitRide(modes = listOf(TravelMode.CABLE_CAR), durationSeconds = 1_440),
                    ),
                ),
            )
        }
        setContent("p1")
        compose.onNodeWithTag("timeline").performScrollToNode(hasText("24 min"))
        compose.onNodeWithText("24 min", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("cable car", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a stop with no leg shows no drive line`() {
        seedPlan()
        setContent("p1")
        compose.onNodeWithTag("timeline").performScrollToNode(hasText("Camping Delta"))
        compose.onAllNodesWithContentDescription("car", useUnmergedTree = true).assertCountEquals(0)
    }
    @Test
    fun `a stop shows how high it sits`() {
        seedPlan()
        runBlocking {
            val s2 = tripRepository.stops("p1").first().first { it.id == "s2" }
            tripRepository.upsertStop(
                s2.copy(elevation = StopElevation(LatLng(46.16, 8.79), 1469)),
            )
        }
        setContent("p1")
        compose.onNodeWithTag("timeline").performScrollToNode(hasText("Camping Delta"))
        compose.onNodeWithText("1469 m", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a height measured somewhere else is not shown`() {
        seedPlan()
        runBlocking {
            val s2 = tripRepository.stops("p1").first().first { it.id == "s2" }
            // Pin moved since the height was measured.
            tripRepository.upsertStop(
                s2.copy(elevation = StopElevation(LatLng(46.99, 8.01), 1469)),
            )
        }
        setContent("p1")
        compose.onNodeWithTag("timeline").performScrollToNode(hasText("Camping Delta"))
        compose.onAllNodesWithText("1469 m", substring = true).assertCountEquals(0)
    }

    @Test
    fun `each kind of spot gets its own icon beside the name`() {
        runBlocking {
            tripRepository.upsertTrip(
                Trip(id = "k1", name = "Kinds", startDate = LocalDate.of(2027, 6, 10), status = TripStatus.PLANNED),
            )
            StopKind.entries.forEachIndexed { index, kind ->
                tripRepository.upsertStop(
                    Stop(
                        id = "k-$kind", tripId = "k1", name = kind.name, orderIndex = index,
                        kind = kind, nights = if (kind == StopKind.VISIT) 0 else 1,
                        location = LatLng(47.0 + index, 8.0),
                        arrivalDate = LocalDate.of(2027, 6, 10 + index),
                    ),
                )
            }
        }
        setContent("k1")
        StopKind.entries.forEach { kind ->
            compose.onNodeWithTag("timeline").performScrollToNode(hasText(kind.name))
            compose.onAllNodesWithContentDescription(kind.displayName).onFirst().assertExists()
        }
    }

    @Test
    fun `the height carries an icon that says what it is`() {
        seedPlan()
        runBlocking {
            val s2 = tripRepository.stops("p1").first().first { it.id == "s2" }
            tripRepository.upsertStop(s2.copy(elevation = StopElevation(LatLng(46.16, 8.79), 1469)))
        }
        setContent("p1")
        compose.onNodeWithTag("timeline").performScrollToNode(hasText("Camping Delta"))
        compose.onNodeWithContentDescription("Height above sea level", useUnmergedTree = true)
            .assertExists()
    }

    // --- distance from here ------------------------------------------------------------

    private fun grantLocation() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun locatingViewModel(fix: LatLng?, leg: RoutedLeg?) = TripDetailViewModel(
        SavedStateHandle(mapOf("tripId" to "a1")),
        tripRepository,
        settingsRepository,
        currentLocation = { fix },
        drive = { _, _ -> leg },
    )

    @Test
    fun `tonight's stop shows how far it is from where you are`() {
        seedActive()
        grantLocation()
        setContent("a1", locatingViewModel(LatLng(46.9, 8.5), RoutedLeg("", 47_000, 3_300)))
        compose.onNodeWithTag("timeline").performScrollToNode(hasText("Camp Current"))
        compose.onNodeWithText("47 km · 55 min from here", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `without the location permission it offers to find out`() {
        seedActive()
        setContent("a1", locatingViewModel(LatLng(46.9, 8.5), RoutedLeg("", 47_000, 3_300)))
        compose.onNodeWithTag("timeline").performScrollToNode(hasText("Camp Current"))
        compose.onNodeWithText("Show distance from here", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("from here", substring = true).assertCountEquals(1)
    }

    @Test
    fun `with permission but no fix the planned drive stays`() {
        seedActive()
        grantLocation()
        runBlocking {
            val cur = tripRepository.stops("a1").first().first { it.id == "cur" }
            tripRepository.upsertStop(
                cur.copy(
                    leg = StopLeg(
                        from = LatLng(46.9, 8.5), to = LatLng(46.16, 8.79),
                        distanceMeters = 124_000, durationSeconds = 6_300,
                    ),
                ),
            )
        }
        setContent("a1", locatingViewModel(fix = null, leg = null))
        compose.onNodeWithTag("timeline").performScrollToNode(hasText("Camp Current"))
        compose.onAllNodesWithText("from here", substring = true).assertCountEquals(0)
    }

    /** A registry that answers a permission request immediately, without a system dialog. */
    @Suppress("UNCHECKED_CAST")
    private fun answeringRegistry(granted: Boolean) = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            dispatchResult(requestCode, granted as O)
        }
    }

    @Test
    fun `granting the permission fills the distance in`() {
        seedActive()
        setContent(
            "a1",
            locatingViewModel(LatLng(46.9, 8.5), RoutedLeg("", 47_000, 3_300)),
            permissionAnswer = true,
        )
        compose.onNodeWithTag("timeline").performScrollToNode(hasText("Camp Current"))
        compose.onNodeWithText("Show distance from here", useUnmergedTree = true).performClick()
        compose.onNodeWithText("47 km · 55 min from here", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `refusing the permission leaves the offer standing`() {
        seedActive()
        setContent(
            "a1",
            locatingViewModel(LatLng(46.9, 8.5), RoutedLeg("", 47_000, 3_300)),
            permissionAnswer = false,
        )
        compose.onNodeWithTag("timeline").performScrollToNode(hasText("Camp Current"))
        compose.onNodeWithText("Show distance from here", useUnmergedTree = true).performClick()
        compose.onAllNodesWithText("47 km", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the drive line says when you get there`() {
        seedActive()
        grantLocation()
        setContent("a1", locatingViewModel(LatLng(46.9, 8.5), RoutedLeg("", 47_000, 3_300)))
        compose.onNodeWithTag("timeline").performScrollToNode(hasText("Camp Current"))
        val expected = formatDriveFromHere(47_000, 3_300, LocalDateTime.now())
        compose.onNodeWithText(expected, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `checking in stops showing how far away it is`() {
        seedActive()
        grantLocation()
        runBlocking {
            // Tonight's stop, arrived at: CurrentStop keeps it current through the stay.
            val cur = tripRepository.stops("a1").first().first { it.id == "cur" }
            tripRepository.upsertStop(cur.copy(state = StopState.DONE, arrivalDate = LocalDate.now()))
        }
        setContent("a1", locatingViewModel(LatLng(46.9, 8.5), RoutedLeg("", 47_000, 3_300)))
        compose.onNodeWithTag("timeline").performScrollToNode(hasText("Camp Current"))
        compose.onNodeWithText("Checked in", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("from here", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("arrive", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a checked-in stop is not even offered the distance`() {
        seedActive()
        runBlocking {
            val cur = tripRepository.stops("a1").first().first { it.id == "cur" }
            tripRepository.upsertStop(cur.copy(state = StopState.DONE, arrivalDate = LocalDate.now()))
        }
        setContent("a1", locatingViewModel(LatLng(46.9, 8.5), RoutedLeg("", 47_000, 3_300)))
        compose.onNodeWithTag("timeline").performScrollToNode(hasText("Camp Current"))
        compose.onAllNodesWithText("Show distance from here").assertCountEquals(0)
    }

    @Test
    fun `the home stop reads as the start, with a home icon`() {
        runBlocking {
            tripRepository.upsertTrip(
                Trip(id = "h1", name = "Plan", startDate = LocalDate.of(2027, 6, 10), status = TripStatus.PLANNED),
            )
            tripRepository.upsertStop(
                Stop(
                    id = "home", tripId = "h1", name = "Luzern", orderIndex = 0, nights = 0,
                    kind = StopKind.HOME, location = LatLng(47.05, 8.31),
                    arrivalDate = LocalDate.of(2027, 6, 10),
                ),
            )
        }
        setContent("h1")
        compose.onNodeWithTag("timeline").performScrollToNode(hasText("Luzern"))
        compose.onAllNodesWithContentDescription("Home").onFirst().assertExists()
        // The row's own line: the start date and nothing about nights or a price.
        compose.onNodeWithText(
            "${formatDate(LocalDate.of(2027, 6, 10))} · start",
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }


    @Test
    fun `the home at the end of a plan reads as the way back`() {
        val start = LocalDate.of(2027, 6, 10)
        runBlocking {
            tripRepository.upsertTrip(Trip(id = "h2", name = "Loop", startDate = start, status = TripStatus.PLANNED))
            tripRepository.upsertStop(
                Stop(
                    id = "out", tripId = "h2", name = "Luzern", orderIndex = 0, nights = 0,
                    kind = StopKind.HOME, location = LatLng(47.05, 8.31), arrivalDate = start,
                ),
            )
            tripRepository.upsertStop(Stop(id = "s", tripId = "h2", name = "Locarno", orderIndex = 1, nights = 2, arrivalDate = start))
            tripRepository.upsertStop(
                Stop(
                    id = "back", tripId = "h2", name = "Luzern", orderIndex = 2, nights = 0,
                    kind = StopKind.HOME, location = LatLng(47.05, 8.31), arrivalDate = start.plusDays(2),
                ),
            )
        }
        setContent("h2")
        val back = "${formatDate(start.plusDays(2))} · home again"
        compose.onNodeWithTag("timeline").performScrollToNode(hasText(back))
        compose.onNodeWithText(back, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("${formatDate(start)} · start", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a tour started ahead of its date heads for the first stop, not home`() {
        val start = LocalDate.now().plusDays(1)
        runBlocking {
            tripRepository.upsertTrip(Trip(id = "h3", name = "Loop", startDate = start, status = TripStatus.ACTIVE))
            tripRepository.upsertStop(
                Stop(
                    id = "out", tripId = "h3", name = "Luzern", orderIndex = 0, nights = 0, state = StopState.DONE,
                    kind = StopKind.HOME, location = LatLng(47.05, 8.31), arrivalDate = start,
                ),
            )
            tripRepository.upsertStop(
                Stop(
                    id = "s", tripId = "h3", name = "Locarno", orderIndex = 1, nights = 2,
                    location = LatLng(46.16, 8.79), arrivalDate = start,
                ),
            )
        }
        setContent("h3")
        compose.onNodeWithTag("timeline").performScrollToNode(hasText("Locarno"))
        compose.onNodeWithText("TONIGHT").assertIsDisplayed()
        compose.onNodeWithText("Navigate").assertIsDisplayed()
        compose.onNodeWithText("Show distance from here", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("Checked in", useUnmergedTree = true).assertCountEquals(0)
    }
}
