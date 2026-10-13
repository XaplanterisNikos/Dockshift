package com.foxnks.dockshift.ui.worker

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.worker.WorkerForm
import com.foxnks.dockshift.domain.worker.WorkerProblem
import com.foxnks.dockshift.domain.worker.WorkerRole
import com.foxnks.dockshift.ui.theme.DockshiftTheme

/**
 * Entry point of Menu → Worker.
 *
 * @param onDone called after saving or tapping back: closes the screen.
 * @param viewModel provided by Hilt.
 */
@Composable
fun WorkerRoute(onDone: () -> Unit, viewModel: WorkerViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Saved: leave the screen
    LaunchedEffect(uiState.finished) { if (uiState.finished) onDone() }
    WorkerScreen(
        uiState = uiState,
        onBack = onDone,
        onSave = viewModel::onSave,
        onNameChanged = viewModel::onNameChanged,
        onEmailChanged = viewModel::onEmailChanged,
        onRoleSelected = viewModel::onRoleSelected,
        onCategoryToggled = viewModel::onCategoryToggled,
    )
}

/**
 * The profile form: name, email, role, the categories worked (only those of the role)
 * and, at the bottom, the permanent id. Nothing is required; Save in the top bar.
 *
 * @param uiState what to show.
 * @param onBack back arrow tapped: leaves without saving.
 * @param onSave Save tapped.
 * @param onNameChanged the name was typed.
 * @param onEmailChanged the email was typed.
 * @param onRoleSelected a role was picked.
 * @param onCategoryToggled a category was ticked or unticked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerScreen(
    uiState: WorkerUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onNameChanged: (String) -> Unit = {},
    onEmailChanged: (String) -> Unit = {},
    onRoleSelected: (WorkerRole) -> Unit = {},
    onCategoryToggled: (MachineCategory) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.worker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = uiState.loaded) { Text(stringResource(R.string.edit_save)) }
                },
            )
        },
    ) { innerPadding ->
        if (!uiState.loaded) {
            Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.worker_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val nameTooLong = WorkerProblem.NAME_TOO_LONG in uiState.problems
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = onNameChanged,
                    label = { Text(stringResource(R.string.worker_name)) },
                    singleLine = true,
                    isError = nameTooLong,
                    supportingText = if (nameTooLong) {
                        { Text(stringResource(R.string.worker_name_too_long, WorkerForm.MAX_NAME_LENGTH)) }
                    } else {
                        null
                    },
                    // Names start with capitals
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                val emailInvalid = WorkerProblem.EMAIL_INVALID in uiState.problems
                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = onEmailChanged,
                    label = { Text(stringResource(R.string.worker_email)) },
                    singleLine = true,
                    isError = emailInvalid,
                    supportingText = if (emailInvalid) {
                        { Text(stringResource(R.string.worker_email_invalid)) }
                    } else {
                        null
                    },
                    // The email keyboard has "@" and ".com" at hand
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionTitle(R.string.worker_role)
            Column(Modifier.selectableGroup()) {
                WorkerRole.entries.forEach { role ->
                    ListItem(
                        headlineContent = { Text(stringResource(role.labelRes())) },
                        supportingContent = { Text(role.categories.sortedBy { it.ordinal }.joinToString(", ") { it.name }) },
                        leadingContent = { RadioButton(selected = uiState.role == role, onClick = null) },
                        // The whole row is the touch target
                        modifier = Modifier.selectable(
                            selected = uiState.role == role,
                            role = Role.RadioButton,
                            onClick = { onRoleSelected(role) },
                        ),
                    )
                }
            }

            // The categories appear once a role is chosen, only those of the role
            uiState.role?.let { role ->
                SectionTitle(R.string.worker_categories)
                Text(
                    text = stringResource(R.string.worker_categories_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (WorkerProblem.NO_CATEGORY in uiState.problems) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                MachineCategory.entries.filter { it in role.categories }.forEach { category ->
                    val checked = category in uiState.categories
                    ListItem(
                        headlineContent = { Text(category.name) },
                        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                        modifier = Modifier.toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { onCategoryToggled(category) },
                        ),
                    )
                }
            }

            // Read-only: the user never types or changes it
            Text(
                text = stringResource(R.string.worker_id, uiState.id),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** Small colored heading above a group of rows. */
@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** Name of a role. */
@StringRes
fun WorkerRole.labelRes(): Int = when (this) {
    WorkerRole.OPERATOR -> R.string.worker_role_operator
    WorkerRole.DRIVER -> R.string.worker_role_driver
    WorkerRole.BOTH -> R.string.worker_role_both
}

/** Android Studio preview of [WorkerScreen]; not part of the running app. */
@Preview(showBackground = true)
@Composable
private fun WorkerScreenPreview() {
    DockshiftTheme {
        WorkerScreen(
            uiState = WorkerUiState(
                loaded = true,
                id = "3f2b8c1e-0d4a-4b6e-9a1f-2c3d4e5f6a7b",
                name = "Νίκος",
                email = "nikos@",
                role = WorkerRole.OPERATOR,
                categories = setOf(MachineCategory.RTG, MachineCategory.QC),
                problems = setOf(WorkerProblem.EMAIL_INVALID),
            ),
            onBack = {},
            onSave = {},
        )
    }
}
