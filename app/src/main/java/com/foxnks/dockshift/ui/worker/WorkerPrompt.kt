package com.foxnks.dockshift.ui.worker

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

/**
 * The first-run question "fill in your profile?", over whatever screen is open (see
 * [WorkerPromptViewModel]). "Yes" opens the profile; "Later" leaves it to the menu.
 *
 * @param onFillIn opens the profile screen.
 * @param viewModel provided by Hilt (activity scope: shown over every screen).
 */
@Composable
fun WorkerPrompt(onFillIn: () -> Unit, viewModel: WorkerPromptViewModel = hiltViewModel()) {
    val ask by viewModel.ask.collectAsStateWithLifecycle()
    if (!ask) return
    AlertDialog(
        // Back or tapping outside counts as "later": the question is never repeated
        onDismissRequest = viewModel::onAnswered,
        icon = { Icon(painterResource(R.drawable.ic_person), contentDescription = null) },
        title = { Text(stringResource(R.string.worker_prompt_title)) },
        text = { Text(stringResource(R.string.worker_prompt_text)) },
        confirmButton = {
            TextButton(onClick = {
                viewModel.onAnswered()
                onFillIn()
            }) { Text(stringResource(R.string.worker_prompt_yes)) }
        },
        dismissButton = { TextButton(onClick = viewModel::onAnswered) { Text(stringResource(R.string.worker_prompt_later)) } },
    )
}
