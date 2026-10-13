package com.foxnks.dockshift.ui.machines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.machine.MachineForm
import com.foxnks.dockshift.domain.machine.MachineRepository
import com.foxnks.dockshift.domain.machine.MachineSavePlan
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One machine of the list, with how many shifts used it.
 *
 * @property machine the machine.
 * @property shifts shifts it was driven in; 0 means it can still be deleted for good.
 */
data class MachineRow(val machine: Machine, val shifts: Int)

/** Why the form could not be saved, shown under the number field. */
sealed interface MachineFormError {

    /** The number field is empty. */
    data object BlankNumber : MachineFormError

    /** The number is longer than [MachineForm.MAX_NUMBER_LENGTH]. */
    data object NumberTooLong : MachineFormError

    /**
     * Another machine already has this number.
     *
     * @property existing that machine (its label, and whether it is inactive).
     */
    data class Duplicate(val existing: Machine) : MachineFormError
}

/**
 * The add/edit sheet while it is open.
 *
 * @property form the values being edited.
 * @property original the stored machine being edited, or null when adding one.
 * @property shifts shifts that use the machine (0 for a new one); decides whether
 * "delete for good" is offered.
 * @property error why the last save failed, or null.
 * @property confirmDelete true while the "delete for good?" question is shown.
 */
data class MachineFormState(
    val form: MachineForm,
    val original: Machine? = null,
    val shifts: Int = 0,
    val error: MachineFormError? = null,
    val confirmDelete: Boolean = false,
)

/** A short confirmation shown once at the bottom of the screen. */
sealed interface MachineMessage {

    /** @property label e.g. "RTG 46". */
    data class Added(val label: String) : MachineMessage

    /** @property label the machine that was inactive and is active again. */
    data class Reactivated(val label: String) : MachineMessage

    /** @property label the machine deleted for good. */
    data class Deleted(val label: String) : MachineMessage
}

/** What the machines screen shows. */
sealed interface MachinesUiState {

    /** The machine list is being read. */
    data object Loading : MachinesUiState

    /**
     * @property category the selected category chip.
     * @property query the search text (number or location).
     * @property rows the machines of [category] that match [query], active and inactive.
     * @property locations every distinct location in use, for the location suggestions.
     * @property sheet the open add/edit sheet, or null.
     * @property message a confirmation to show once, or null.
     */
    data class Ready(
        val category: MachineCategory,
        val query: String,
        val rows: List<MachineRow>,
        val locations: List<String>,
        val sheet: MachineFormState?,
        val message: MachineMessage?,
    ) : MachinesUiState
}

/**
 * ViewModel of the machines screen: filtering, and the add/edit sheet.
 *
 * Every rule (unique numbers, reactivation, clean values) is decided by
 * [MachineForm.plan] in the domain; this class only carries the plan out.
 *
 * @param machines the machine list.
 */
@HiltViewModel
class MachinesViewModel @Inject constructor(
    private val machines: MachineRepository,
) : ViewModel() {

    /** What the user chose on this screen; the machine data comes from the repository. */
    private data class Choices(
        val category: MachineCategory = MachineCategory.RTG,
        val query: String = "",
        val sheet: MachineFormState? = null,
        val message: MachineMessage? = null,
    )

    private val choices = MutableStateFlow(Choices())

    /** The screen state. */
    val uiState: StateFlow<MachinesUiState> =
        combine(machines.observeAll(), machines.observeUsage(), choices) { all, usage, chosen ->
            val search = chosen.query.trim()
            MachinesUiState.Ready(
                category = chosen.category,
                query = chosen.query,
                rows = all
                    .filter { it.category == chosen.category }
                    .filter { search.isEmpty() || it.number.contains(search) || it.location.orEmpty().contains(search, ignoreCase = true) }
                    .map { MachineRow(it, usage[it.id] ?: 0) },
                locations = all.mapNotNull { it.location }.distinct().sorted(),
                sheet = chosen.sheet,
                message = chosen.message,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MachinesUiState.Loading)

    /** @param category the category chip tapped. */
    fun onCategorySelected(category: MachineCategory) = choices.update { it.copy(category = category) }

    /** @param text the search field's new text. */
    fun onQueryChanged(text: String) = choices.update { it.copy(query = text) }

    /** Opens an empty sheet for a new machine, in the selected category. */
    fun onAddClicked() = choices.update { it.copy(sheet = MachineFormState(MachineForm(category = it.category))) }

    /** @param row the machine tapped: opens it for editing. */
    fun onMachineClicked(row: MachineRow) =
        choices.update { it.copy(sheet = MachineFormState(MachineForm.from(row.machine), original = row.machine, shifts = row.shifts)) }

    /**
     * Any field of the sheet changed. A stored machine's category is fixed, so a
     * changed category is ignored for it.
     *
     * @param form the form with the new values.
     */
    fun onFormChanged(form: MachineForm) = updateSheet { sheet ->
        val category = if (sheet.form.isNew) form.category else sheet.form.category
        // The old error no longer applies to the new values
        sheet.copy(form = form.copy(id = sheet.form.id, category = category), error = null)
    }

    /** Closes the sheet without saving. */
    fun onSheetDismissed() = choices.update { it.copy(sheet = null) }

    /** Saves the sheet, following the plan of the domain rules. */
    fun onSave() {
        val sheet = choices.value.sheet ?: return
        viewModelScope.launch {
            when (val plan = sheet.form.plan(machines.observeAll().first())) {
                is MachineSavePlan.Save -> {
                    val saved = machines.save(plan.machine)
                    close(if (sheet.form.isNew) MachineMessage.Added(saved.label) else null, show = saved)
                }
                is MachineSavePlan.Reactivate -> {
                    val saved = machines.save(plan.machine)
                    close(MachineMessage.Reactivated(saved.label), show = saved)
                }
                is MachineSavePlan.Duplicate -> showError(MachineFormError.Duplicate(plan.existing))
                MachineSavePlan.BlankNumber -> showError(MachineFormError.BlankNumber)
                MachineSavePlan.NumberTooLong -> showError(MachineFormError.NumberTooLong)
            }
        }
    }

    /** Asks "delete for good?"; only offered for machines that no shift used. */
    fun onDeleteRequested() = updateSheet { if (it.original != null && it.shifts == 0) it.copy(confirmDelete = true) else it }

    /** Hides the delete question. */
    fun onDeleteCancelled() = updateSheet { it.copy(confirmDelete = false) }

    /** Deletes the machine of the sheet for good, if still unused. */
    fun onDeleteConfirmed() {
        val original = choices.value.sheet?.original ?: return
        viewModelScope.launch {
            if (machines.delete(original.id)) {
                choices.update { it.copy(sheet = null, message = MachineMessage.Deleted(original.label)) }
            } else {
                // A shift started using it meanwhile: it can only be deactivated now
                updateSheet { it.copy(confirmDelete = false) }
            }
        }
    }

    /** The message was shown; do not show it again (e.g. after rotation). */
    fun onMessageShown() = choices.update { it.copy(message = null) }

    /** Applies a change to the open sheet, if any. */
    private fun updateSheet(change: (MachineFormState) -> MachineFormState) =
        choices.update { current -> current.sheet?.let { current.copy(sheet = change(it)) } ?: current }

    /** Keeps the sheet open with an error under the number. */
    private fun showError(error: MachineFormError) = updateSheet { it.copy(error = error) }

    /**
     * Closes the sheet and makes the saved machine visible: its category is selected
     * and the search cleared, so the user sees what was saved.
     */
    private fun close(message: MachineMessage?, show: Machine) = choices.update {
        it.copy(sheet = null, message = message ?: it.message, category = show.category, query = "")
    }

    private companion object {
        /** Keeps the list alive across a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
