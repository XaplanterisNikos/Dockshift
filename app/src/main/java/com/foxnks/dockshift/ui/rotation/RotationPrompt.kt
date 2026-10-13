package com.foxnks.dockshift.ui.rotation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.ui.shift.ShiftFormat
import com.foxnks.dockshift.ui.shift.label
import com.foxnks.dockshift.ui.theme.DockshiftTheme

/**
 * Shows the one-time "use the rotating schedule?" question over whatever screen is
 * open, when the recent shifts fit the rotation (see [RotationPromptViewModel]).
 *
 * @param viewModel provided by Hilt (activity scope: shown over every screen).
 */
@Composable
fun RotationPrompt(viewModel: RotationPromptViewModel = hiltViewModel()) {
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    val anchor = suggestion ?: return
    AlertDialog(
        // Dismissing (back, tap outside) is not an answer: the question comes back later
        onDismissRequest = {},
        icon = { Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null, tint = DockshiftTheme.shiftColors.forecast) },
        title = { Text(stringResource(R.string.rotation_prompt_title)) },
        text = {
            Text(
                stringResource(
                    R.string.rotation_prompt_text,
                    ShiftFormat.long(anchor.cycleStart),
                    anchor.firstType.label(),
                ),
            )
        },
        confirmButton = { TextButton(onClick = { viewModel.onAccept(anchor) }) { Text(stringResource(R.string.rotation_prompt_yes)) } },
        dismissButton = { TextButton(onClick = viewModel::onDecline) { Text(stringResource(R.string.rotation_prompt_no)) } },
    )
}
