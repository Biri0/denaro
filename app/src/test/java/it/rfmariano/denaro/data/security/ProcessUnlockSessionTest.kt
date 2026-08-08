package it.rfmariano.denaro.data.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessUnlockSessionTest {
    @Test
    fun processStartsUnlockedWhenAppLockIsDisabled() {
        assertTrue(ProcessUnlockSession(appLockEnabledAtProcessStart = false).unlocked.value)
    }

    @Test
    fun processStartsLockedAndRemainsUnlockedAfterAuthentication() {
        val session = ProcessUnlockSession(appLockEnabledAtProcessStart = true)

        assertFalse(session.unlocked.value)
        session.unlock()
        assertTrue(session.unlocked.value)
    }

    @Test
    fun aNewProcessSessionLocksAgain() {
        val firstSession = ProcessUnlockSession(appLockEnabledAtProcessStart = true)
        firstSession.unlock()

        assertFalse(ProcessUnlockSession(appLockEnabledAtProcessStart = true).unlocked.value)
    }

    @Test
    fun returningBeforeBackgroundTimeoutRemainsUnlocked() {
        val session = unlockedSession()

        session.onAppBackgrounded(elapsedRealtimeMillis = 1_000L)
        session.onAppForegrounded(
            elapsedRealtimeMillis = 60_999L,
            appLockEnabled = true,
        )

        assertTrue(session.unlocked.value)
    }

    @Test
    fun returningAtBackgroundTimeoutLocks() {
        val session = unlockedSession()

        session.onAppBackgrounded(elapsedRealtimeMillis = 1_000L)
        session.onAppForegrounded(
            elapsedRealtimeMillis = 61_000L,
            appLockEnabled = true,
        )

        assertFalse(session.unlocked.value)
    }

    @Test
    fun returningAfterBackgroundTimeoutLocks() {
        val session = unlockedSession()

        session.onAppBackgrounded(elapsedRealtimeMillis = 1_000L)
        session.onAppForegrounded(
            elapsedRealtimeMillis = 61_001L,
            appLockEnabled = true,
        )

        assertFalse(session.unlocked.value)
    }

    @Test
    fun returningWhileAppLockIsDisabledRemainsUnlocked() {
        val session = unlockedSession()

        session.onAppBackgrounded(elapsedRealtimeMillis = 1_000L)
        session.onAppForegrounded(
            elapsedRealtimeMillis = 61_000L,
            appLockEnabled = false,
        )

        assertTrue(session.unlocked.value)
    }

    @Test
    fun eachBackgroundIntervalGetsItsOwnTimeout() {
        val session = unlockedSession()

        session.onAppBackgrounded(elapsedRealtimeMillis = 1_000L)
        session.onAppForegrounded(
            elapsedRealtimeMillis = 60_999L,
            appLockEnabled = true,
        )
        session.onAppBackgrounded(elapsedRealtimeMillis = 100_000L)
        session.onAppForegrounded(
            elapsedRealtimeMillis = 160_000L,
            appLockEnabled = true,
        )

        assertFalse(session.unlocked.value)
    }

    private fun unlockedSession() =
        ProcessUnlockSession(appLockEnabledAtProcessStart = true).apply { unlock() }
}
