package com.foxnks.dockshift.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.settings.ThemeMode
import com.foxnks.dockshift.domain.worker.WorkerForm
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
 * in Settings re-themes the whole app immediately, and tells the splash screen when
 * the saved settings have been read. On the first start it also gives the user's
 * profile its permanent id.
 *
 * @param settings source of the saved appearance settings.
 */
@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {

    private val loaded = MutableStateFlow(false)

    /**
     * Current appearance. Started eagerly so the saved choice is applied as early
     * as possible; until it is read, the defaults (follow the phone) are used.
     */
    val appearance: StateFlow<Appearance> = settings.settings
        .map { Appearance(themeMode = it.themeMode, dynamicColor = it.dynamicColor) }
        // Other settings changing must not redraw the whole app
        .distinctUntilChanged()
        // Read once: the splash screen may go (same pass as the value, so no frame in between)
        .onEach { loaded.value = true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())

    /**
     * True once the saved settings have been read. The splash screen stays until then,
     * so the app never flashes in the wrong theme.
     */
    val ready: StateFlow<Boolean> = loaded.asStateFlow()

    init {
        // Once per install (the store ignores it when an id exists): the future online
        // account is tied to this id, and backups carry it to a new phone
        viewModelScope.launch { settings.ensureWorkerId(WorkerForm.newId()) }
    }
}
