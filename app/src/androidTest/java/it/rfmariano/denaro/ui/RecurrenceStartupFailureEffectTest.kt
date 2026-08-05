package it.rfmariano.denaro.ui

import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecurrenceStartupFailureEffectTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pendingFailureIsReportedOnlyOnceAfterCompositionRecreation() {
        val failurePending = mutableStateOf(true)
        val compositionGeneration = mutableIntStateOf(0)
        var consumedCount = 0
        var reportedCount = 0

        composeRule.setContent {
            key(compositionGeneration.intValue) {
                RecurrenceStartupFailureEffect(
                    failurePending = failurePending.value,
                    onFailureConsumed = {
                        consumedCount += 1
                        failurePending.value = false
                    },
                    showFailure = { reportedCount += 1 },
                )
            }
        }

        composeRule.waitUntil {
            consumedCount == 1 && reportedCount == 1
        }
        composeRule.runOnUiThread {
            compositionGeneration.intValue += 1
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(1, consumedCount)
            assertEquals(1, reportedCount)
        }
    }

    @Test
    fun absentFailureIsNotReported() {
        var consumedCount = 0
        var reportedCount = 0

        composeRule.setContent {
            RecurrenceStartupFailureEffect(
                failurePending = false,
                onFailureConsumed = { consumedCount += 1 },
                showFailure = { reportedCount += 1 },
            )
        }

        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(0, consumedCount)
            assertEquals(0, reportedCount)
        }
    }
}
