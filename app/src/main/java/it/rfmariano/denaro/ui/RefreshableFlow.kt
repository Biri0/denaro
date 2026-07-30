package it.rfmariano.denaro.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> refreshableFlow(
    refreshRequests: Flow<Long>,
    loadingState: T,
    source: () -> Flow<T>,
): Flow<T> = refreshRequests.flatMapLatest {
    source().onStart { emit(loadingState) }
}
