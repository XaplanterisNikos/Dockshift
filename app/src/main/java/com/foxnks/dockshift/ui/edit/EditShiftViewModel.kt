package com.foxnks.dockshift.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.machine.MachineForm
import com.foxnks.dockshift.domain.machine.MachineRepository
import com.foxnks.dockshift.domain.rotation.ShiftSuggestion
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.settings.WorkSchedule
import com.foxnks.dockshift.domain.shift.ShiftDraft
import com.foxnks.dockshift.domain.shift.ShiftRepository
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.domain.shift.WorkDay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the edit screen shows. */
sealed interface EditShiftUiState {

    /** The stored shift of the day is being read. */
    data object Loading : EditShiftUiState

    /**
     * @property draft the shift being edited.
     * @property originalDate the day of the stored shift being edited, or null for a new one.
     * @property confirmReplace a day the user moved the shift to that already has a shift:
     * ask before replacing it; null when there is nothing to ask.
     * @property confirmDelete true while the "delete this shift?" question is shown.
     * @property finished true once saved or deleted: the screen closes itself.
     * @property typeChosen false while a new shift has no type yet (nothing could be
     * suggested): no type chip is selected and saving waits for a choice.
     * @property workSchedule the operator's schedule: it decides which working types are offered.
     * @property showAllTypes true to offer every type, also those outside the schedule.
     * @property outsideSchedule the shift running now, when it is outside the schedule:
     * the screen says so (and offers every type); null otherwise.
     */
    data class Editing(
        val draft: ShiftDraft,
        val originalDate: LocalDate?,
        val confirmReplace: LocalDate? = null,
        val confirmDelete: Boolean = false,
        val finished: Boolean = false,
        val typeChosen: Boolean = true,
        val workSchedule: WorkSchedule = WorkSchedule.ROTATING,
        val showAllTypes: Boolean = false,
        val outsideSchedule: ShiftType? = null,
    ) : EditShiftUiState {
        /** True when editing a stored shift, false when adding a new one. */
        val isExisting: Boolean get() = originalDate != null

        /** True when some types are hidden by the schedule (a "show all" button is offered). */
        val hasHiddenTypes: Boolean get() = !showAllTypes && ShiftType.entries.any { !workSchedule.allows(it) }

        /**
         * The type chips to show: the schedule's types (always every non-working type),
         * or all of them; the selected type is never hidden.
         */
        val offeredTypes: List<ShiftType>
            get() = ShiftType.entries.filter {
                showAllTypes || workSchedule.allows(it) || (typeChosen && it == draft.type)
            }
    }
}

/**
 * ViewModel of the add/edit shift screen.
 *
 * The day to edit comes from the navigation key through [start]; each screen entry
 * gets its own ViewModel (Navigation 3 ViewModel store), so [start] runs once.
 * All editing rules live in [ShiftDraft]; this class only loads and saves.
 *
 * @param shifts stored shifts.
 * @param machines the machine list, for the machine picker.
 * @param settings the work schedule and rotation, for the offered and suggested types.
 * @param clock the current time, for the suggestion by time of day.
 */
@HiltViewModel
class EditShiftViewModel @Inject constructor(
    private val shifts: ShiftRepository,
    private val machines: MachineRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val state = MutableStateFlow<EditShiftUiState>(EditShiftUiState.Loading)

    /** The screen state. */
    val uiState: StateFlow<EditShiftUiState> = state.asStateFlow()

    /** Active machines for the picker, sorted by category and number. */
    val activeMachines: StateFlow<List<Machine>> = machines.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private var started = false

    /**
     * Loads the day to edit: its stored shift, or a new one with the suggested type
     * (see [ShiftSuggestion]): the prediction, the shift running now, or nothing.
     * Safe to call again (e.g. after rotation): only the first call does anything.
     *
     * @param date the day from the navigation key.
     */
    fun start(date: LocalDate) {
        if (started) return
        started = true
        viewModelScope.launch {
            val existing = shifts.observeShift(date).first()
            val appSettings = settings.settings.first()
            val schedule = appSettings.workSchedule
            state.value = if (existing != null) {
                EditShiftUiState.Editing(
                    draft = ShiftDraft.from(existing),
                    originalDate = existing.date,
                    workSchedule = schedule,
                    // A stored shift outside the schedule (e.g. before it changed) stays editable
                    showAllTypes = !schedule.allows(existing.type),
                )
            } else {
                val now = LocalDateTime.now(clock)
                // Yesterday's night may still be running: then "now" belongs to yesterday
                val yesterday = shifts.observeShift(now.toLocalDate().minusDays(1)).first()?.type
                val currentWorkDay = WorkDay.current(now, yesterday)
                when (val suggestion = ShiftSuggestion.of(date, now, currentWorkDay, appSettings)) {
                    is ShiftSuggestion.Suggested -> EditShiftUiState.Editing(
                        draft = ShiftDraft(date).withType(suggestion.type),
                        originalDate = null,
                        workSchedule = schedule,
                    )
                    is ShiftSuggestion.OutsideSchedule -> EditShiftUiState.Editing(
                        draft = ShiftDraft(date),
                        originalDate = null,
                        typeChosen = false,
                        workSchedule = schedule,
                        // Outside the schedule: say so, and let any type be picked
                        showAllTypes = true,
                        outsideSchedule = suggestion.runningNow,
                    )
                    ShiftSuggestion.None -> EditShiftUiState.Editing(
                        draft = ShiftDraft(date),
                        originalDate = null,
                        typeChosen = false,
                        workSchedule = schedule,
                    )
                }
            }
        }
    }

    /** Applies a change to the draft, if editing. */
    private fun edit(change: (ShiftDraft) -> ShiftDraft) {
        state.update { current ->
            if (current is EditShiftUiState.Editing) current.copy(draft = change(current.draft)) else current
        }
    }

    /** @param date the day picked in the date picker. */
    fun onDateSelected(date: LocalDate) = edit { it.withDate(date) }

    /** @param type the shift type chip tapped. */
    fun onTypeSelected(type: ShiftType) {
        state.update { current ->
            if (current is EditShiftUiState.Editing) {
                current.copy(draft = current.draft.withType(type), typeChosen = true)
            } else {
                current
            }
        }
    }

    /** "Other shift": offer every type, also those outside the work schedule. */
    fun onShowAllTypes() {
        state.update { if (it is EditShiftUiState.Editing) it.copy(showAllTypes = true) else it }
    }

    /** @param enabled new position of the overtime switch. */
    fun onOvertimeToggled(enabled: Boolean) = edit { it.withOvertime(enabled) }

    /** @param machine the machine picked in the machine sheet. */
    fun onMachineAdded(machine: Machine) = edit { it.addMachine(machine) }

    /**
     * Adds a machine that was missing from the picker (or reactivates it, if it was
     * deactivated) and puts it in the shift.
     *
     * @param category the category selected in the picker.
     * @param number the typed fleet number.
     */
    fun onMachineQuickAdded(category: MachineCategory, number: String) {
        val clean = number.trim()
        // Same limits as the machines screen; the picker only offers valid numbers
        if (clean.isEmpty() || clean.length > MachineForm.MAX_NUMBER_LENGTH) return
        viewModelScope.launch {
            val machine = machines.addOrReactivate(category, clean)
            edit { it.addMachine(machine) }
        }
    }

    /** @param index position of the machine to remove. */
    fun onMachineRemoved(index: Int) = edit { it.removeMachineAt(index) }

    /** @param index position of the machine to move one place up. */
    fun onMachineMovedUp(index: Int) = edit { it.moveMachineUp(index) }

    /** @param index position of the machine to move one place down. */
    fun onMachineMovedDown(index: Int) = edit { it.moveMachineDown(index) }

    /** @param index position of the machine whose overtime mark is flipped. */
    fun onMachineOvertimeToggled(index: Int) = edit { it.toggleOvertimeAt(index) }

    /** @param text the comment field's new text. */
    fun onCommentsChanged(text: String) = edit { it.withComments(text) }

    /**
     * Saves. If the shift was moved to a day that already has a shift, asks first
     * ([EditShiftUiState.Editing.confirmReplace]) instead of overwriting silently.
     */
    fun onSave() {
        val current = state.value as? EditShiftUiState.Editing ?: return
        // Nothing to save until a type is picked
        if (!current.typeChosen) return
        viewModelScope.launch {
            val target = current.draft.date
            val movedOntoAnotherShift = target != current.originalDate && shifts.observeShift(target).first() != null
            if (movedOntoAnotherShift) {
                state.value = current.copy(confirmReplace = target)
            } else {
                save(current)
            }
        }
    }

    /** The user agreed to replace the shift of the target day. */
    fun onConfirmReplace() {
        val current = state.value as? EditShiftUiState.Editing ?: return
        viewModelScope.launch { save(current.copy(confirmReplace = null)) }
    }

    /** The user cancelled the replace question; keep editing. */
    fun onCancelReplace() {
        state.update { if (it is EditShiftUiState.Editing) it.copy(confirmReplace = null) else it }
    }

    /** Stores the draft; a shift moved to another day leaves its old day empty. */
    private suspend fun save(current: EditShiftUiState.Editing) {
        shifts.save(current.draft.toShift())
        val original = current.originalDate
        if (original != null && original != current.draft.date) shifts.delete(original)
        state.value = current.copy(finished = true)
    }

    /** Shows the "delete this shift?" question (only for stored shifts). */
    fun onDeleteRequested() {
        state.update { if (it is EditShiftUiState.Editing && it.isExisting) it.copy(confirmDelete = true) else it }
    }

    /** Hides the delete question. */
    fun onCancelDelete() {
        state.update { if (it is EditShiftUiState.Editing) it.copy(confirmDelete = false) else it }
    }

    /** Deletes the stored shift being edited. */
    fun onConfirmDelete() {
        val current = state.value as? EditShiftUiState.Editing ?: return
        val original = current.originalDate ?: return
        viewModelScope.launch {
            shifts.delete(original)
            state.value = current.copy(confirmDelete = false, finished = true)
        }
    }

    private companion object {
        /** Keeps the machine list alive across a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
