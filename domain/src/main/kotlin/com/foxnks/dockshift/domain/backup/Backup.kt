package com.foxnks.dockshift.domain.backup

import com.foxnks.dockshift.domain.faults.MachineFault
import com.foxnks.dockshift.domain.leave.Leave
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.shift.Shift
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Everything a backup holds: the whole app, so a new phone gets back exactly what the
 * old one had.
 *
 * @property createdAt when the backup was made.
 * @property appVersion the app version that made it, for support.
 * @property shifts every stored shift, with its machines.
 * @property machines every machine, active or not, with its identity (shifts refer to it).
 * @property leaves every planned leave.
 * @property settings every setting.
 * @property faults every machine fault, open and fixed (none in backups made before faults existed).
 */
data class BackupContents(
    val createdAt: LocalDateTime,
    val appVersion: String,
    val shifts: List<Shift>,
    val machines: List<Machine>,
    val leaves: List<Leave>,
    val settings: AppSettings,
    val faults: List<MachineFault> = emptyList(),
)

/**
 * What a backup file contains, shown before restoring so the user can check it is the
 * right file.
 *
 * @property createdAt when the backup was made.
 * @property shifts how many shifts.
 * @property firstShift the earliest shift's day, or null without shifts.
 * @property lastShift the latest shift's day, or null without shifts.
 * @property leaves how many leaves.
 * @property machines how many machines.
 */
data class BackupSummary(
    val createdAt: LocalDateTime,
    val shifts: Int,
    val firstShift: LocalDate?,
    val lastShift: LocalDate?,
    val leaves: Int,
    val machines: Int,
) {
    companion object {
        /** @param contents the backup to describe. */
        fun of(contents: BackupContents): BackupSummary = BackupSummary(
            createdAt = contents.createdAt,
            shifts = contents.shifts.size,
            firstShift = contents.shifts.minOfOrNull { it.date },
            lastShift = contents.shifts.maxOfOrNull { it.date },
            leaves = contents.leaves.size,
            machines = contents.machines.size,
        )
    }
}

/**
 * Checks that a backup can be restored as it is: every machine a shift refers to is
 * in the backup, and no two machines share an identity.
 *
 * Restoring replaces everything, so a broken file must be refused before anything is
 * deleted.
 */
object BackupCheck {

    /**
     * @param contents the backup read from a file.
     * @return a description of the first problem found, or null when it is sound.
     */
    fun problemOf(contents: BackupContents): String? {
        val ids = contents.machines.map { it.id }
        if (ids.any { it <= 0 }) return "A machine has no identity"
        if (ids.size != ids.toSet().size) return "Two machines share an identity"
        val known = ids.toSet()
        contents.shifts.forEach { shift ->
            shift.machines.forEach { assignment ->
                if (assignment.machine.id !in known) return "The shift of ${shift.date} uses a machine missing from the backup"
            }
        }
        contents.faults.forEach { fault ->
            if (fault.machineId !in known) return "A fault of ${fault.reportedOn} is on a machine missing from the backup"
        }
        val days = contents.shifts.map { it.date }
        if (days.size != days.toSet().size) return "Two shifts on the same day"
        return null
    }
}

/** When to remind the user to make a backup. */
object BackupReminder {

    /** Days after the last backup before reminding again. */
    const val INTERVAL_DAYS = 15

    /**
     * Days since the last backup when a reminder is due, or null when it is not.
     *
     * Nothing is due while the reminder is off or there is nothing to lose (no shift).
     * Without any backup yet, it is due at once (reported as [Int.MAX_VALUE] days).
     *
     * @param enabled whether the user wants reminders.
     * @param lastBackup the day of the last backup, or null when never made.
     * @param storedShifts how many shifts are stored.
     * @param today the current date.
     */
    fun dueDays(enabled: Boolean, lastBackup: LocalDate?, storedShifts: Int, today: LocalDate): Int? {
        if (!enabled || storedShifts == 0) return null
        if (lastBackup == null) return Int.MAX_VALUE
        val days = ChronoUnit.DAYS.between(lastBackup, today).toInt()
        return if (days >= INTERVAL_DAYS) days else null
    }

    /**
     * File name of a backup made on [date], e.g. "dockshift-backup-2026-10-14.json".
     *
     * @param date the day of the backup.
     */
    fun fileName(date: LocalDate): String = "dockshift-backup-$date.json"
}

/**
 * Reads the whole app into a backup, and replaces the whole app with one.
 *
 * Defined here, stored by the :app module (database and settings).
 */
interface BackupRepository {

    /** Everything stored, as one backup. */
    suspend fun read(): BackupContents

    /**
     * Replaces every shift, machine, fault, leave and setting with the backup's, all or
     * nothing: if anything fails, the stored data stays as it was.
     *
     * @param contents a backup that passed [BackupCheck].
     */
    suspend fun restore(contents: BackupContents)
}
