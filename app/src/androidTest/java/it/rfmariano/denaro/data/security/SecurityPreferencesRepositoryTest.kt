package it.rfmariano.denaro.data.security

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityPreferencesRepositoryTest {
    private val context by lazy {
        object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("test_$name", mode)
        }
    }

    @Before
    fun setUp() {
        preferences().edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences().edit().clear().commit()
    }

    @Test
    fun secureDefaultsAreUsed() {
        val state = SecurityPreferencesRepository(context).state.value

        assertFalse(state.appLockEnabled)
        assertTrue(state.screenSecurityEnabled)
    }

    @Test
    fun securityChoicesPersist() {
        val repository = SecurityPreferencesRepository(context)
        repository.setAppLockEnabled(true)
        repository.setScreenSecurityEnabled(false)

        val restored = SecurityPreferencesRepository(context).state.value
        assertTrue(restored.appLockEnabled)
        assertFalse(restored.screenSecurityEnabled)
    }

    private fun preferences() = context.getSharedPreferences(
        SecurityPreferencesRepository.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
}
