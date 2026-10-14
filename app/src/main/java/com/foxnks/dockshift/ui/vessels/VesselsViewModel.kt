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
import kotlinx.coroutines.flow.first
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
 * @property message how the last refresh went, when the user should hear about it;
 * null once shown.
 */
data class VesselsUiState(
    val configured: Boolean = true,
    val snapshot: VesselSnapshot? = null,
    val refreshing: Boolean = false,
    val lastManualRefresh: LocalDateTime? = null,
    val message: RefreshResult? = null,
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

    /** The screen state. */
    val uiState: StateFlow<VesselsUiState> = combine(
        vessels.snapshot,
        vessels.lastManualRefresh,
        refreshing,
        message,
    ) { snapshot, lastManual, busy, shown ->
        VesselsUiState(
            configured = vessels.isConfigured,
            snapshot = snapshot,
            refreshing = busy,
            lastManualRefresh = lastManual,
            message = shown,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), VesselsUiState(configured = vessels.isConfigured))

    /**
     * A screen showing the lists opened (or came back to the front): reads the site
     * if the lists are stale. Quiet: only a failure with nothing to show is reported.
     */
    fun onShown() {
        if (!vessels.isConfigured) return
        run(manual = false)
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
    private fun run(manual: Boolean) {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                val result = vessels.refresh(manual)
                message.value = when {
                    // A press always gets an answer
                    manual -> result
                    // Automatic: silent unless it failed and there is nothing to show
                    result == RefreshResult.NETWORK_ERROR || result == RefreshResult.FORMAT_ERROR ->
                        result.takeIf { vessels.snapshot.first() == null }
                    else -> null
                }
            } finally {
                refreshing.value = false
            }
        }
    }

    private companion object {
        /** Keeps the state alive across a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
