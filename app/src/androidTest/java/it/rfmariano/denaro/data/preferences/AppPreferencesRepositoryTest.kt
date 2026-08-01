package it.rfmariano.denaro.data.preferences

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
class AppPreferencesRepositoryTest {
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
    fun amountsAreVisibleByDefaultAndPersistWhenHidden() {
        val repository = AppPreferencesRepository(context)
        assertTrue(repository.state.value.amountsVisible)

        repository.setAmountsVisible(false)

        assertFalse(AppPreferencesRepository(context).state.value.amountsVisible)
    }

    private fun preferences() =
        context.getSharedPreferences("denaro_preferences", Context.MODE_PRIVATE)
}
