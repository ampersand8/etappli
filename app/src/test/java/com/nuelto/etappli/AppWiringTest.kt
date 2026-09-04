package com.nuelto.etappli

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.etappli.data.AuthUser
import com.nuelto.etappli.data.InMemorySettingsRepository
import com.nuelto.etappli.data.InMemoryTripRepository
import com.nuelto.etappli.data.TripRepository
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopState
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.domain.SharedPlace
import com.nuelto.etappli.testutil.FakeAuthRepository
import com.nuelto.etappli.testutil.TestCamperApp
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class AppContainerTest {

    @Test
    fun `in-memory container has no auth and local repositories`() {
        val container = AppContainer.inMemory()
        assertFalse(container.firebaseEnabled)
        assertTrue(container.tripRepository is InMemoryTripRepository)
        assertTrue(container.settingsRepository is InMemorySettingsRepository)
    }

    @Test
    fun `an auth repository marks the container as firebase-backed`() {
        val container = AppContainer(
            FakeAuthRepository(),
            InMemoryTripRepository(seed = false),
            InMemorySettingsRepository(),
        )
        assertTrue(container.firebaseEnabled)
    }

    @Test
    fun `the in-memory container starts with an empty intake and no link resolver`() = runTest {
        val container = AppContainer.inMemory()
        assertNull(container.shareIntake.pending.value)
        assertNull(container.expandShareLink("https://maps.app.goo.gl/x1"))
    }

    @Test
    fun `application initializes its container on create`() {
        val app = ApplicationProvider.getApplicationContext<CamperApp>()
        assertFalse(app.container.firebaseEnabled)
        assertTrue(app.container.tripRepository is InMemoryTripRepository)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class AppRootTest {

    @get:Rule
    val compose = createComposeRule()

    private fun firebaseContainerWith(auth: FakeAuthRepository) =
        AppContainer(auth, InMemoryTripRepository(seed = false), InMemorySettingsRepository())

    @Test
    fun `local mode goes straight to the trip list`() {
        compose.setContent { AppRoot(AppContainer.inMemory()) }
        compose.onNodeWithText("Trips").assertIsDisplayed()
    }

    @Test
    fun `firebase mode without a user shows sign-in`() {
        compose.setContent { AppRoot(firebaseContainerWith(FakeAuthRepository())) }
        compose.onNodeWithText("Sign in with Google").assertIsDisplayed()
    }

    @Test
    fun `firebase mode with a signed-in user shows the app`() {
        compose.setContent { AppRoot(firebaseContainerWith(FakeAuthRepository(AuthUser("u1")))) }
        compose.onNodeWithText("Trips").assertIsDisplayed()
    }

    @Test
    fun `a share arriving while signed out waits for the sign-in`() {
        val auth = FakeAuthRepository()
        val container = firebaseContainerWith(auth)
        container.shareIntake.offer(SharedPlace("Camping Grimselblick", LatLng(46.5601, 8.332)))
        compose.setContent { AppRoot(container) }
        compose.onNodeWithText("Sign in with Google").assertIsDisplayed()

        auth.state.value = AuthUser("u1")
        compose.onNodeWithText("Add to a trip").assertIsDisplayed()
    }

    @Test
    fun `signing out drops back to the sign-in screen`() {
        val auth = FakeAuthRepository(AuthUser("u1"))
        compose.setContent { AppRoot(firebaseContainerWith(auth)) }
        compose.onNodeWithText("Trips").assertIsDisplayed()
        auth.signOut()
        compose.onNodeWithText("Sign in with Google").assertIsDisplayed()
    }

    // --- tracking trigger ----------------------------------------------------------------

    private val today = LocalDate.now()

    /** A trip of [status] started on [start], its first stop in [first]. */
    private fun tour(
        status: TripStatus = TripStatus.ACTIVE,
        start: LocalDate = today,
        first: StopState = StopState.PLANNED,
    ) = InMemoryTripRepository(seed = false).apply {
        runBlocking {
            upsertTrip(Trip(id = "t1", name = "Tour", startDate = start, status = status))
            upsertStop(
                Stop(
                    id = "s1", tripId = "t1", name = "A", nights = 2, orderIndex = 0,
                    location = LatLng(46.0, 8.0), arrivalDate = start, state = first,
                ),
            )
            upsertStop(
                Stop(
                    id = "s2", tripId = "t1", name = "B", nights = 1, orderIndex = 1,
                    location = LatLng(46.5, 8.5), arrivalDate = start.plusDays(2),
                ),
            )
        }
    }

    private fun assertStarts(expected: Int, repository: TripRepository, auth: FakeAuthRepository? = null) {
        var starts = 0
        compose.setContent {
            AppRoot(AppContainer(auth, repository, InMemorySettingsRepository(), startTracking = { starts++ }))
        }
        compose.waitForIdle()
        assertEquals(expected, starts)
    }

    @Test
    fun `an open app with a drive underway starts tracking`() = assertStarts(1, tour())

    @Test
    fun `signed in, firebase mode starts tracking too`() =
        assertStarts(1, tour(), FakeAuthRepository(AuthUser("u1")))

    @Test
    fun `mid-stay nothing starts`() = assertStarts(0, tour(first = StopState.DONE))

    @Test
    fun `before the start date nothing starts`() = assertStarts(0, tour(start = today.plusDays(1)))

    @Test
    fun `without an active trip nothing starts`() = assertStarts(0, tour(status = TripStatus.PLANNED))

    @Test
    fun `a plan that cannot be read starts nothing`() {
        // What the Firestore repositories do without a user; the list screen never asks this.
        val unreadable = object : TripRepository by tour() {
            override fun stops(tripId: String): Flow<List<Stop>> = flow { throw IllegalStateException("no user") }
        }
        assertStarts(0, unreadable)
    }
}
