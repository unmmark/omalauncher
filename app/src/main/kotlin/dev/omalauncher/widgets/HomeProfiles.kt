package dev.omalauncher.widgets

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.omalauncher.upgrade.Versioned
import org.json.JSONArray
import org.json.JSONObject

/** Where an item sits folded: its [page], and its [cell] (null while that page hasn't been laid out). */
data class Placed(
    val key: String,
    val page: Int,
    val cell: Cell?,
)

/**
 * The open layout a foldable starts from the first time it's unfolded: what home showed there before profiles,
 * folded pages side by side. Folded pages 2k-1 and 2k become open page k, the second shifted right by a folded
 * grid's width.
 */
fun seedOpen(folded: List<Placed>): Map<String, Pair<Int, Cell?>> =
    folded.associate { p ->
        val page = p.page.coerceAtLeast(1)
        val right = page % 2 == 0
        p.key to ((page + 1) / 2 to p.cell?.let { if (right) it.copy(x = it.x + GRID_COLUMNS) else it })
    }

/** Home's two arrangements on a foldable: [Folded] (and on phones and tablets), [Open] on the inner screen. */
enum class Profile { Folded, Open }

/** What adding something from the home screen comes to; see [HomeProfiles.arrive]. */
enum class Arrival {
    /** It didn't exist: made now, shown only in the profile showing. */
    New,

    /** It exists but is hidden here: brought back instead of a second copy. */
    Reveal,

    /** It exists and shows here already. */
    Present,
}

/**
 * Folded and open home layouts, and what each one hides. Folded keeps the layout launchers before profiles
 * saved, untouched; open is its own, [OPEN_COLUMNS] wide, so a tile can span the hinge. The two start alike
 * ([seed]) and then go their own ways: each hides what the user took off it, and something made on one shows
 * only there until it's brought to the other.
 */
class HomeProfiles(
    private val prefs: SharedPreferences,
    val folded: WidgetLayout,
    val open: WidgetLayout,
) {
    var active by mutableStateOf(Profile.Folded)

    /** The layout showing. */
    val layout get() = layoutOf(active)

    val layouts get() = listOf(folded, open)

    fun layoutOf(profile: Profile) = if (profile == Profile.Open) open else folded

    private val hidden = mapOf(Profile.Folded to mutableStateListOf<String>(), Profile.Open to mutableStateListOf<String>())

    /** Whether the open layout was made from the folded one yet; see [seed]. */
    var seeded = false
        private set

    /** Open pages live in [WidgetLayout]'s plain map; this is read with them so pages recompose when one moves. */
    private var openPages by mutableIntStateOf(0)

    init {
        (Versioned.read(prefs.getString(STATE, null), VERSION) as? JSONObject)?.let { data ->
            data.optJSONObject("hidden")?.let { h ->
                for (p in Profile.entries) h.optJSONArray(p.name.lowercase())?.let { a -> for (i in 0 until a.length()) a.optString(i).ifEmpty { null }?.let(hidden.getValue(p)::add) }
            }
            seeded = data.optBoolean("seeded")
        }
    }

    fun isHidden(
        key: String,
        profile: Profile = active,
    ) = key in hidden.getValue(profile)

    fun hiddenKeys(profile: Profile = active): List<String> = hidden.getValue(profile).toList()

    /** Takes [key] off [profile]'s home; its cell there goes, the other profile keeps it. */
    fun hide(
        key: String,
        profile: Profile = active,
    ) {
        if (!isHidden(key, profile)) hidden.getValue(profile) += key
        layoutOf(profile).clearCell(key)
        save()
    }

    fun unhide(
        key: String,
        profile: Profile = active,
    ) {
        if (hidden.getValue(profile).remove(key)) save()
    }

    /** [key] is gone for good: no cell, page or hidden mark in either profile, so something new by that name starts fresh. */
    fun forget(key: String) {
        var changed = false
        for (list in hidden.values) changed = list.remove(key) || changed
        for (l in layouts) l.remove(key)
        openPages++
        if (changed) save()
    }

    /** [to] takes [from]'s cell, open page and hidden marks in both profiles, and [from] is forgotten: a folder made from an app, or the app left when one dissolves. */
    fun inherit(
        from: String,
        to: String,
    ) {
        for (l in layouts) {
            l.cell(from)?.let { l.set(to, it) }
            l.page(from)?.let { l.setPage(to, it) }
        }
        for (list in hidden.values) if (from in list && to !in list) list += to
        forget(from)
    }

    /**
     * The page [item] is on in [profile]. Folded, its own. Open, the one it was put on there; something that
     * arrived some other way (an agent wrote a widget file) is on the open page its folded page pairs into.
     */
    fun pageIn(
        item: HomeItem,
        profile: Profile = active,
    ): Int {
        if (profile == Profile.Folded) return item.page
        openPages
        return open.page(item.key) ?: ((item.page.coerceAtLeast(1) + 1) / 2)
    }

    fun setOpenPage(
        key: String,
        page: Int,
    ) {
        open.setPage(key, page)
        openPages++
    }

    /**
     * Something is being added from the home screen under [key]. New, it shows only in the profile showing:
     * the other one hides it, but only once the open layout exists ([seeded]): before the first unfold, [seed] places
     * everything from the folded home, so nothing needs hiding there. One that [existed] and is hidden here comes back rather than as a second copy.
     */
    fun arrive(
        key: String,
        existed: Boolean,
    ): Arrival {
        if (existed) return if (isHidden(key)) Arrival.Reveal else Arrival.Present
        val other = if (active == Profile.Open) Profile.Folded else Profile.Open
        hidden.getValue(active).remove(key)
        if (seeded && !isHidden(key, other)) hidden.getValue(other) += key
        save()
        return Arrival.New
    }

    /** The first unfold: the open layout starts as home looked there before, folded pages side by side; see [seedOpen]. */
    fun seed(foldedItems: List<Placed>) {
        if (seeded) return
        for ((key, place) in seedOpen(foldedItems)) {
            open.setPage(key, place.first)
            place.second?.let { open.set(key, it) }
        }
        open.save()
        for (key in hidden.getValue(Profile.Folded)) if (!isHidden(key, Profile.Open)) hidden.getValue(Profile.Open) += key
        seeded = true
        openPages++
        save()
    }

    private fun save() {
        val h = JSONObject()
        for (p in Profile.entries) h.put(p.name.lowercase(), JSONArray(hidden.getValue(p).toList()))
        prefs.edit().putString(STATE, Versioned.write(VERSION, JSONObject().put("hidden", h).put("seeded", seeded))).apply()
    }

    companion object {
        fun of(context: Context) =
            HomeProfiles(context.getSharedPreferences("home_profiles", Context.MODE_PRIVATE), WidgetLayout.folded(context), WidgetLayout.open(context))

        private const val STATE = "state"
        private const val VERSION = 1
    }
}
