package com.foxnks.dockshift.data.legacy

import android.content.Context
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.legacy.LegacyNotes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * [LegacyNotes] in the user's language, from string resources.
 *
 * @param context the application context, injected by Hilt.
 */
class AndroidLegacyNotes @Inject constructor(
    @ApplicationContext private val context: Context,
) : LegacyNotes {

    override fun containerMoves(moves: Int): String =
        context.getString(R.string.legacy_note_container_moves, moves)

    override fun machineOnNonWorkingDay(machineLabel: String): String =
        context.getString(R.string.legacy_note_machine_non_working_day, machineLabel)
}
