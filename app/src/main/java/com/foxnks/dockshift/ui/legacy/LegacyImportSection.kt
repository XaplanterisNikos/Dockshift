package com.foxnks.dockshift.ui.legacy

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.legacy.LegacyImportPlan
import com.foxnks.dockshift.domain.legacy.LegacyProblem
import com.foxnks.dockshift.domain.legacy.LegacyProblem.NumberColumn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The v1 import, temporarily on the home screen: the stored-shift count, an
 * "Import from the first version" button, and every dialog of the flow.
 * It moves to Settings when the real screens arrive.
 *
 * @param viewModel provided by Hilt.
 */
@Composable
fun LegacyImportSection(viewModel: LegacyImportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The system file picker; its result comes back to the ViewModel
    val picker = rememberLauncherForActivityResult(OpenLegacyBackup()) { uri ->
        viewModel.onFileChosen(uri?.toString())
    }
    val openPicker = { picker.launch(OpenLegacyBackup.MIME_TYPES) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.legacy_stored_shifts, state.storedShifts),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = openPicker, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.legacy_import_button))
        }
    }

    // First-run question, only while nothing else is shown
    if (state.askOnStart && state.step == LegacyImportStep.Idle) {
        StartQuestionDialog(
            onYes = {
                viewModel.onStartQuestionAnswered()
                openPicker()
            },
            onNo = viewModel::onStartQuestionAnswered,
        )
    }

    StepDialog(
        step = state.step,
        onConfirm = viewModel::onConfirmImport,
        onDismiss = viewModel::onDismiss,
    )
}

/** "Do you have data from the first version?" Both answers close it for good. */
@Composable
private fun StartQuestionDialog(onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        // Back or tapping outside counts as "no": the question is never repeated
        onDismissRequest = onNo,
        title = { Text(stringResource(R.string.legacy_question_title)) },
        text = { Text(stringResource(R.string.legacy_question_text)) },
        confirmButton = { TextButton(onClick = onYes) { Text(stringResource(R.string.legacy_question_yes)) } },
        dismissButton = { TextButton(onClick = onNo) { Text(stringResource(R.string.legacy_question_no)) } },
    )
}

/** The dialog of the current import step, if that step has one. */
@Composable
private fun StepDialog(step: LegacyImportStep, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    when (step) {
        LegacyImportStep.Idle -> Unit
        LegacyImportStep.Working -> WorkingDialog()
        is LegacyImportStep.Report -> ReportDialog(step.plan, onConfirm, onDismiss)
        is LegacyImportStep.Problems -> ProblemsDialog(step.problems, onDismiss)
        LegacyImportStep.NothingToImport -> MessageDialog(R.string.legacy_nothing_to_import, onDismiss)
        LegacyImportStep.ReadFailed -> MessageDialog(R.string.legacy_read_failed, onDismiss)
        LegacyImportStep.SaveFailed -> MessageDialog(R.string.legacy_save_failed, onDismiss)
        is LegacyImportStep.Done -> MessageDialog(R.string.legacy_done, onDismiss, step.saved)
    }
}

/** A non-dismissable progress dialog: reading or saving must not be interrupted. */
@Composable
private fun WorkingDialog() {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.legacy_working))
            }
        },
    )
}

/** What will be imported, before anything is saved. */
@Composable
private fun ReportDialog(plan: LegacyImportPlan, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.legacy_report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.legacy_report_shifts, plan.shifts.size))
                val first = plan.firstDate
                val last = plan.lastDate
                if (first != null && last != null) {
                    Text(stringResource(R.string.legacy_report_range, first.display(), last.display()))
                }
                if (plan.newMachines.isNotEmpty()) {
                    val labels = plan.newMachines.map { it.label }.sorted().joinToString(", ")
                    Text(stringResource(R.string.legacy_report_new_machines, labels))
                }
                if (plan.duplicatesInFile > 0) {
                    Text(stringResource(R.string.legacy_report_duplicates, plan.duplicatesInFile))
                }
                if (plan.alreadyInApp > 0) {
                    Text(stringResource(R.string.legacy_report_already_in_app, plan.alreadyInApp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.legacy_report_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.legacy_cancel)) } },
    )
}

/** Everything wrong with the file, so the user can fix it in one go. */
@Composable
private fun ProblemsDialog(problems: List<LegacyProblem>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.legacy_problems_title)) },
        text = {
            // A long list scrolls inside the dialog instead of pushing the buttons away
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                problems.take(MAX_PROBLEMS_SHOWN).forEach { Text(problemText(it)) }
                if (problems.size > MAX_PROBLEMS_SHOWN) {
                    Text(stringResource(R.string.legacy_problems_more, problems.size - MAX_PROBLEMS_SHOWN))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.legacy_ok)) } },
    )
}

/** A one-line message with an OK button. */
@Composable
private fun MessageDialog(message: Int, onDismiss: () -> Unit, vararg args: Any) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(message, *args)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.legacy_ok)) } },
    )
}

/** One problem as a sentence in the user's language, e.g. "Line 213: unknown shift type «Πρω»". */
@Composable
private fun problemText(problem: LegacyProblem): String = when (problem) {
    is LegacyProblem.UnterminatedQuote ->
        stringResource(R.string.legacy_problem_unterminated_quote, problem.line)
    is LegacyProblem.WrongColumnCount ->
        stringResource(R.string.legacy_problem_columns, problem.line, problem.found)
    is LegacyProblem.NotANumber ->
        stringResource(R.string.legacy_problem_not_a_number, problem.line, columnName(problem.column), problem.value)
    is LegacyProblem.InvalidDate ->
        stringResource(R.string.legacy_problem_invalid_date, problem.line, problem.day, problem.month, problem.year)
    is LegacyProblem.UnknownShiftType ->
        stringResource(R.string.legacy_problem_unknown_type, problem.line, problem.value)
    is LegacyProblem.UnknownMachineCategory ->
        stringResource(R.string.legacy_problem_unknown_category, problem.line, problem.value)
}

/** Name of a numeric v1 column, for [problemText]. */
@Composable
private fun columnName(column: NumberColumn): String = stringResource(
    when (column) {
        NumberColumn.DAY -> R.string.legacy_column_day
        NumberColumn.MONTH -> R.string.legacy_column_month
        NumberColumn.YEAR -> R.string.legacy_column_year
        NumberColumn.CONTAINERS -> R.string.legacy_column_containers
    },
)

/** A date in the phone's own short style, e.g. "1 Ιουν 2022". */
private fun LocalDate.display(): String =
    format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/** Beyond this, the problems list only says how many more there are. */
private const val MAX_PROBLEMS_SHOWN = 30
