package com.foxnks.dockshift.ui.legacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.data.legacy.TextFileReader
import com.foxnks.dockshift.domain.legacy.LegacyImportPlan
import com.foxnks.dockshift.domain.legacy.LegacyMigration
import com.foxnks.dockshift.domain.legacy.LegacyProblem
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.shift.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Where the v1 import currently is. Only one step at a time is shown.
 */
sealed interface LegacyImportStep {

    /** Nothing in progress. */
    data object Idle : LegacyImportStep

    /** Reading/checking the file, or saving it. */
    data object Working : LegacyImportStep

    /** The file is valid: show the report and let the user save. */
    data class Report(val plan: LegacyImportPlan) : LegacyImportStep

    /** The file has problems: show them; nothing can be saved. */
    data class Problems(val problems: List<LegacyProblem>) : LegacyImportStep

    /** The file is valid but adds nothing (empty, or every day already exists). */
    data object NothingToImport : LegacyImportStep

    /** The picked file could not be opened or read. */
    data object ReadFailed : LegacyImportStep

    /** Saving failed (e.g. no free space); the transaction left nothing half-saved. */
    data object SaveFailed : LegacyImportStep

    /** Saved. */
    data class Done(val saved: Int) : LegacyImportStep
}

/**
 * Screen state of the v1 import.
 *
 * @property storedShifts shifts currently stored in the app.
 * @property askOnStart true to show the first-run question "do you have data from
 * the first version?": only on a fresh install, only once.
 * @property step the import step to show.
 */
data class LegacyImportUiState(
    val storedShifts: Int = 0,
    val askOnStart: Boolean = false,
    val step: LegacyImportStep = LegacyImportStep.Idle,
)

/**
 * Drives the v1 → v2 migration: the first-run question, reading the picked file,
 * the report, and saving.
 *
 * @param migration reads/checks and saves v1 files.
 * @param reader reads the file the user picked.
 * @param settings remembers that the first-run question was answered.
 * @param shifts source of the stored shift count.
 */
@HiltViewModel
class LegacyImportViewModel @Inject constructor(
    private val migration: LegacyMigration,
    private val reader: TextFileReader,
    private val settings: SettingsRepository,
    shifts: ShiftRepository,
) : ViewModel() {

    private val step = MutableStateFlow<LegacyImportStep>(LegacyImportStep.Idle)

    /** Everything the UI shows, always up to date. */
    val uiState: StateFlow<LegacyImportUiState> =
        combine(settings.settings, shifts.observeCount(), step) { appSettings, count, currentStep ->
            LegacyImportUiState(
                storedShifts = count,
                // Asked only on a fresh install: never once answered, never with data present
                askOnStart = !appSettings.legacyImportAsked && count == 0,
                step = currentStep,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = LegacyImportUiState(),
        )

    /**
     * The user answered the first-run question. Either answer is final; with
     * "yes" the UI also opens the file picker.
     */
    fun onStartQuestionAnswered() {
        viewModelScope.launch { settings.setLegacyImportAsked() }
    }

    /**
     * The file picker closed.
     *
     * @param uri the picked file, or null if the user cancelled.
     */
    fun onFileChosen(uri: String?) {
        if (uri == null) return
        step.value = LegacyImportStep.Working
        viewModelScope.launch {
            val text = try {
                reader.read(uri)
            } catch (e: IOException) {
                step.value = LegacyImportStep.ReadFailed
                return@launch
            }
            val plan = migration.prepare(text)
            step.value = when {
                plan.problems.isNotEmpty() -> LegacyImportStep.Problems(plan.problems)
                !plan.canImport -> LegacyImportStep.NothingToImport
                else -> LegacyImportStep.Report(plan)
            }
        }
    }

    /** The user confirmed the report: save everything in one transaction. */
    fun onConfirmImport() {
        val report = step.value as? LegacyImportStep.Report ?: return
        step.value = LegacyImportStep.Working
        viewModelScope.launch {
            step.value = try {
                LegacyImportStep.Done(migration.commit(report.plan))
            } catch (e: CancellationException) {
                // Never swallow cancellation: the coroutine must stop
                throw e
            } catch (e: Exception) {
                // Any storage failure: all-or-nothing, so the user can simply retry
                LegacyImportStep.SaveFailed
            }
        }
    }

    /** Closes whatever dialog is shown, without saving anything. */
    fun onDismiss() {
        step.value = LegacyImportStep.Idle
    }

    private companion object {
        /** Keeps the state alive across a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
