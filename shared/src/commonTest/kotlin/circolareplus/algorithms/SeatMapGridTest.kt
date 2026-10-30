package circolareplus.algorithms

import circolareplus.ui.screens.deskGridCells
import circolareplus.ui.screens.deskIndexUnderPointer
import circolareplus.ui.screens.DeskBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SeatMapGridTest {
    @Test
    fun dropTargetsUseOnlyVisibleBoundsAndIgnoreReleasesOutsideTheMap() {
        val layout = listOf(DeskAssignment(0, 0, "a", "b"), DeskAssignment(0, 1, "c", "d"))
        val cells = deskGridCells(layout).second
        val bounds = mapOf(0 to DeskBounds(Offset(10f, 100f), IntSize(80, 100)),
            1 to DeskBounds(Offset(110f, 100f), IntSize(80, 100)))
        assertEquals(1, deskIndexUnderPointer(Offset(150f, 140f), bounds, listOf(1), cells, 0f))
        assertNull(deskIndexUnderPointer(Offset(30f, 140f), bounds, listOf(1), cells, 0f))
        assertNull(deskIndexUnderPointer(Offset(400f, 400f), bounds, listOf(0, 1), cells, 12f))
    }

    @Test
    fun keepsMiddleHoleAndRightDeskAtTheirPhysicalColumns() {
        val layout = listOf(DeskAssignment(0, 0, "a", "b"), DeskAssignment(0, 1, "c", "d"),
            DeskAssignment(0, 2, "e", "f"), DeskAssignment(3, 0, "g", "h"), DeskAssignment(3, 2, "i", "j"))
        val (columns, cells) = deskGridCells(layout)
        assertEquals(3, columns)
        assertEquals(12, cells.size)
        assertNull(cells[10])
        assertEquals(2, cells[11]?.value?.column)
        assertEquals("i", cells[11]?.value?.studentAId)
    }

    @Test
    fun resizeKeepsChosenHoleAndMixedDesks() {
        val slots = listOf(SeatMapOptimizer.DeskSlot(0, 0, 2), SeatMapOptimizer.DeskSlot(0, 1, 0),
            SeatMapOptimizer.DeskSlot(0, 2, 3))
        val grown = SeatMapOptimizer.resizeDeskSlots(slots, 2, 4, 2)
        assertEquals(0, grown.first { it.row == 0 && it.column == 1 }.seats)
        assertEquals(3, grown.first { it.row == 0 && it.column == 2 }.seats)
        assertEquals(0, grown.first { it.row == 0 && it.column == 3 }.seats)
        assertEquals(4, grown.count { it.row == 1 })
    }
}
