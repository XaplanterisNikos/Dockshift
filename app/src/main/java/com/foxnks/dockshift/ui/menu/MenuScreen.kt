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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.worker.WorkerProfile
import com.foxnks.dockshift.domain.worker.WorkerRole
import com.foxnks.dockshift.ui.navigation.AppBottomBar
import com.foxnks.dockshift.ui.navigation.BackupKey
import com.foxnks.dockshift.ui.navigation.LeavesKey
import com.foxnks.dockshift.ui.navigation.MachinesKey
import com.foxnks.dockshift.ui.navigation.SettingsKey
import com.foxnks.dockshift.ui.navigation.StatisticsKey
import com.foxnks.dockshift.ui.navigation.TopLevel
import com.foxnks.dockshift.ui.navigation.WorkerKey
import com.foxnks.dockshift.ui.theme.DockshiftTheme
import com.foxnks.dockshift.ui.worker.labelRes

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
    MenuEntry(StatisticsKey, R.string.menu_statistics, R.string.menu_statistics_summary, R.drawable.ic_bar_chart),
    MenuEntry(LeavesKey, R.string.menu_leave, R.string.menu_leave_summary, R.drawable.ic_beach),
    MenuEntry(BackupKey, R.string.backup_title, R.string.menu_backup_summary, R.drawable.ic_backup),
)

/** Settings, kept apart below the sections: it changes the app, not your data. */
private val settingsEntry =
    MenuEntry(SettingsKey, R.string.settings_title, R.string.menu_settings_summary, R.drawable.ic_settings)

/**
 * Entry point of the menu.
 *
 * @param onOpen open the screen of a menu row.
 * @param onSelectTopLevel switch screen from the bottom bar.
 * @param viewModel provided by Hilt.
 */
@Composable
fun MenuRoute(onOpen: (NavKey) -> Unit, onSelectTopLevel: (TopLevel) -> Unit, viewModel: MenuViewModel = hiltViewModel()) {
    val worker by viewModel.worker.collectAsStateWithLifecycle()
    MenuScreen(onOpen = onOpen, onSelectTopLevel = onSelectTopLevel, worker = worker)
}

/**
 * The central menu: the user's profile at the top, what the app does (machines,
 * statistics, leave, data) below it, Settings at the bottom after a divider.
 *
 * @param onOpen open the screen of a menu row.
 * @param onSelectTopLevel switch screen from the bottom bar.
 * @param worker the user's profile, for the top row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(onOpen: (NavKey) -> Unit, onSelectTopLevel: (TopLevel) -> Unit, worker: WorkerProfile = WorkerProfile()) {
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
            WorkerRow(worker, onOpen)
            HorizontalDivider()
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

/**
 * The profile row: the user's name and role, or an invitation to fill them in.
 *
 * @param worker the profile.
 * @param onOpen opens the profile screen.
 */
@Composable
private fun WorkerRow(worker: WorkerProfile, onOpen: (NavKey) -> Unit) {
    val role = worker.role
    ListItem(
        headlineContent = { Text(worker.name.ifBlank { stringResource(R.string.worker_title) }) },
        supportingContent = {
            Text(
                if (role != null) {
                    // e.g. "Χειριστής · RTG, QC"
                    "${stringResource(role.labelRes())} · ${worker.machineCategories.joinToString(", ") { it.name }}"
                } else {
                    stringResource(R.string.menu_worker_summary)
                },
            )
        },
        leadingContent = { Icon(painterResource(R.drawable.ic_person), contentDescription = null) },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
        modifier = Modifier.clickable { onOpen(WorkerKey) },
    )
}

/** Android Studio preview of [MenuScreen]; not part of the running app. */
@Preview(showBackground = true)
@Composable
private fun MenuScreenPreview() {
    DockshiftTheme {
        MenuScreen(
            onOpen = {},
            onSelectTopLevel = {},
            worker = WorkerProfile(name = "Νίκος", role = WorkerRole.OPERATOR),
        )
    }
}
