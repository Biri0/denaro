package it.rfmariano.denaro.ui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshableFlowTest {
    @Test
    fun emitsLoadingBeforeEveryDataGeneration() = runBlocking {
        val refreshRequests = MutableStateFlow(0L)
        var generation = 0
        val states = Channel<String>(Channel.UNLIMITED)
        val collection = launch {
            refreshableFlow(
                refreshRequests = refreshRequests,
                loadingState = "loading",
                source = { flowOf("loaded-${++generation}") },
            ).collect(states::send)
        }

        assertEquals("loading", states.receive())
        assertEquals("loaded-1", states.receive())

        refreshRequests.value += 1

        assertEquals("loading", states.receive())
        assertEquals("loaded-2", states.receive())
        collection.cancel()
    }
}
