package com.foxnks.dockshift.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.worker.WorkerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel of the menu: the user's profile, shown at the top.
 *
 * @param settings where the profile is stored.
 */
@HiltViewModel
class MenuViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {

    /** The profile; empty until filled in. */
    val worker: StateFlow<WorkerProfile> = settings.settings
        .map { it.worker }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WorkerProfile())

    private companion object {
        /** Keeps the state alive across a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
