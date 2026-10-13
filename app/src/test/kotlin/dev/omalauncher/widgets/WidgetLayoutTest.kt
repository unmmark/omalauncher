package dev.omalauncher.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

fun widget(name: String, w: Int, h: Int, page: Int = 1) = WidgetItem(WidgetSpec(File(name), name, page, 0, null, size = w to h))

class WidgetLayoutTest {
    @Test fun wideGridPlacesAcrossBothHalves() {
        val prefs = FakePrefs()
        val layout = WidgetLayout(prefs, OPEN_COLUMNS)
        layout.place(listOf(widget("a.owl", 12, 2)), OPEN_COLUMNS, 10)
        assertEquals(Cell(0, 0, 12, 2), layout.cell("a.owl"))
        // Saved with its own column count, so reading it back doesn't rescale it to six.
        assertEquals(Cell(0, 0, 12, 2), WidgetLayout(prefs, OPEN_COLUMNS).cell("a.owl"))
    }

    @Test fun foldedLayoutStillRescalesOtherColumnCounts() {
        val prefs = FakePrefs()
        WidgetLayout(prefs, OPEN_COLUMNS).apply {
            set("a.owl", Cell(6, 0, 6, 2))
            save()
        }
        assertEquals(Cell(3, 0, 3, 2), WidgetLayout(prefs).cell("a.owl"))
    }

    @Test fun clearCellKeepsThePage() {
        val layout = WidgetLayout(FakePrefs(), OPEN_COLUMNS)
        layout.set("a.owl", Cell(0, 0, 2, 2))
        layout.setPage("a.owl", 3)
        layout.clearCell("a.owl")
        assertNull(layout.cell("a.owl"))
        assertEquals(3, layout.page("a.owl"))
    }

    @Test fun aCellPastTheEdgeOrOverAnotherIsPlacedAfresh() {
        val layout = WidgetLayout(FakePrefs())
        // As a drop on the open grid could save into the folded layout before profiles switched touch handling.
        layout.set("a.owl", Cell(0, 3, 6, 4))
        layout.set("b.owl", Cell(6, 5, 6, 2))
        layout.set("c.owl", Cell(0, 4, 3, 2))
        layout.place(listOf(widget("a.owl", 6, 4), widget("b.owl", 6, 2), widget("c.owl", 3, 2)), GRID_COLUMNS, 10)
        assertEquals(Cell(0, 3, 6, 4), layout.cell("a.owl"))
        assertEquals(Cell(0, 0, 6, 2), layout.cell("b.owl"))
        assertEquals(Cell(0, 7, 3, 2), layout.cell("c.owl"))
    }

    @Test fun aSoundLayoutIsLeftAsItIs() {
        val prefs = FakePrefs()
        val layout = WidgetLayout(prefs)
        layout.set("a.owl", Cell(0, 0, 3, 3))
        layout.set("b.owl", Cell(3, 0, 3, 3))
        layout.save()
        val before = HashMap(prefs.values)
        layout.place(listOf(widget("a.owl", 3, 3), widget("b.owl", 3, 3)), GRID_COLUMNS, 10)
        assertEquals(before, prefs.values)
    }
}
