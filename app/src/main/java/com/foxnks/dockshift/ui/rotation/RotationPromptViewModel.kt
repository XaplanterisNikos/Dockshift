package com.foxnks.dockshift.ui.rotation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.rotation.RotationAnchor
import com.foxnks.dockshift.domain.rotation.RotationSchedule
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.shift.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The one-time question "your shifts match the rotating schedule: show predictions?".
 *
 * Asked only when it can help: the schedule is the rotating one, no place is set, the
 * first-run import and profile questions are already answered (one dialog at a time),
 * the question was never answered, and the recent shifts fit exactly one place in the
 * rotation.
 *
 * @param settings the schedule, the place and whether the question was answered.
 * @param shifts recent shifts, to find the place.
 * @param clock the current time, for "recent".
 */
@HiltViewModel
class RotationPromptViewModel @Inject constructor(
    private val settings: SettingsRepository,
    shifts: ShiftRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)

    /** The place to offer, as the latest four-day rest up to today; null when not asking. */
    val suggestion: StateFlow<RotationAnchor?> = combine(
        settings.settings,
        // One full rotation period back: enough to find the place, without old schedules
        shifts.observeShifts(today.minusDays(RotationSchedule.PERIOD_DAYS.toLong()), today),
    ) { saved, recent ->
        val ask = saved.legacyImportAsked &&
            saved.workerPromptAsked &&
            !saved.rotationPromptAnswered &&
            saved.workSchedule.predicts &&
            saved.rotationAnchor == null
        if (ask) RotationSchedule.detect(recent)?.let { RotationSchedule.latestAnchor(it, today) } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /**
     * "Yes": the found place is saved and predictions start.
     *
     * @param anchor the place that was offered.
     */
    fun onAccept(anchor: RotationAnchor) {
        viewModelScope.launch {
            settings.setRotationAnchor(anchor)
            settings.setRotationPromptAnswered()
        }
    }

    /** "Not now": never asked again; the place can still be set in Settings. */
    fun onDecline() {
        viewModelScope.launch { settings.setRotationPromptAnswered() }
    }

    private companion object {
        /** Keeps the state alive across a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
