package io.github.playfoundryhq.adaptiveflow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.playfoundryhq.adaptiveflow.ui.TutorialPillarItem
import io.github.playfoundryhq.adaptiveflow.ui.TutorialTab
import io.github.playfoundryhq.adaptiveflow.ui.theme.AdaptiveFlowTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi visual-regression suite. Reference PNGs are committed under
 * `src/test/screenshots/`. `./gradlew recordRoborazziDebug` regenerates them;
 * `./gradlew verifyRoborazziDebug` diffs against them with a generous threshold
 * (font hinting differs between machines). Plain `testDebugUnitTest` just renders
 * each composable — a no-op for the PNGs, but it still catches render crashes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.05f),
    )

    @Test
    fun tutorialTab() {
        composeRule.setContent {
            AdaptiveFlowTheme(darkTheme = false) { TutorialTab() }
        }
        composeRule.onRoot().captureRoboImage(roborazziOptions = options)
    }

    @Test
    fun tutorialTabDark() {
        composeRule.setContent {
            AdaptiveFlowTheme(darkTheme = true) { TutorialTab() }
        }
        composeRule.onRoot().captureRoboImage(roborazziOptions = options)
    }

    @Test
    fun pillarItem() {
        composeRule.setContent {
            AdaptiveFlowTheme(darkTheme = false) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    TutorialPillarItem(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = "Paste It, Study It",
                        desc = "Paste text or a word list, or load a PDF / TXT / CSV.",
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage(roborazziOptions = options)
    }
}
