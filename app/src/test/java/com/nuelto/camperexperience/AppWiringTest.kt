package com.nuelto.camperexperience

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.camperexperience.data.AuthUser
import com.nuelto.camperexperience.data.InMemorySettingsRepository
import com.nuelto.camperexperience.data.InMemoryTripRepository
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.domain.SharedPlace
import com.nuelto.camperexperience.testutil.FakeAuthRepository
import com.nuelto.camperexperience.testutil.TestCamperApp
import kotlinx.coroutines.test.runTest
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
}
