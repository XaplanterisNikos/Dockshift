package com.foxnks.dockshift.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel of the home screen.
 *
 * Turns domain data into [HomeUiState] and user actions into domain calls.
 * It survives rotation, so the screen never reloads data on configuration changes.
 *
 * @param settingsRepository the settings source; the DataStore implementation in
 * the app, a fake one in tests.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /**
     * The screen state, always holding the latest value.
     *
     * WhileSubscribed(5_000) keeps reading settings for 5 seconds after the screen
     * stops observing: enough to survive a rotation without restarting the flow,
     * while still stopping the work when the app goes to the background.
     */
    val uiState: StateFlow<HomeUiState> = settingsRepository.settings
        .map { settings -> HomeUiState.Ready(rotationEnabled = settings.rotationEnabled) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = HomeUiState.Loading,
        )

    /**
     * Called when the user flips the rotating schedule switch.
     * No need to update [uiState] by hand: the saved value flows back through it.
     *
     * @param enabled the new switch position.
     */
    fun onRotationToggled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRotationEnabled(enabled)
        }
    }

    private companion object {
        /** How long to keep collecting after the last observer leaves. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
