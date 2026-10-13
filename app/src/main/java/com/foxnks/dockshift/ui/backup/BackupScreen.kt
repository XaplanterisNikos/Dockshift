package com.foxnks.dockshift.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.data.backup.BackupStorage
import com.foxnks.dockshift.domain.backup.BackupReminder
import com.foxnks.dockshift.domain.backup.BackupSummary
import com.foxnks.dockshift.ui.legacy.LegacyImportItem
import com.foxnks.dockshift.ui.shift.ShiftFormat
import com.foxnks.dockshift.ui.theme.DockshiftTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Entry point of Menu → Data & backup: connects [BackupViewModel] and the system
 * pickers to [BackupScreen].
 *
 * @param onBack called when the user taps the back arrow.
 * @param viewModel provided by Hilt.
 */
@Composable
fun BackupRoute(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val saveTo = rememberLauncherForActivityResult(CreateBackupFile()) { uri -> viewModel.onSaveTo(uri?.toString()) }
    val restoreFrom = rememberLauncherForActivityResult(OpenBackupFile()) { uri -> viewModel.onRestoreChosen(uri?.toString()) }
    val shareTitle = stringResource(R.string.backup_share)

    BackupScreen(
        uiState = uiState,
        today = LocalDate.now(),
        onBack = onBack,
        onBackupNow = viewModel::onBackupNow,
        onSaveElsewhere = { saveTo.launch(uiState.fileName) },
        onRestore = { restoreFrom.launch(OpenBackupFile.MIME_TYPES) },
        onReminderToggled = viewModel::onReminderToggled,
        onShare = { uri ->
            // A phone without any app to share with is rare; then nothing happens
            runCatching { context.startActivity(shareBackupIntent(uri, shareTitle)) }
        },
        onRestoreContinue = viewModel::onRestoreContinue,
        onRestoreConfirmed = viewModel::onRestoreConfirmed,
        onDismiss = viewModel::onDismiss,
        onDeleteAllShifts = viewModel::onDeleteAllShifts,
        legacyImport = { LegacyImportItem() },
    )
}

/**
 * Everything about the user's data, in three groups: Backup (now, elsewhere, the
 * reminder), Restore (a backup, or the first version's file), Delete.
 *
 * @param uiState what to show.
 * @param today the current date, for "N days ago".
 * @param onBack back arrow tapped.
 * @param onBackupNow "Back up now" tapped.
 * @param onSaveElsewhere "Save elsewhere" tapped: opens the system "save as" picker.
 * @param onRestore "Restore" tapped: opens the system file picker.
 * @param onReminderToggled the reminder switch was flipped.
 * @param onShare "Share" tapped after saving, with the file's address.
 * @param onRestoreContinue the first restore confirmation was given.
 * @param onRestoreConfirmed the second, final restore confirmation was given.
 * @param onDismiss a dialog was closed.
 * @param onDeleteAllShifts "delete all shifts" was confirmed twice.
 * @param legacyImport the v1 import row (a slot, so previews need no Hilt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    uiState: BackupUiState,
    today: LocalDate,
    onBack: () -> Unit,
    onBackupNow: () -> Unit = {},
    onSaveElsewhere: () -> Unit = {},
    onRestore: () -> Unit = {},
    onReminderToggled: (Boolean) -> Unit = {},
    onShare: (String) -> Unit = {},
    onRestoreContinue: () -> Unit = {},
    onRestoreConfirmed: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onDeleteAllShifts: () -> Unit = {},
    legacyImport: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle(R.string.backup_section_backup)
            LastBackupLine(uiState.lastBackup, today)
            ActionItem(R.drawable.ic_backup, R.string.backup_now, stringResource(R.string.backup_now_summary, FOLDER_PATH), onBackupNow)
            ActionItem(R.drawable.ic_save_as, R.string.backup_elsewhere, stringResource(R.string.backup_elsewhere_summary), onSaveElsewhere)
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_reminder, BackupReminder.INTERVAL_DAYS)) },
                supportingContent = { Text(stringResource(R.string.backup_reminder_summary)) },
                trailingContent = { Switch(checked = uiState.reminder, onCheckedChange = null) },
                // The whole row toggles, not just the small switch
                modifier = Modifier.toggleable(
                    value = uiState.reminder,
                    role = Role.Switch,
                    onValueChange = onReminderToggled,
                ),
            )
            HorizontalDivider()

            SectionTitle(R.string.backup_section_restore)
            ActionItem(R.drawable.ic_restore, R.string.backup_restore, stringResource(R.string.backup_restore_summary), onRestore)
            legacyImport()
            HorizontalDivider()

            SectionTitle(R.string.backup_section_delete)
            DeleteAllItem(storedShifts = uiState.storedShifts, onConfirmed = onDeleteAllShifts)
        }
    }

    StepDialog(
        step = uiState.step,
        storedShifts = uiState.storedShifts,
        onShare = onShare,
        onRestoreContinue = onRestoreContinue,
        onRestoreConfirmed = onRestoreConfirmed,
        onDismiss = onDismiss,
    )
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

/** "Last backup: 14 Oct 2026 (3 days ago)", or "No backup yet" in the warning color. */
@Composable
private fun LastBackupLine(lastBackup: LocalDate?, today: LocalDate) {
    val text = if (lastBackup == null) {
        stringResource(R.string.backup_never)
    } else {
        val days = ChronoUnit.DAYS.between(lastBackup, today).toInt().coerceAtLeast(0)
        val ago = if (days == 0) stringResource(R.string.backup_today) else pluralStringResource(R.plurals.backup_days_ago, days, days)
        stringResource(R.string.backup_last, ShiftFormat.medium(lastBackup), ago)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (lastBackup == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** A tappable row with an icon, a title and one line under it. */
@Composable
private fun ActionItem(icon: Int, @StringRes title: Int, summary: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(summary) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** The dialog of the current step, if that step has one. */
@Composable
private fun StepDialog(
    step: BackupStep,
    storedShifts: Int,
    onShare: (String) -> Unit,
    onRestoreContinue: () -> Unit,
    onRestoreConfirmed: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (step) {
        BackupStep.Idle -> Unit
        BackupStep.Working -> WorkingDialog()
        is BackupStep.Saved -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(painterResource(R.drawable.ic_backup), contentDescription = null) },
            title = { Text(stringResource(R.string.backup_saved_title)) },
            text = {
                Text(
                    if (step.inDocuments) {
                        stringResource(R.string.backup_saved_documents, FOLDER_PATH)
                    } else {
                        stringResource(R.string.backup_saved_elsewhere)
                    },
                )
            },
            // Sending it off the phone is what protects against a lost phone
            confirmButton = {
                TextButton(onClick = {
                    onShare(step.uri)
                    onDismiss()
                }) { Text(stringResource(R.string.backup_share)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_ok)) } },
        )
        is BackupStep.RestorePreview -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(painterResource(R.drawable.ic_restore), contentDescription = null) },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryLines(step.summary)
                    Text(stringResource(R.string.backup_restore_replaces), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = onRestoreContinue) { Text(stringResource(R.string.backup_continue)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        is BackupStep.RestoreConfirm -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = { Text(stringResource(R.string.backup_restore_confirm_text, storedShifts, step.summary.shifts)) },
            confirmButton = {
                TextButton(onClick = onRestoreConfirmed) {
                    Text(stringResource(R.string.backup_restore_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        is BackupStep.Restored -> MessageDialog(stringResource(R.string.backup_restored, step.summary.shifts), onDismiss)
        is BackupStep.Failed -> MessageDialog(stringResource(step.failure.messageRes()), onDismiss)
    }
}

/** What a backup holds: its date, shifts with their range, leaves and machines. */
@Composable
private fun SummaryLines(summary: BackupSummary) {
    Text(stringResource(R.string.backup_summary_made, summary.createdAt.display()), style = MaterialTheme.typography.titleSmall)
    val first = summary.firstShift
    val last = summary.lastShift
    if (first != null && last != null) {
        Text(stringResource(R.string.backup_summary_shifts, summary.shifts, ShiftFormat.medium(first), ShiftFormat.medium(last)))
    } else {
        Text(stringResource(R.string.backup_summary_no_shifts))
    }
    Text(stringResource(R.string.backup_summary_leaves_machines, summary.leaves, summary.machines))
}

/** A non-dismissable progress dialog: a backup or restore must not be interrupted. */
@Composable
private fun WorkingDialog() {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.backup_working))
            }
        },
    )
}

/** A message with an OK button. */
@Composable
private fun MessageDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_ok)) } },
    )
}

/** The message of each failure. */
@StringRes
private fun BackupFailure.messageRes(): Int = when (this) {
    BackupFailure.SAVE -> R.string.backup_failed_save
    BackupFailure.READ -> R.string.backup_failed_read
    BackupFailure.INVALID -> R.string.backup_failed_invalid
    BackupFailure.RESTORE -> R.string.backup_failed_restore
}

/** "1 Oct 2026 09:30" in the phone's style. */
private fun LocalDateTime.display(): String =
    "${ShiftFormat.medium(toLocalDate())} ${format(DateTimeFormatter.ofPattern("HH:mm"))}"

/** Where "Back up now" saves, as the user sees it in a file manager. */
private const val FOLDER_PATH = "Documents/${BackupStorage.FOLDER}"

/** Two steps the "delete all" confirmation can be in. */
private enum class DeleteAllStep { NONE, FIRST, SECOND }

/**
 * "Delete all shifts", in red, behind two confirmations: the first says how many
 * shifts will go, the second that it cannot be undone.
 *
 * @param storedShifts how many shifts would be deleted.
 * @param onConfirmed called after the second confirmation.
 */
@Composable
private fun DeleteAllItem(storedShifts: Int, onConfirmed: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(DeleteAllStep.NONE) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_delete_all), color = MaterialTheme.colorScheme.error) },
        supportingContent = { Text(stringResource(R.string.settings_delete_all_summary)) },
        // Nothing to delete: the item stays visible but does nothing
        modifier = Modifier.clickable(enabled = storedShifts > 0) { step = DeleteAllStep.FIRST },
    )

    when (step) {
        DeleteAllStep.NONE -> Unit
        DeleteAllStep.FIRST -> AlertDialog(
            onDismissRequest = { step = DeleteAllStep.NONE },
            title = { Text(stringResource(R.string.delete_all_title)) },
            text = { Text(stringResource(R.string.delete_all_text, storedShifts)) },
            confirmButton = {
                TextButton(onClick = { step = DeleteAllStep.SECOND }) { Text(stringResource(R.string.delete_all_continue)) }
            },
            dismissButton = { TextButton(onClick = { step = DeleteAllStep.NONE }) { Text(stringResource(R.string.cancel)) } },
        )
        DeleteAllStep.SECOND -> AlertDialog(
            onDismissRequest = { step = DeleteAllStep.NONE },
            title = { Text(stringResource(R.string.delete_all_confirm_title)) },
            text = { Text(stringResource(R.string.delete_all_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    step = DeleteAllStep.NONE
                    onConfirmed()
                }) { Text(stringResource(R.string.delete_all_button), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { step = DeleteAllStep.NONE }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Android Studio preview of [BackupScreen]; not part of the running app. */
@Preview(showBackground = true)
@Composable
private fun BackupScreenPreview() {
    DockshiftTheme {
        BackupScreen(
            uiState = BackupUiState(lastBackup = LocalDate.of(2026, 10, 1), storedShifts = 245),
            today = LocalDate.of(2026, 10, 14),
            onBack = {},
        )
    }
}
