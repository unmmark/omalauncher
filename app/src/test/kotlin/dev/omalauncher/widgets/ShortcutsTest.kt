package dev.omalauncher.widgets

import dev.omalauncher.linux.LinuxApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutsTest {
    private val profiles = HomeProfiles(FakePrefs(), WidgetLayout(FakePrefs()), WidgetLayout(FakePrefs(), OPEN_COLUMNS))
    private val shortcuts = Shortcuts(FakePrefs(), profiles)

    private fun app(id: String) = LinuxApp(id, id, false, id, null)

    @Test fun groupingWhileOpenKeepsFoldedCells() {
        shortcuts.addLinux(app("a"), 1)
        shortcuts.addLinux(app("b"), 1)
        profiles.folded.set("linux:a", Cell(0, 0, 1, 1))
        profiles.folded.set("linux:b", Cell(1, 0, 1, 1))
        profiles.open.set("linux:a", Cell(6, 0, 1, 1))
        profiles.open.set("linux:b", Cell(7, 0, 1, 1))
        profiles.active = Profile.Open
        val a = shortcuts.items.first { it.key == "linux:a" }
        val b = shortcuts.items.first { it.key == "linux:b" }
        val folder = shortcuts.group(b, a)!!
        assertEquals(Cell(0, 0, 1, 1), profiles.folded.cell(folder))
        assertEquals(Cell(6, 0, 1, 1), profiles.open.cell(folder))
        for (l in profiles.layouts) {
            assertNull(l.cell("linux:a"))
            assertNull(l.cell("linux:b"))
        }
        assertEquals(2, shortcuts.onGrid().filterIsInstance<Folder>().single().apps.size)
    }

    @Test fun removingAFolderForgetsItsAppsEverywhere() {
        shortcuts.addLinux(app("a"), 1)
        shortcuts.addLinux(app("b"), 1)
        val folder = shortcuts.group(shortcuts.items.first { it.key == "linux:b" }, shortcuts.items.first { it.key == "linux:a" })!!
        profiles.hide(folder, Profile.Open)
        shortcuts.remove(folder)
        assertTrue(shortcuts.onGrid().isEmpty())
        assertEquals(emptyList<String>(), profiles.hiddenKeys(Profile.Open))
    }

    @Test fun knowsWhatIsPinned() {
        shortcuts.addLinux(app("a"), 1)
        assertTrue(shortcuts.has(Shortcuts.linuxKey(app("a"))))
    }
}
