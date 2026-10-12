package com.example.timetable.ui.puremode

import com.example.timetable.testing.FakeSettingsRepository
import com.example.timetable.testing.FakeTimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PureModeViewModelTest {

    private val settings = FakeSettingsRepository()

    private val timeProvider = FakeTimeProvider()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PureModeViewModel(settings, timeProvider)

    @Test
    fun backPressIsIgnoredWhilePureModeIsOff() = runTest {
        val viewModel = viewModel()
        val received = mutableListOf<PureModeMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.messages.collect { received += it }
        }

        val shouldExit = viewModel.onBackPressed()

        assertFalse(shouldExit)
        assertFalse("纯净模式关闭时不得改变开关", viewModel.enabled.value)
        assertTrue("纯净模式关闭时不得发出任何消息", received.isEmpty())
    }

    @Test
    fun secondBackPressInsideWindowExitsAndWritesThrough() = runTest {
        val viewModel = viewModel()
        settings.setPureModeEnabled(true)
        val received = mutableListOf<PureModeMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.messages.collect { received += it }
        }
        assertTrue(viewModel.enabled.value)

        timeProvider.advance(1_000L)
        assertFalse("第一次返回只提示", viewModel.onBackPressed())
        assertEquals(listOf<PureModeMessage>(PureModeMessage.ConfirmExit), received)
        assertTrue(viewModel.enabled.value)

        timeProvider.advance(500L)
        assertTrue("窗口内第二次返回应退出", viewModel.onBackPressed())
        assertFalse("退出必须落 DataStore", viewModel.enabled.value)
        assertFalse(settings.current().pureModeEnabled)
    }

    @Test
    fun backPressAfterWindowOnlyRearms() = runTest {
        val viewModel = viewModel()
        settings.setPureModeEnabled(true)

        timeProvider.advance(1_000L)
        viewModel.onBackPressed()

        timeProvider.advance(DoubleBackDetector.EXIT_WINDOW_MILLIS + 1)
        assertFalse("超窗口的返回只重新计时", viewModel.onBackPressed())
        assertTrue(viewModel.enabled.value)

        timeProvider.advance(1_000L)
        assertTrue("重新计时后窗口内的第二次返回应退出", viewModel.onBackPressed())
    }

    @Test
    fun firstEntryHintIsSentExactlyOnce() = runTest {
        val viewModel = viewModel()
        val received = mutableListOf<PureModeMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.messages.collect { received += it }
        }

        settings.setPureModeEnabled(true)

        assertEquals(listOf<PureModeMessage>(PureModeMessage.HintFirstEntry), received)
        assertTrue("提示发出即置位，不依赖调用方回调", settings.current().pureModeHintShown)

        settings.setPureModeEnabled(false)
        settings.setPureModeEnabled(true)

        assertEquals(
            "第二次进入不得重复提示",
            1,
            received.count { it == PureModeMessage.HintFirstEntry },
        )
    }

    @Test
    fun hintIsNotSentWhenFlagWasAlreadyPersisted() = runTest {
        settings.update { it.copy(pureModeHintShown = true) }
        val viewModel = viewModel()
        val received = mutableListOf<PureModeMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.messages.collect { received += it }
        }

        settings.setPureModeEnabled(true)

        assertTrue("历史已提示过就不得再发", received.isEmpty())
    }
}
