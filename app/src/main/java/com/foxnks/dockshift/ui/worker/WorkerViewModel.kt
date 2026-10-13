package com.foxnks.dockshift.ui.worker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.worker.WorkerForm
import com.foxnks.dockshift.domain.worker.WorkerProblem
import com.foxnks.dockshift.domain.worker.WorkerRole
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the profile form shows.
 *
 * @property loaded false for the instant the stored profile is being read.
 * @property id the permanent id, shown read-only.
 * @property name the name as typed.
 * @property email the email as typed.
 * @property role the chosen role, or null.
 * @property categories the ticked categories.
 * @property problems what blocks saving; filled only after a save was tried, then kept
 * up to date while typing, so nobody is told off before finishing.
 * @property finished true once saved: the screen closes itself.
 */
data class WorkerUiState(
    val loaded: Boolean = false,
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: WorkerRole? = null,
    val categories: Set<MachineCategory> = emptySet(),
    val problems: Set<WorkerProblem> = emptySet(),
    val finished: Boolean = false,
)

/**
 * ViewModel of Menu → Worker: the user's profile, saved with a button (unlike the
 * settings) because the email is checked first.
 *
 * @param settings where the profile is stored.
 */
@HiltViewModel
class WorkerViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {

    private val state = MutableStateFlow(WorkerUiState())

    /** Whether a save was tried: from then on, problems show while typing. */
    private var checked = false

    /** The form state. */
    val uiState: StateFlow<WorkerUiState> = state.asStateFlow()

    init {
        // The stored profile is read once; afterwards the form holds the user's typing
        viewModelScope.launch {
            val worker = settings.settings.first().worker
            state.value = WorkerUiState(
                loaded = true,
                id = worker.id,
                name = worker.name,
                email = worker.email,
                role = worker.role,
                categories = worker.categories,
            )
        }
    }

    /** @param text the name as typed. */
    fun onNameChanged(text: String) = edit { it.copy(name = text) }

    /** @param text the email as typed. */
    fun onEmailChanged(text: String) = edit { it.copy(email = text) }

    /**
     * A role was picked: all of its categories are ticked, the user unticks the ones
     * they do not work.
     *
     * @param role the role.
     */
    fun onRoleSelected(role: WorkerRole) = edit {
        if (it.role == role) it else it.copy(role = role, categories = role.categories)
    }

    /** @param category the category whose tick was flipped. */
    fun onCategoryToggled(category: MachineCategory) = edit {
        it.copy(categories = if (category in it.categories) it.categories - category else it.categories + category)
    }

    /** Saves the profile, or shows what blocks it. Saving also answers the first-run question. */
    fun onSave() {
        val current = state.value
        if (!current.loaded) return
        val problems = WorkerForm.problems(current.name, current.email, current.role, current.categories)
        if (problems.isNotEmpty()) {
            checked = true
            state.value = current.copy(problems = problems)
            return
        }
        viewModelScope.launch {
            settings.setWorker(WorkerForm.build(current.id, current.name, current.email, current.role, current.categories))
            settings.setWorkerPromptAsked()
            state.value = current.copy(problems = emptySet(), finished = true)
        }
    }

    /** Applies a change of the form and, after a save attempt, re-checks it. */
    private fun edit(change: (WorkerUiState) -> WorkerUiState) {
        state.update { old ->
            val new = change(old)
            if (checked) new.copy(problems = WorkerForm.problems(new.name, new.email, new.role, new.categories)) else new
        }
    }
}
