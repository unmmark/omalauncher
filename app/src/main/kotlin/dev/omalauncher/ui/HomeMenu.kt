package dev.omalauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.omalauncher.Runtime
import dev.omalauncher.linux.newAppCommand
import dev.omalauncher.widgets.Folder
import dev.omalauncher.widgets.HomeItem
import dev.omalauncher.widgets.Shortcut
import dev.omalauncher.widgets.WidgetItem
import dev.omalauncher.widgets.newWidgetCommand
import kotlinx.coroutines.launch

@Composable
internal fun EditBar(content: @Composable () -> Unit) {
    Row(
        Modifier
            .frame(2.dp, Palette.accent, Palette.background)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
internal fun EditAction(
    label: String,
    color: androidx.compose.ui.graphics.Color = Palette.foreground,
    onClick: () -> Unit,
) {
    Box(Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)) {
        BasicText(label, style = Mono.copy(color = color, fontSize = TextSize.Body))
    }
}

/**
 * The long-press menu for empty home space. Drawn by the launcher over the whole
 * screen (not inside the home page's system-bar padding) so its scrim covers the
 * status and navigation bars too.
 */
@Composable
fun HomeMenu(
    runtime: Runtime,
    state: LauncherState,
) {
    if (!state.homeMenuOpen) return
    val engine = runtime.widgets
    val agent = runtime.agents.default
    val agentName = agent?.name?.lowercase() ?: "an agent"
    // These need Linux; without it they open its install sheet.
    val linux = runtime.bootstrap.isInstalled
    val scope = rememberCoroutineScope()

    /** Recolors right away, then applies the full theme: its first background, like omarchy-theme-set, and the guest's configs. */
    fun pickTheme(theme: dev.omalauncher.theme.BundledTheme) {
        runtime.themes.pick(theme)
        // Until all themes are downloaded, one that isn't there yet is fetched on its own.
        scope.launch { if (runtime.themes.complete) runtime.themes.applyPicked() else runtime.themes.fetchPicked() }
    }
    Menu(onDismiss = { state.homeMenuOpen = false }) {
        MenuItem("new widget with $agentName") {
            state.homeMenuOpen = false
            when {
                !linux -> state.linuxOpen = true
                agent == null -> state.agentsOpen = true
                else -> runtime.terminal(newWidgetCommand(agent.bin), "New widget", agent)
            }
        }
        MenuItem("new app with $agentName") {
            state.homeMenuOpen = false
            when {
                !linux -> state.linuxOpen = true
                agent == null -> state.agentsOpen = true
                else -> runtime.terminal(newAppCommand(agent.bin), "New app", agent)
            }
        }
        val hidden = engine.hiddenItems()
        if (hidden.isNotEmpty()) {
            MenuItem("hidden widgets (${hidden.size})") {
                state.homeMenuOpen = false
                state.hiddenOpen = true
            }
        }
        MenuItem("widget store") {
            state.homeMenuOpen = false
            state.storeOpen = true
        }
        MenuItem("AUR app store") {
            state.homeMenuOpen = false
            if (linux) state.packagesOpen = true else state.linuxOpen = true
        }
        Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) { ThemeStrip(
                runtime.themes,
                gutter = 20.dp,
                onPick = ::pickTheme,
                onBrowse = {
                    state.homeMenuOpen = false
                    state.themeBrowserOpen = true
                },
            ) }
    }
}

/** What the home profile showing hides; a tap brings one back to the page showing. */
@Composable
fun HiddenMenu(
    runtime: Runtime,
    state: LauncherState,
) {
    if (!state.hiddenOpen) return
    val engine = runtime.widgets
    val items = engine.hiddenItems()
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) state.hiddenOpen = false }
    Menu(onDismiss = { state.hiddenOpen = false }) {
        for (item in items) {
            MenuItem(hiddenLabel(item)) {
                engine.reveal(item.key, state.currentPage)
                if (items.size == 1) state.hiddenOpen = false
            }
        }
    }
}

private fun hiddenLabel(item: HomeItem) =
    when (item) {
        is WidgetItem -> item.spec.name
        is Shortcut -> item.label
        is Folder -> "folder · " + item.apps.joinToString(", ") { it.label }
    }

@Composable
private fun Menu(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.overlay)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(min = 240.dp, max = 360.dp)
                .frame(2.dp, Palette.accent, Palette.background, radius = 20.dp)
                .clickable(enabled = false) {}
                .padding(8.dp),
        ) { content() }
    }
}

@Composable
private fun MenuItem(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) { BasicText(label, style = Mono.copy(fontSize = TextSize.Item)) }
}
