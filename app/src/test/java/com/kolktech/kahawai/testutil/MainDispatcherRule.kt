package com.kolktech.kahawai.testutil

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/// Points [Dispatchers.Main] (used by `viewModelScope`, unavailable on
/// the JVM unit test classpath) at a [TestDispatcher] for the duration
/// of a test. Defaults to [UnconfinedTestDispatcher] so `viewModelScope.launch {}`
/// blocks run eagerly up to their first real suspension (e.g. a MockWebServer
/// round trip) instead of needing manual scheduler advancement — the
/// standard recipe for observing StateFlow emissions with Turbine.
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
