package dev.omalauncher.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeProfilesTest {
    private val foldedPrefs = FakePrefs()
    private val openPrefs = FakePrefs()
    private val statePrefs = FakePrefs()

    private fun profiles() = HomeProfiles(statePrefs, WidgetLayout(foldedPrefs), WidgetLayout(openPrefs, OPEN_COLUMNS))

    @Test fun hidingIsPerProfileAndPersists() {
        profiles().apply {
            active = Profile.Open
            hide("a.owl")
        }
        val again = profiles()
        assertTrue(again.isHidden("a.owl", Profile.Open))
        assertFalse(again.isHidden("a.owl", Profile.Folded))
    }

    @Test fun hidingFreesTheCell() {
        val p = profiles()
        p.folded.set("a.owl", Cell(0, 0, 2, 2))
        p.hide("a.owl")
        assertNull(p.folded.cell("a.owl"))
    }

    @Test fun forgetClearsBothProfiles() {
        val p = profiles()
        p.folded.set("a.owl", Cell(0, 0, 2, 2))
        p.open.set("a.owl", Cell(6, 0, 2, 2))
        p.hide("a.owl", Profile.Folded)
        p.forget("a.owl")
        assertNull(p.folded.cell("a.owl"))
        assertNull(p.open.cell("a.owl"))
        assertFalse(p.isHidden("a.owl", Profile.Folded))
    }

    @Test fun openPageDefaultsToThePairedFoldedPage() {
        val p = profiles()
        p.active = Profile.Open
        assertEquals(2, p.pageIn(widget("new.owl", 2, 2, page = 4)))
        assertEquals(2, p.pageIn(widget("new.owl", 2, 2, page = 3)))
        p.setOpenPage("new.owl", 5)
        assertEquals(5, p.pageIn(widget("new.owl", 2, 2, page = 3)))
        assertEquals(3, p.pageIn(widget("new.owl", 2, 2, page = 3), Profile.Folded))
    }

    @Test fun somethingNewShowsOnlyWhereItWasMade() {
        val p = profiles()
        p.seed(emptyList())
        p.active = Profile.Open
        assertEquals(Arrival.New, p.arrive("a.owl", existed = false))
        assertTrue(p.isHidden("a.owl", Profile.Folded))
        assertFalse(p.isHidden("a.owl", Profile.Open))
    }

    @Test fun somethingMadeBeforeTheFirstUnfoldCarriesOver() {
        val p = profiles()
        assertEquals(Arrival.New, p.arrive("a.owl", existed = false))
        assertFalse(p.isHidden("a.owl", Profile.Open))
        p.seed(listOf(Placed("a.owl", 1, Cell(0, 0, 2, 2))))
        assertEquals(Cell(0, 0, 2, 2), p.open.cell("a.owl"))
    }

    @Test fun somethingHiddenHereComesBackInsteadOfACopy() {
        val p = profiles()
        p.hide("agent-claude.owl")
        assertEquals(Arrival.Reveal, p.arrive("agent-claude.owl", existed = true))
        assertEquals(Arrival.Present, p.arrive("other.owl", existed = true))
    }

    @Test fun aNewTerminalLeavesTheHiddenOneHidden() {
        val p = profiles()
        p.hide("terminal-abc.owl")
        assertEquals(Arrival.New, p.arrive("terminal-xyz.owl", existed = false))
        assertTrue(p.isHidden("terminal-abc.owl"))
    }

    @Test fun arrivingAfterForgetIsNew() {
        val p = profiles()
        p.hide("a.owl")
        p.forget("a.owl")
        assertEquals(Arrival.New, p.arrive("a.owl", existed = false))
        assertFalse(p.isHidden("a.owl"))
    }

    @Test fun seedingHappensOnceAndCarriesHiddenItems() {
        val p = profiles()
        p.hide("b.owl", Profile.Folded)
        p.seed(listOf(Placed("a.owl", 2, Cell(0, 0, 2, 2)), Placed("b.owl", 1, null)))
        assertEquals(Cell(6, 0, 2, 2), p.open.cell("a.owl"))
        assertEquals(1, p.open.page("a.owl"))
        assertTrue(p.isHidden("b.owl", Profile.Open))
        p.open.set("a.owl", Cell(0, 4, 2, 2))
        p.seed(listOf(Placed("a.owl", 2, Cell(0, 0, 2, 2))))
        assertEquals(Cell(0, 4, 2, 2), p.open.cell("a.owl"))
        assertTrue(profiles().seeded)
    }

    @Test fun inheritMovesAPlaceInBothProfiles() {
        val p = profiles()
        p.folded.set("app:a", Cell(1, 1, 1, 1))
        p.open.set("app:a", Cell(7, 1, 1, 1))
        p.setOpenPage("app:a", 2)
        p.hide("app:a", Profile.Open)
        p.inherit(from = "app:a", to = "folder:x")
        assertEquals(Cell(1, 1, 1, 1), p.folded.cell("folder:x"))
        assertEquals(2, p.open.page("folder:x"))
        assertTrue(p.isHidden("folder:x", Profile.Open))
        assertNull(p.folded.cell("app:a"))
        assertFalse(p.isHidden("app:a", Profile.Open))
    }

    @Test fun foldedStorageIsUntouched() {
        WidgetLayout(foldedPrefs).apply {
            set("a.owl", Cell(0, 0, 2, 2))
            save()
        }
        val before = HashMap(foldedPrefs.values)
        val p = profiles()
        p.seed(listOf(Placed("a.owl", 1, Cell(0, 0, 2, 2))))
        p.active = Profile.Open
        p.open.set("a.owl", Cell(4, 0, 6, 2))
        p.open.save()
        p.setOpenPage("a.owl", 3)
        assertEquals(before, foldedPrefs.values)
    }
}
