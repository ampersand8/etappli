package com.nuelto.camperexperience.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.camperexperience.testutil.FakeAuthRepository
import com.nuelto.camperexperience.testutil.TestCamperApp
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class SignInScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `failed sign-in shows the error message`() {
        val auth = FakeAuthRepository(signInResult = "Sign-in failed")
        compose.setContent { SignInScreen(auth) }
        compose.onNodeWithText("CamperExperience").assertIsDisplayed()
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("Sign-in failed").assertIsDisplayed()
        assertEquals(1, auth.signInCalls)
    }

    @Test
    fun `while signing in the button is replaced by a spinner`() {
        val auth = FakeAuthRepository()
        auth.gate = CompletableDeferred()
        compose.setContent { SignInScreen(auth) }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("Sign in with Google").assertDoesNotExist()
        auth.gate!!.complete(Unit)
        compose.onNodeWithText("Sign in with Google").assertIsDisplayed()
    }

    @Test
    fun `successful sign-in shows no error`() {
        val auth = FakeAuthRepository()
        compose.setContent { SignInScreen(auth) }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("Sign in with Google").assertIsDisplayed()
        assertEquals(1, auth.signInCalls)
    }
}
