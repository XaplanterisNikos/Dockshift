package com.foxnks.dockshift.ui.menu

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavKey
import com.foxnks.dockshift.R
import com.foxnks.dockshift.ui.navigation.AppBottomBar
import com.foxnks.dockshift.ui.navigation.MachinesKey
import com.foxnks.dockshift.ui.navigation.SettingsKey
import com.foxnks.dockshift.ui.navigation.TopLevel
import com.foxnks.dockshift.ui.theme.DockshiftTheme

/**
 * One row of the menu.
 *
 * @property key the screen it opens.
 * @property title row title.
 * @property summary one line under the title.
 * @property icon leading icon.
 */
private data class MenuEntry(
    val key: NavKey,
    @StringRes val title: Int,
    @StringRes val summary: Int,
    @DrawableRes val icon: Int,
)

/**
 * The app's sections, in menu order. Only sections that exist are listed (no
 * "coming soon" rows); each new feature adds its own entry here.
 */
private val sections = listOf(
    MenuEntry(MachinesKey, R.string.menu_machines, R.string.menu_machines_summary, R.drawable.ic_forklift),
)

/** Settings, kept apart below the sections: it changes the app, not your data. */
private val settingsEntry =
    MenuEntry(SettingsKey, R.string.settings_title, R.string.menu_settings_summary, R.drawable.ic_settings)

/**
 * Entry point of the menu. It has no state of its own, so no ViewModel.
 *
 * @param onOpen open the screen of a menu row.
 * @param onSelectTopLevel switch screen from the bottom bar.
 */
@Composable
fun MenuRoute(onOpen: (NavKey) -> Unit, onSelectTopLevel: (TopLevel) -> Unit) {
    MenuScreen(onOpen = onOpen, onSelectTopLevel = onSelectTopLevel)
}

/**
 * The central menu: what the app does (machines, and later statistics, leave...)
 * on top, Settings at the bottom after a divider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(onOpen: (NavKey) -> Unit, onSelectTopLevel: (TopLevel) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_menu)) }) },
        bottomBar = { AppBottomBar(selected = TopLevel.MENU, onSelect = onSelectTopLevel) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            sections.forEach { MenuRow(it, onOpen) }
            HorizontalDivider()
            MenuRow(settingsEntry, onOpen)
        }
    }
}

/** A menu row: icon, title, summary and an arrow; the whole row is the touch target. */
@Composable
private fun MenuRow(entry: MenuEntry, onOpen: (NavKey) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(entry.title)) },
        supportingContent = { Text(stringResource(entry.summary)) },
        leadingContent = { Icon(painterResource(entry.icon), contentDescription = null) },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
        modifier = Modifier.clickable { onOpen(entry.key) },
    )
}

/** Android Studio preview of [MenuScreen]; not part of the running app. */
@Preview(showBackground = true)
@Composable
private fun MenuScreenPreview() {
    DockshiftTheme {
        MenuScreen(onOpen = {}, onSelectTopLevel = {})
    }
}
