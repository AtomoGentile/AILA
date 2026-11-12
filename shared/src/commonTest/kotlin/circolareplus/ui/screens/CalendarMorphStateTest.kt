package circolareplus.ui.screens

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class CalendarMorphStateTest {
    @Test fun restoresStableModeInsteadOfInterruptedDrag() {
        val state = CalendarMorphState(initialTimetable = true)
        state.beginDrag()
        state.drag(-0.4f)
        val saver = CalendarMorphState.saver
        val saved = with(saver) {
            object : androidx.compose.runtime.saveable.SaverScope {
                override fun canBeSaved(value: Any) = true
            }.save(state)
        }
        val restored = saver.restore(requireNotNull(saved))!!
        assertTrue(restored.timetable)
        assertEquals(1f, restored.progress)
    }

    @Test fun distanceAndVelocityBothDetermineDestination() {
        assertFalse(morphDestination(0.49f, 0f))
        assertTrue(morphDestination(0.5f, 0f))
        assertTrue(morphDestination(0.1f, 3f))
        assertFalse(morphDestination(0.9f, -3f))
        assertFalse(morphDestination(0.3f, -0.5f))
        assertTrue(morphDestination(0.8f, 0.5f))
    }

    @Test fun cancellationReturnsToStartingModeInBothDirections() = runTest {
        val state = CalendarMorphState()
        state.beginDrag()
        state.drag(0.8f)
        state.finish(this, 5f, true, cancelled = true)
        assertEquals(0f, state.progress)
        assertFalse(state.timetable)
        state.settle(this, true, true)
        state.beginDrag()
        state.drag(-0.8f)
        state.finish(this, -5f, true, cancelled = true)
        assertEquals(1f, state.progress)
        assertTrue(state.timetable)
    }

    @Test fun overscrollIsBoundedAndRepeatedGesturesRemainReversible() = runTest {
        val state = CalendarMorphState()
        repeat(20) {
            state.beginDrag()
            state.drag(3f)
            assertEquals(1f, state.progress)
            state.finish(this, 0f, true)
            assertTrue(state.timetable)
            state.beginDrag()
            state.drag(-3f)
            assertEquals(0f, state.progress)
            state.finish(this, 0f, true)
            assertFalse(state.timetable)
        }
    }
}
