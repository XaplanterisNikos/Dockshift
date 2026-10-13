package com.foxnks.dockshift.data.backup

import com.foxnks.dockshift.data.db.ShiftTypeCodes
import com.foxnks.dockshift.domain.backup.BackupContents
import com.foxnks.dockshift.domain.holidays.GreekHoliday
import com.foxnks.dockshift.domain.leave.Leave
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.rotation.RotationAnchor
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.settings.CalendarView
import com.foxnks.dockshift.domain.settings.ThemeMode
import com.foxnks.dockshift.domain.settings.WorkSchedule
import com.foxnks.dockshift.domain.shift.MachineAssignment
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Dockshift's backup file: readable JSON, one file with everything.
 *
 * The file states its own format and version. Newer app versions must keep reading
 * older files; fields added later are optional, so an older file simply lacks them
 * and gets the defaults. Shift types are written with the database's stable codes,
 * which never change.
 */
class JsonBackupFormat @Inject constructor() : BackupFormat {

    private val json = Json {
        prettyPrint = true
        // A newer app may add fields; an older one skips what it does not know
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun encode(contents: BackupContents): String = json.encodeToString(BackupFileJson.serializer(), contents.toJson())

    override fun decode(text: String): BackupContents {
        val file = try {
            json.decodeFromString(BackupFileJson.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupFormatException("Not a Dockshift backup", e)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("Not a Dockshift backup", e)
        }
        if (file.format != FORMAT) throw BackupFormatException("Not a Dockshift backup: format '${file.format}'")
        if (file.version > VERSION) throw BackupFormatException("Made by a newer version of the app (format ${file.version})")
        return try {
            file.toDomain()
        } catch (e: RuntimeException) {
            // Bad dates, unknown types or categories, rules broken by edited files
            throw BackupFormatException("Invalid backup contents: ${e.message}", e)
        }
    }

    companion object {
        /** What the "format" field of every Dockshift backup says. */
        const val FORMAT = "dockshift-backup"

        /** The file version this app writes; it reads this one and all older ones. */
        const val VERSION = 1
    }
}

/** The whole file. */
@Serializable
private data class BackupFileJson(
    val format: String,
    val version: Int,
    val createdAt: String,
    val appVersion: String = "",
    val machines: List<MachineJson> = emptyList(),
    val shifts: List<ShiftJson> = emptyList(),
    val leaves: List<LeaveJson> = emptyList(),
    val settings: SettingsJson = SettingsJson(),
)

/** One machine, with the identity the shifts refer to. */
@Serializable
private data class MachineJson(
    val id: Long,
    val category: String,
    val number: String,
    val active: Boolean = true,
    val location: String? = null,
)

/** One shift; its machines in the order they were driven. */
@Serializable
private data class ShiftJson(
    val date: String,
    val type: String,
    val overtime: Boolean = false,
    val comments: String? = null,
    val machines: List<AssignmentJson> = emptyList(),
)

/** One machine of a shift. */
@Serializable
private data class AssignmentJson(val machineId: Long, val duringOvertime: Boolean = false)

/** One planned leave. */
@Serializable
private data class LeaveJson(val start: String, val end: String, val note: String? = null)

/** The place in the rotation. */
@Serializable
private data class AnchorJson(val cycleStart: String, val firstType: String)

/**
 * Every setting, by enum name. All optional: a missing or unknown value gets the
 * default, so a file never fails over a setting.
 */
@Serializable
private data class SettingsJson(
    val themeMode: String? = null,
    val dynamicColor: Boolean? = null,
    val workSchedule: String? = null,
    val rotationAnchor: AnchorJson? = null,
    val rotationPromptAnswered: Boolean? = null,
    val calendarViewPortrait: String? = null,
    val calendarViewLandscape: String? = null,
    val showHolidays: Boolean? = null,
    val leaveEntitlements: Map<String, Int> = emptyMap(),
    val portClosedHolidays: List<String> = emptyList(),
    val backupReminder: Boolean? = null,
)

/** Domain → file. */
private fun BackupContents.toJson() = BackupFileJson(
    format = JsonBackupFormat.FORMAT,
    version = JsonBackupFormat.VERSION,
    createdAt = createdAt.toString(),
    appVersion = appVersion,
    machines = machines.map { MachineJson(it.id, it.category.name, it.number, it.active, it.location) },
    shifts = shifts.map { shift ->
        ShiftJson(
            date = shift.date.toString(),
            type = ShiftTypeCodes.toCode(shift.type),
            overtime = shift.overtime,
            comments = shift.comments,
            machines = shift.machines.map { AssignmentJson(it.machine.id, it.duringOvertime) },
        )
    },
    leaves = leaves.map { LeaveJson(it.start.toString(), it.end.toString(), it.note) },
    settings = with(settings) {
        SettingsJson(
            themeMode = themeMode.name,
            dynamicColor = dynamicColor,
            workSchedule = workSchedule.name,
            rotationAnchor = rotationAnchor?.let { AnchorJson(it.cycleStart.toString(), it.firstType.name) },
            rotationPromptAnswered = rotationPromptAnswered,
            calendarViewPortrait = calendarViewPortrait.name,
            calendarViewLandscape = calendarViewLandscape.name,
            showHolidays = showHolidays,
            leaveEntitlements = leaveEntitlements.mapKeys { it.key.toString() },
            portClosedHolidays = portClosedHolidays.map { it.name },
            backupReminder = backupReminder,
        )
    },
)

/** File → domain; throws on anything invalid (the caller turns it into [BackupFormatException]). */
private fun BackupFileJson.toDomain(): BackupContents {
    val machineById = machines.associate { json ->
        json.id to Machine(json.id, MachineCategory.valueOf(json.category), json.number, json.active, json.location)
    }
    return BackupContents(
        createdAt = LocalDateTime.parse(createdAt),
        appVersion = appVersion,
        machines = machineById.values.toList(),
        shifts = shifts.map { json ->
            Shift(
                date = LocalDate.parse(json.date),
                type = ShiftTypeCodes.fromCode(json.type),
                overtime = json.overtime,
                comments = json.comments,
                machines = json.machines.map { assignment ->
                    val machine = machineById[assignment.machineId]
                        ?: throw IllegalArgumentException("The shift of ${json.date} uses machine ${assignment.machineId}, missing from the file")
                    MachineAssignment(machine, assignment.duringOvertime)
                },
            )
        },
        // New identities: leaves are referred to by nothing else
        leaves = leaves.map { Leave(0, LocalDate.parse(it.start), LocalDate.parse(it.end), it.note) },
        settings = settings.toDomain(),
    )
}

/** Settings → domain; unknown or missing values get the defaults. */
private fun SettingsJson.toDomain(): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
        themeMode = ThemeMode.entries.firstOrNull { it.name == themeMode } ?: defaults.themeMode,
        dynamicColor = dynamicColor ?: defaults.dynamicColor,
        workSchedule = WorkSchedule.entries.firstOrNull { it.name == workSchedule } ?: defaults.workSchedule,
        rotationAnchor = rotationAnchor?.let { anchor ->
            runCatching { RotationAnchor(LocalDate.parse(anchor.cycleStart), ShiftType.valueOf(anchor.firstType)) }.getOrNull()
        },
        rotationPromptAnswered = rotationPromptAnswered ?: defaults.rotationPromptAnswered,
        // A restored phone has its data: the first-run "import from v1?" question is not asked
        legacyImportAsked = true,
        calendarViewPortrait = CalendarView.entries.firstOrNull { it.name == calendarViewPortrait } ?: defaults.calendarViewPortrait,
        calendarViewLandscape = CalendarView.entries.firstOrNull { it.name == calendarViewLandscape } ?: defaults.calendarViewLandscape,
        showHolidays = showHolidays ?: defaults.showHolidays,
        leaveEntitlements = leaveEntitlements.mapNotNull { (year, days) -> year.toIntOrNull()?.let { it to days } }.toMap(),
        portClosedHolidays = portClosedHolidays.mapNotNull { name -> GreekHoliday.entries.firstOrNull { it.name == name } }.toSet(),
        backupReminder = backupReminder ?: defaults.backupReminder,
    )
}
