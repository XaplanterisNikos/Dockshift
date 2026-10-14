package com.foxnks.dockshift.ui.vessels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.vessels.ScheduledVessel
import com.foxnks.dockshift.domain.vessels.VesselSnapshot
import com.foxnks.dockshift.domain.vessels.WorkingVessel
import com.foxnks.dockshift.ui.navigation.AppBottomBar
import com.foxnks.dockshift.ui.navigation.TopLevel
import com.foxnks.dockshift.ui.theme.DockshiftTheme
import java.time.LocalDateTime

/**
 * Entry point of the Vessels tab.
 *
 * @param onSelectTopLevel switch screen from the bottom bar.
 * @param viewModel provided by Hilt.
 */
@Composable
fun VesselsRoute(onSelectTopLevel: (TopLevel) -> Unit, viewModel: VesselsViewModel = hiltViewModel()) {
    // Opening the tab, or coming back to it, reads the site if the lists are stale
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onShown() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    VesselsScreen(
        uiState = uiState,
        cooldownMinutes = rememberCooldownMinutes(uiState.lastManualRefresh),
        onRefresh = viewModel::onRefresh,
        onMessageShown = viewModel::onMessageShown,
        onSelectTopLevel = onSelectTopLevel,
    )
}

/**
 * The vessels in two tabs: "Working" (being worked now, with completion) and
 * "Scheduled" (expected, by berthing time, [PAGE_SIZE] per page). The refresh button
 * in the top bar locks for an hour after use and shows the minutes left.
 *
 * @param uiState what to show.
 * @param cooldownMinutes minutes until the refresh button unlocks; 0 when usable.
 * @param onRefresh the refresh button was tapped.
 * @param onMessageShown the refresh message was shown.
 * @param onSelectTopLevel switch screen from the bottom bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VesselsScreen(
    uiState: VesselsUiState,
    cooldownMinutes: Int,
    onRefresh: () -> Unit,
    onMessageShown: () -> Unit,
    onSelectTopLevel: (TopLevel) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val messageRes = uiState.message?.messageRes()
    val messageText = messageRes?.let { stringResource(it) }
    LaunchedEffect(uiState.message) {
        if (uiState.message != null) {
            messageText?.let { snackbar.showSnackbar(it) }
            onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_vessels)) },
                actions = {
                    if (uiState.configured) RefreshAction(uiState.refreshing, cooldownMinutes, onRefresh)
                },
            )
        },
        bottomBar = { AppBottomBar(selected = TopLevel.VESSELS, onSelect = onSelectTopLevel) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        val snapshot = uiState.snapshot
        when {
            !uiState.configured -> CenteredText(R.string.vessels_not_configured, Modifier.padding(innerPadding))
            snapshot == null -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                if (uiState.refreshing) CircularProgressIndicator() else Text(stringResource(R.string.vessels_not_read))
            }
            else -> VesselTabs(snapshot, Modifier.fillMaxSize().padding(innerPadding))
        }
    }
}

/** The refresh button; while locked, the minutes left instead, and a spinner while reading. */
@Composable
private fun RefreshAction(refreshing: Boolean, cooldownMinutes: Int, onRefresh: () -> Unit) {
    when {
        refreshing -> CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
        cooldownMinutes > 0 -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
            Icon(
                painterResource(R.drawable.ic_refresh),
                contentDescription = stringResource(R.string.vessels_refresh_locked, cooldownMinutes),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA),
            )
            Text(
                text = stringResource(R.string.vessels_minutes, cooldownMinutes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> IconButton(onClick = onRefresh) {
            Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.vessels_refresh))
        }
    }
}

/** The two tabs and the list of the selected one. */
@Composable
private fun VesselTabs(snapshot: VesselSnapshot, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text(stringResource(R.string.vessels_tab_working, snapshot.working.size)) },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text(stringResource(R.string.vessels_tab_scheduled, snapshot.scheduled.size)) },
            )
        }
        UpdatedLine(snapshot.fetchedAt, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (tab == 0) WorkingList(snapshot.working) else ScheduledList(snapshot.scheduled)
    }
}

/** The vessels being worked, each with its completion bar. */
@Composable
private fun WorkingList(vessels: List<WorkingVessel>) {
    if (vessels.isEmpty()) {
        CenteredText(R.string.vessels_none_working)
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(vessels, key = { it.name + it.callSign }) { vessel ->
            Column {
                WorkingVesselRow(vessel, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                vessel.callSign.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        text = stringResource(R.string.vessels_call_sign, it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

/** The expected vessels, [PAGE_SIZE] per page, with ‹ page/pages › below. */
@Composable
private fun ScheduledList(vessels: List<ScheduledVessel>) {
    if (vessels.isEmpty()) {
        CenteredText(R.string.vessels_none_scheduled)
        return
    }
    val pages = (vessels.size + PAGE_SIZE - 1) / PAGE_SIZE
    var page by rememberSaveable { mutableIntStateOf(0) }
    // A refresh may bring fewer pages
    val current = page.coerceIn(0, pages - 1)
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            items(vessels.drop(current * PAGE_SIZE).take(PAGE_SIZE), key = { "${it.name}|${it.berths}" }) { vessel ->
                ScheduledRow(vessel)
                HorizontalDivider()
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { page = current - 1 }, enabled = current > 0) {
                Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.vessels_page_previous))
            }
            Text(stringResource(R.string.vessels_page, current + 1, pages), style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = { page = current + 1 }, enabled = current < pages - 1) {
                Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.vessels_page_next))
            }
        }
    }
}

/** One expected vessel: name, line and service, berthing → departure, voyages. */
@Composable
private fun ScheduledRow(vessel: ScheduledVessel) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(vessel.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        val line = listOf(vessel.operator, vessel.lane).filter { it.isNotEmpty() }.joinToString(" · ")
        if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.vessels_scheduled_times, vessel.berths?.short() ?: "–", vessel.departs?.short() ?: "–"),
            style = MaterialTheme.typography.bodySmall,
        )
        val voyages = listOf(vessel.inVoyage, vessel.outVoyage).filter { it.isNotEmpty() }.distinct().joinToString(" / ")
        if (voyages.isNotEmpty()) {
            Text(
                text = stringResource(R.string.vessels_voyage, voyages),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A grey message in the middle of the free space. */
@Composable
private fun CenteredText(text: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
        Text(stringResource(text), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Expected vessels per page of the "Scheduled" tab. */
private const val PAGE_SIZE = 10

/** Material's alpha for disabled content. */
private const val DISABLED_ALPHA = 0.38f

/** Android Studio preview of [VesselsScreen]; not part of the running app. */
@Preview(showBackground = true)
@Composable
private fun VesselsScreenPreview() {
    val now = LocalDateTime.of(2026, 10, 14, 10, 0)
    DockshiftTheme {
        VesselsScreen(
            uiState = VesselsUiState(
                snapshot = VesselSnapshot(
                    fetchedAt = now,
                    working = listOf(
                        WorkingVessel("SEA BREEZE", "ABC1", now.minusDays(1), now.plusHours(28), 12),
                        WorkingVessel("ALPHA STAR", "XYZ9", now.minusDays(4), now.plusHours(3), 96),
                    ),
                    scheduled = emptyList(),
                ),
                lastManualRefresh = now.minusMinutes(18),
            ),
            cooldownMinutes = 42,
            onRefresh = {},
            onMessageShown = {},
            onSelectTopLevel = {},
        )
    }
}
