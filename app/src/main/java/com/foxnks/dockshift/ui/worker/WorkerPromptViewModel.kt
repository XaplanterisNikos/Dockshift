package com.foxnks.dockshift.ui.worker

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
 * The one-time first-run question "fill in your profile?".
 *
 * Asked after the "data from the first version?" question (one dialog at a time), and
 * only once: either answer, or saving the profile, closes it for good.
 *
 * @param settings whether the questions were answered.
 */
@HiltViewModel
class WorkerPromptViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {

    /** True while the question should be shown. */
    val ask: StateFlow<Boolean> = settings.settings
        .map { it.legacyImportAsked && !it.workerPromptAsked }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    /** Either answer: never asked again. "Yes" also opens the profile screen (the caller does that). */
    fun onAnswered() {
        viewModelScope.launch { settings.setWorkerPromptAsked() }
    }

    private companion object {
        /** Keeps the state alive across a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
