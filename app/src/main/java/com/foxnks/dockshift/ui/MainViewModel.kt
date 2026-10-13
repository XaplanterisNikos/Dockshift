package com.foxnks.dockshift.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The appearance settings the whole app is drawn with.
 *
 * @property themeMode light/dark choice.
 * @property dynamicColor whether app colors follow the wallpaper.
 */
data class Appearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)

/**
 * Activity-level ViewModel: provides the [Appearance] for the theme, so a change
 * in Settings re-themes the whole app immediately.
 *
 * @param settings source of the saved appearance settings.
 */
@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {

    /**
     * Current appearance. Started eagerly so the saved choice is applied as early
     * as possible; until it is read, the defaults (follow the phone) are used.
     */
    val appearance: StateFlow<Appearance> = settings.settings
        .map { Appearance(themeMode = it.themeMode, dynamicColor = it.dynamicColor) }
        // Other settings changing must not redraw the whole app
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())
}
