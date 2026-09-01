package com.nuelto.etappli.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.etappli.testutil.TestCamperApp
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class ThemeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun colorsFor(darkTheme: Boolean): Color {
        var primary = Color.Unspecified
        compose.setContent {
            CamperTheme(darkTheme = darkTheme) {
                primary = MaterialTheme.colorScheme.primary
                Text("themed")
            }
        }
        compose.onNodeWithText("themed").assertIsDisplayed()
        return primary
    }

    @Test
    fun `dynamic colors are used on modern android`() {
        assertNotEquals(Color.Unspecified, colorsFor(darkTheme = false))
    }

    @Test
    fun `dynamic dark colors are used on modern android`() {
        assertNotEquals(Color.Unspecified, colorsFor(darkTheme = true))
    }

    @Test
    @Config(sdk = [30])
    fun `static light palette is used before android 12`() {
        assertNotEquals(Color.Unspecified, colorsFor(darkTheme = false))
    }

    @Test
    @Config(sdk = [30])
    fun `static dark palette differs from light before android 12`() {
        assertNotEquals(Color.Unspecified, colorsFor(darkTheme = true))
    }
}
