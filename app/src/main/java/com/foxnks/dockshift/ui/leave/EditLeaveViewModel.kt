package com.foxnks.dockshift.ui.leave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.holidays.GreekHoliday
import com.foxnks.dockshift.domain.leave.Leave
import com.foxnks.dockshift.domain.leave.LeaveBalance
import com.foxnks.dockshift.domain.leave.LeaveProblem
import com.foxnks.dockshift.domain.leave.LeaveRepository
import com.foxnks.dockshift.domain.leave.LeaveRules
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.shift.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the add/edit leave screen shows. */
sealed interface EditLeaveUiState {

    /** The leave and the other leaves are being read. */
    data object Loading : EditLeaveUiState

    /**
     * @property id the leave's identity, 0 for a new one.
     * @property start first day picked.
     * @property end last day picked.
     * @property note the name typed, e.g. "Καλοκαιρινή".
     * @property problem why it cannot be saved yet, or null.
     * @property countedDays days that count against the entitlement (closed-port holidays left out).
     * @property closedDays holidays inside the leave on which the port is closed (they do not count).
     * @property remainingAfter days left in the start's year once this leave is saved; null without an entitlement.
     * @property confirmReplace how many stored shifts the leave would replace: asked before saving; null when nothing to ask.
     * @property confirmDelete true while "delete this leave?" is shown.
     * @property finished true once saved or deleted: the screen closes itself.
     */
    data class Editing(
        val id: Long,
        val start: LocalDate,
        val end: LocalDate,
        val note: String,
        val problem: LeaveProblem?,
        val countedDays: Int,
        val closedDays: Int,
        val remainingAfter: Int?,
        val confirmReplace: Int? = null,
        val confirmDelete: Boolean = false,
        val finished: Boolean = false,
    ) : EditLeaveUiState {
        /** True when editing a stored leave, false when adding one. */
        val isExisting: Boolean get() = id != 0L
    }
}

/**
 * ViewModel of the add/edit leave screen.
 *
 * Every change recomputes the counted days, the days left and any problem (end
 * before start, overlap with another leave). Saving a leave over days that already
 * have stored shifts asks first, then replaces them: a leave day cannot be worked.
 *
 * @param leaves stored leaves.
 * @param shifts stored shifts, replaced by the leave's days.
 * @param settings entitlements and closed holidays.
 * @param clock the current time, for the default day and "taken" versus "planned".
 */
@HiltViewModel
class EditLeaveViewModel @Inject constructor(
    private val leaves: LeaveRepository,
    private val shifts: ShiftRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val state = MutableStateFlow<EditLeaveUiState>(EditLeaveUiState.Loading)

    /** The screen state. */
    val uiState: StateFlow<EditLeaveUiState> = state.asStateFlow()

    /** The other stored leaves, read once at start (the screen is modal). */
    private var others: List<Leave> = emptyList()

    /** The entitlements by year, read once at start. */
    private var entitlements: Map<Int, Int> = emptyMap()

    /** The holidays on which the port is closed, read once at start. */
    private var portClosed: Set<GreekHoliday> = emptySet()

    private var started = false

    /**
     * Loads the leave to edit, or starts a new one on [day].
     * Safe to call again (e.g. after rotation): only the first call does anything.
     *
     * @param id the leave's identity, or 0 for a new one.
     * @param day the first day of a new leave, or null for today.
     */
    fun start(id: Long, day: LocalDate?) {
        if (started) return
        started = true
        viewModelScope.launch {
            val all = leaves.observeAll().first()
            val saved = settings.settings.first()
            others = all.filter { it.id != id }
            entitlements = saved.leaveEntitlements
            portClosed = saved.portClosedHolidays
            val existing = all.firstOrNull { it.id == id }
            val first = day ?: LocalDate.now(clock)
            state.value = build(
                id = existing?.id ?: 0L,
                start = existing?.start ?: first,
                end = existing?.end ?: first,
                note = existing?.note.orEmpty(),
            )
        }
    }

    /** Recomputes everything that depends on the picked days. */
    private fun build(id: Long, start: LocalDate, end: LocalDate, note: String): EditLeaveUiState.Editing {
        val problem = LeaveProblem.of(start, end, id, others)
        val leave = if (end >= start) Leave(id, start, end, note) else null
        val year = start.year
        val entitlement = LeaveRules.entitlementFor(year, entitlements)
        val othersOfYear = others.filter { it.start.year <= year && it.end.year >= year }
        // The balance of the start's year as it will be once this leave is saved
        val after = LeaveBalance.of(year, entitlement, othersOfYear + listOfNotNull(leave), portClosed, LocalDate.now(clock))
        return EditLeaveUiState.Editing(
            id = id,
            start = start,
            end = end,
            note = note,
            problem = problem,
            countedDays = leave?.let { LeaveRules.countedDays(it, portClosed) } ?: 0,
            closedDays = if (leave != null) LeaveRules.closedDates(start, end, portClosed).size else 0,
            remainingAfter = after.remaining,
        )
    }

    /** Applies a change to the picked values and recomputes. */
    private fun edit(change: (EditLeaveUiState.Editing) -> EditLeaveUiState.Editing) {
        state.update { current ->
            if (current is EditLeaveUiState.Editing) {
                val changed = change(current)
                build(changed.id, changed.start, changed.end, changed.note)
            } else {
                current
            }
        }
    }

    /**
     * The first day was picked; a last day before it moves along, so the range stays valid.
     *
     * @param date the new first day.
     */
    fun onStartSelected(date: LocalDate) = edit { it.copy(start = date, end = if (it.end < date) date else it.end) }

    /** @param date the new last day. */
    fun onEndSelected(date: LocalDate) = edit { it.copy(end = date) }

    /** @param text the note field's new text. */
    fun onNoteChanged(text: String) = edit { it.copy(note = text) }

    /** Saves; asks first when stored shifts would be replaced. */
    fun onSave() {
        val current = state.value as? EditLeaveUiState.Editing ?: return
        if (current.problem != null) return
        viewModelScope.launch {
            val covered = shifts.observeShifts(current.start, current.end).first()
            if (covered.isEmpty()) save(current) else state.value = current.copy(confirmReplace = covered.size)
        }
    }

    /** The user agreed: the leave replaces the stored shifts of its days. */
    fun onConfirmReplace() {
        val current = state.value as? EditLeaveUiState.Editing ?: return
        viewModelScope.launch { save(current.copy(confirmReplace = null)) }
    }

    /** The user kept the shifts; nothing is saved. */
    fun onCancelReplace() {
        state.update { if (it is EditLeaveUiState.Editing) it.copy(confirmReplace = null) else it }
    }

    /** Replaces the shifts of the leave's days and stores the leave. */
    private suspend fun save(current: EditLeaveUiState.Editing) {
        shifts.observeShifts(current.start, current.end).first().forEach { shifts.delete(it.date) }
        leaves.save(Leave(current.id, current.start, current.end, current.note.trim().ifEmpty { null }))
        state.value = current.copy(finished = true)
    }

    /** Shows "delete this leave?" (only for stored leaves). */
    fun onDeleteRequested() {
        state.update { if (it is EditLeaveUiState.Editing && it.isExisting) it.copy(confirmDelete = true) else it }
    }

    /** Hides the delete question. */
    fun onCancelDelete() {
        state.update { if (it is EditLeaveUiState.Editing) it.copy(confirmDelete = false) else it }
    }

    /** Deletes the leave; its days go back to the rotation's predictions. */
    fun onConfirmDelete() {
        val current = state.value as? EditLeaveUiState.Editing ?: return
        if (!current.isExisting) return
        viewModelScope.launch {
            leaves.delete(current.id)
            state.value = current.copy(confirmDelete = false, finished = true)
        }
    }
}
