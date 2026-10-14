package com.foxnks.dockshift.ui.vessels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.vessels.RefreshResult
import com.foxnks.dockshift.domain.vessels.VesselRefresh
import com.foxnks.dockshift.domain.vessels.VesselRepository
import com.foxnks.dockshift.domain.vessels.VesselSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the vessel screens show.
 *
 * @property configured false when this build has no vessel pages set up.
 * @property snapshot the last lists read, or null when never read.
 * @property refreshing true while the site is being read.
 * @property lastManualRefresh the time of the last manual refresh, or null; the
 * screen counts the button's lock down from it (see [VesselRefresh.manualAvailableAt]).
 * @property message the answer to a press of the refresh button ("updated", "locked"),
 * shown once; null once shown.
 * @property problem why the lists could not be read last time (no connection, app
 * blocked, site down, page changed), shown in place until a refresh succeeds; null
 * when the last refresh went fine.
 */
data class VesselsUiState(
    val configured: Boolean = true,
    val snapshot: VesselSnapshot? = null,
    val refreshing: Boolean = false,
    val lastManualRefresh: LocalDateTime? = null,
    val message: RefreshResult? = null,
    val problem: RefreshResult? = null,
)

/**
 * ViewModel of the vessel lists, used by the "Vessels" tab and by the card on "Today".
 * Opening either reads the site when the lists are stale; the button reads it by hand
 * (then locked for an hour).
 *
 * @param vessels the lists and their refresh.
 * @param clock the current time, for the button's lock.
 */
@HiltViewModel
class VesselsViewModel @Inject constructor(
    private val vessels: VesselRepository,
    private val clock: Clock,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<RefreshResult?>(null)
    private val problem = MutableStateFlow<Failure?>(null)

    /** The screen state. */
    val uiState: StateFlow<VesselsUiState> = combine(
        vessels.snapshot,
        vessels.lastManualRefresh,
        refreshing,
        message,
        problem,
    ) { snapshot, lastManual, busy, shown, failure ->
        // Lists read after the failure (e.g. by the other screen's refresh) settle it
        val stillFailing = failure?.takeIf { snapshot == null || snapshot.fetchedAt.isBefore(it.at) }
        VesselsUiState(
            configured = vessels.isConfigured,
            snapshot = snapshot,
            refreshing = busy,
            lastManualRefresh = lastManual,
            message = shown,
            problem = stillFailing?.result,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), VesselsUiState(configured = vessels.isConfigured))

    /**
     * A screen showing the lists opened (or came back to the front, e.g. from the
     * phone's settings): reads the site if the lists are stale. Quiet: a failure only
     * shows as [VesselsUiState.problem].
     */
    fun onShown() {
        if (!vessels.isConfigured) return
        // After a failure, try again even if the lists are fresh: the cause may be fixed
        run(manual = false, retry = uiState.value.problem != null)
    }

    /** The refresh button. Ignored while locked (the button is disabled then anyway). */
    fun onRefresh() {
        val now = LocalDateTime.now(clock)
        if (VesselRefresh.manualAvailableAt(uiState.value.lastManualRefresh, now) != null) return
        run(manual = true)
    }

    /** The message was shown. */
    fun onMessageShown() {
        message.value = null
    }

    /** Reads the site once at a time, and keeps the result for a message. */
    private fun run(manual: Boolean, retry: Boolean = false) {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                val result = vessels.refresh(manual, retry)
                // A failure stays on screen until a refresh works; "not needed" changes nothing
                when {
                    result == RefreshResult.UPDATED -> problem.value = null
                    result.isFailure -> problem.value = Failure(result, LocalDateTime.now(clock))
                }
                // A press gets an answer; failures already show as the problem
                message.value = if (manual && !result.isFailure) result else null
            } finally {
                refreshing.value = false
            }
        }
    }

    /**
     * A failed refresh and when it happened.
     *
     * @property result what went wrong.
     * @property at when; lists read later make it obsolete.
     */
    private data class Failure(val result: RefreshResult, val at: LocalDateTime)

    private companion object {
        /** Keeps the state alive across a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
