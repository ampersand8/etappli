package com.nuelto.camperexperience

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.camperexperience.testutil.TestCamperApp
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * The share target end to end: an Intent arrives, and the chooser is on screen. The
 * manifest filter itself is only checked here — Robolectric cannot run the share sheet.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class ShareIntentTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val intake get() = (context as CamperApp).container.shareIntake

    private val mapsLink =
        "Camping Grimselblick\nhttps://www.google.com/maps/place/Grimselblick/data=!3d46.5601!4d8.332"

    private fun shareIntent(text: CharSequence) = Intent(Intent.ACTION_SEND)
        .setClass(context, MainActivity::class.java)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)

    @Test
    fun `a cold share opens the chooser and is not replayed`() {
        ActivityScenario.launch<MainActivity>(shareIntent(mapsLink)).use { scenario ->
            compose.onNodeWithText("Add to a trip").assertIsDisplayed()
            // The URL's own place segment names the stop; the share sheet's line is a fallback.
            compose.onNodeWithText("Grimselblick").assertIsDisplayed()
            // Consumed the moment the chooser was pushed; a recreate offers nothing new.
            assertNull(intake.pending.value)
            scenario.recreate()
            assertNull(intake.pending.value)
        }
    }

    @Test
    fun `a styled EXTRA_TEXT is read`() {
        ActivityScenario.launch<MainActivity>(shareIntent(SpannableString(mapsLink))).use {
            compose.onNodeWithText("Add to a trip").assertIsDisplayed()
        }
    }

    @Test
    fun `a geo link opens the chooser`() {
        val intent = Intent(Intent.ACTION_VIEW)
            .setClass(context, MainActivity::class.java)
            .setData(Uri.parse("geo:0,0?q=46.5601,8.332(Grimselpass)"))
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.onNodeWithText("Grimselpass").assertIsDisplayed()
        }
    }

    @Test
    fun `a share arriving while the app runs is handled too`() {
        // Through the framework's own onNewIntent, so the listener registration is what
        // is under test rather than the method it points at.
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        compose.onNodeWithText("Trips").assertIsDisplayed()
        controller.newIntent(shareIntent(mapsLink))
        compose.onNodeWithText("Add to a trip").assertIsDisplayed()
        controller.destroy()
    }

    @Test
    fun `a plain launch and an unreadable share open nothing`() {
        ActivityScenario.launch<MainActivity>(shareIntent("hello")).use { scenario ->
            compose.onNodeWithText("Trips").assertIsDisplayed()
            scenario.onActivity {
                assertNull(it.sharedPlace(Intent(Intent.ACTION_MAIN)))
                // A relaunch that shares nothing leaves whatever is on screen alone.
                it.onShared(Intent(Intent.ACTION_MAIN))
            }
            assertNull(intake.pending.value)
        }
    }

    @Test
    fun `the share filter is live in the manifest`() {
        val filter = Intent(Intent.ACTION_SEND).setType("text/plain")
        val activities = context.packageManager.queryIntentActivities(filter, 0)
        assertTrue(activities.any { it.activityInfo.name == MainActivity::class.java.name })
    }
}
