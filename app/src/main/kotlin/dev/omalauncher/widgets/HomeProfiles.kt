package dev.omalauncher.widgets

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
