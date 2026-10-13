package com.foxnks.dockshift.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.rotation.RotationAnchor
import com.foxnks.dockshift.domain.rotation.RotationSchedule
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.settings.ThemeMode
import com.foxnks.dockshift.domain.settings.WorkSchedule
import com.foxnks.dockshift.domain.shift.ShiftRepository
import com.foxnks.dockshift.domain.shift.ShiftType
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
 * What the Settings screen shows.
 */
sealed interface SettingsUiState {

    /** Settings are still being read (the first instant). */
    data object Loading : SettingsUiState

    /**
     * Settings are loaded.
     *
     * @property themeMode light/dark choice.
     * @property dynamicColor whether app colors follow the wallpaper.
         * @property showHolidays whether public holidays are marked.
     * @property workSchedule which shifts the operator works.
     * @property rotationAnchor the operator's place in the rotation, as their latest
     * four-day rest up to today; null when not set.
     * @property detectedAnchor the place found from the recent shifts, offered as the
     * answer when setting the place; null when nothing fits clearly.
     * @property today the current date (a four-day rest cannot start after it).
     */
    data class Ready(
        val themeMode: ThemeMode,
        val dynamicColor: Boolean,
        val showHolidays: Boolean = true,
        val workSchedule: WorkSchedule = WorkSchedule.ROTATING,
        val rotationAnchor: RotationAnchor? = null,
        val detectedAnchor: RotationAnchor? = null,
        val today: LocalDate = LocalDate.MIN,
    ) : SettingsUiState
}

/**
 * ViewModel of the Settings screen. Every change is saved at once; there is no
 * "save" button, as in the phone's own settings.
 *
 * @param settings where the settings are stored.
 * @param shifts stored shifts, for finding the rotation place.
 * @param clock the current time, for "today".
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val shifts: ShiftRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)

    /** The screen state, always holding the latest saved values. */
    val uiState: StateFlow<SettingsUiState> = combine(
        settings.settings,
        // One full rotation period back is enough to find the place, and avoids old schedules
        shifts.observeShifts(today.minusDays(RotationSchedule.PERIOD_DAYS.toLong()), today),
    ) { saved, recent ->
        SettingsUiState.Ready(
            themeMode = saved.themeMode,
            dynamicColor = saved.dynamicColor,
            showHolidays = saved.showHolidays,
            workSchedule = saved.workSchedule,
            rotationAnchor = saved.rotationAnchor?.let { RotationSchedule.latestAnchor(it, today) },
            detectedAnchor = RotationSchedule.detect(recent)?.let { RotationSchedule.latestAnchor(it, today) },
            today = today,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SettingsUiState.Loading,
        )

    /** @param mode the theme the user picked. */
    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    /** @param enabled new position of the "colors from wallpaper" switch. */
    fun onDynamicColorToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    /** @param schedule the work schedule the user picked. */
    fun onWorkScheduleSelected(schedule: WorkSchedule) {
        viewModelScope.launch { settings.setWorkSchedule(schedule) }
    }

    /**
     * Saves the operator's place in the rotation. The one-time suggestion is then never
     * asked again.
     *
     * @param cycleStart the first day of their last four-day rest.
     * @param firstType the shift that followed it.
     */
    fun onRotationAnchorSaved(cycleStart: LocalDate, firstType: ShiftType) {
        viewModelScope.launch {
            settings.setRotationAnchor(RotationAnchor(cycleStart, firstType))
            settings.setRotationPromptAnswered()
        }
    }

    /** Removes the rotation place: no more predictions until it is set again. */
    fun onRotationAnchorCleared() {
        viewModelScope.launch { settings.setRotationAnchor(null) }
    }

    /** @param enabled new position of the public holidays switch. */
    fun onShowHolidaysToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setShowHolidays(enabled) }
    }

    private companion object {
        /** Keeps the state alive across a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
