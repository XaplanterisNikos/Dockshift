package com.foxnks.dockshift.ui.vessels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.vessels.RefreshResult
import com.foxnks.dockshift.domain.vessels.VesselRefresh
import com.foxnks.dockshift.domain.vessels.WorkingVessel
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** "14/10 15:12": day/month and time, short enough for a list row. */
fun LocalDateTime.short(): String = format(DateTimeFormatter.ofPattern("d/M HH:mm"))

/**
 * One vessel being worked: name, times, and a bar with its completion.
 *
 * @param vessel the vessel.
 * @param modifier optional modifier from the caller.
 * @param showTimes false for the compact "Today" card (name and bar only).
 */
@Composable
fun WorkingVesselRow(vessel: WorkingVessel, modifier: Modifier = Modifier, showTimes: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = vessel.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text("${vessel.completion}%", style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(progress = { vessel.completion / 100f }, modifier = Modifier.fillMaxWidth())
        if (showTimes) {
            val arrived = vessel.arrived?.short() ?: "–"
            val departs = vessel.departs?.short() ?: "–"
            Text(
                text = stringResource(R.string.vessels_working_times, arrived, departs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * On "Today", under the shift: the vessels being worked now with their completion.
 * Reads the site when the lists are stale. Draws nothing when this build has no
 * vessel pages set up. Shows no errors: those belong to the Vessels tab.
 *
 * @param onOpen opens the Vessels tab.
 * @param modifier optional modifier from the caller.
 * @param viewModel provided by Hilt, one for the "Today" entry.
 */
@Composable
fun TodayVesselsCard(onOpen: () -> Unit, modifier: Modifier = Modifier, viewModel: VesselsViewModel = hiltViewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onShown() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Messages answer the refresh button, which lives on the Vessels tab, not here
    LaunchedEffect(state.message) { if (state.message != null) viewModel.onMessageShown() }
    if (!state.configured) return

    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_boat), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(R.string.vessels_today_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.refreshing) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            val snapshot = state.snapshot
            // No error messages here: the Vessels tab tells what went wrong, and the
            // "updated" date below tells how old these lists are
            when {
                snapshot == null -> Text(
                    text = stringResource(if (state.refreshing) R.string.vessels_loading else R.string.vessels_today_not_read),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                snapshot.working.isEmpty() -> Text(
                    text = stringResource(R.string.vessels_none_working),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> snapshot.working.forEach { WorkingVesselRow(it, showTimes = false) }
            }
            snapshot?.let { UpdatedLine(it.fetchedAt) }
        }
    }
}

/**
 * Why the lists could not be read, in the warning color. When a phone setting keeps
 * the app off the internet, a button opens the app's page in the phone's settings,
 * where the user turns its data on; coming back retries by itself.
 *
 * @param problem the failed refresh's result.
 * @param modifier optional modifier from the caller.
 */
@Composable
fun ProblemPanel(problem: RefreshResult, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        problem.messageRes()?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        if (problem == RefreshResult.APP_BLOCKED) {
            Text(
                text = stringResource(R.string.vessels_problem_blocked_how),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { context.openAppSettings() }) { Text(stringResource(R.string.vessels_open_settings)) }
        }
    }
}

/** Opens this app's page in the phone's settings (permissions, mobile data, battery). */
private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Every phone has this page; if one ever does not, the button just does nothing
    runCatching { startActivity(intent) }
}

/** "Updated 14/10 10:00", small and quiet. */
@Composable
fun UpdatedLine(fetchedAt: LocalDateTime, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.vessels_updated, fetchedAt.short()),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * Minutes until the refresh button unlocks, counted down live; 0 when it can be used.
 *
 * @param lastManualRefresh the time of the last manual refresh, or null.
 */
@Composable
fun rememberCooldownMinutes(lastManualRefresh: LocalDateTime?): Int {
    val minutes by produceState(initialValue = cooldownMinutes(lastManualRefresh), lastManualRefresh) {
        while (true) {
            value = cooldownMinutes(lastManualRefresh)
            if (value == 0) break
            // A minute display only needs checking twice a minute
            delay(TICK_MILLIS)
        }
    }
    return minutes
}

/** Whole minutes left of the lock, rounded up ("1′" until the very end); 0 when unlocked. */
private fun cooldownMinutes(lastManualRefresh: LocalDateTime?): Int {
    val now = LocalDateTime.now()
    val unlock = VesselRefresh.manualAvailableAt(lastManualRefresh, now) ?: return 0
    val seconds = Duration.between(now, unlock).seconds
    return ((seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE).toInt()
}

/** The message of a refresh result, or null when nothing needs saying. */
@StringRes
fun RefreshResult.messageRes(): Int? = when (this) {
    RefreshResult.UPDATED -> R.string.vessels_message_updated
    RefreshResult.NOT_NEEDED -> null
    RefreshResult.COOLING_DOWN -> R.string.vessels_message_cooling
    RefreshResult.NOT_CONFIGURED -> R.string.vessels_not_configured
    RefreshResult.NO_CONNECTION -> R.string.vessels_problem_offline
    RefreshResult.APP_BLOCKED -> R.string.vessels_problem_blocked
    RefreshResult.SITE_UNAVAILABLE -> R.string.vessels_problem_site
    RefreshResult.FORMAT_ERROR -> R.string.vessels_message_format
}

private const val TICK_MILLIS = 30_000L
private const val SECONDS_PER_MINUTE = 60L
