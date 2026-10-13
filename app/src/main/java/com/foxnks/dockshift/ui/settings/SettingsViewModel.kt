package com.foxnks.dockshift.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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
     * @property rotationEnabled whether the rotating schedule predicts shifts.
     */
    data class Ready(
        val themeMode: ThemeMode,
        val dynamicColor: Boolean,
        val rotationEnabled: Boolean,
    ) : SettingsUiState
}

/**
 * ViewModel of the Settings screen. Every change is saved at once; there is no
 * "save" button, as in the phone's own settings.
 *
 * @param settings where the settings are stored.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    /** The screen state, always holding the latest saved values. */
    val uiState: StateFlow<SettingsUiState> = settings.settings
        .map {
            SettingsUiState.Ready(
                themeMode = it.themeMode,
                dynamicColor = it.dynamicColor,
                rotationEnabled = it.rotationEnabled,
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

    /** @param enabled new position of the rotating schedule switch. */
    fun onRotationToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setRotationEnabled(enabled) }
    }

    private companion object {
        /** Keeps the state alive across a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
