package com.foxnks.dockshift.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule that replaces `Dispatchers.Main` with a test dispatcher.
 *
 * `viewModelScope` runs on the Android main thread, which does not exist in local
 * JVM tests; without this rule every ViewModel coroutine would crash.
 *
 * @param testDispatcher the dispatcher used as Main during each test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        // Restore the real Main so tests never leak into each other
        Dispatchers.resetMain()
    }
}
