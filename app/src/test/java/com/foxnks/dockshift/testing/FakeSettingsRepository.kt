package com.foxnks.dockshift.testing

import com.foxnks.dockshift.domain.holidays.GreekHoliday
import com.foxnks.dockshift.domain.rotation.RotationAnchor
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.settings.CalendarView
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.settings.ThemeMode
import com.foxnks.dockshift.domain.settings.WorkSchedule
import com.foxnks.dockshift.domain.worker.WorkerProfile
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [SettingsRepository] for tests: no files, no Android, fully controllable.
 *
 * @param initial the settings the fake starts with.
 */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    /** The latest settings, readable directly so tests can assert what was saved. */
    val current: AppSettings get() = state.value

    override val settings: Flow<AppSettings> = state.asStateFlow()

    override suspend fun setWorkSchedule(schedule: WorkSchedule) {
        state.update { it.copy(workSchedule = schedule) }
    }

    override suspend fun setRotationAnchor(anchor: RotationAnchor?) {
        state.update { it.copy(rotationAnchor = anchor) }
    }

    override suspend fun setRotationPromptAnswered() {
        state.update { it.copy(rotationPromptAnswered = true) }
    }

    override suspend fun setLegacyImportAsked() {
        state.update { it.copy(legacyImportAsked = true) }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.update { it.copy(themeMode = mode) }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        state.update { it.copy(dynamicColor = enabled) }
    }

    override suspend fun setCalendarView(landscape: Boolean, view: CalendarView) {
        state.update { if (landscape) it.copy(calendarViewLandscape = view) else it.copy(calendarViewPortrait = view) }
    }

    override suspend fun setShowHolidays(enabled: Boolean) {
        state.update { it.copy(showHolidays = enabled) }
    }

    override suspend fun setLeaveEntitlement(year: Int, days: Int) {
        state.update { it.copy(leaveEntitlements = it.leaveEntitlements + (year to days)) }
    }

    override suspend fun setPortClosedHolidays(holidays: Set<GreekHoliday>) {
        state.update { it.copy(portClosedHolidays = holidays) }
    }

    override suspend fun setLastBackup(date: LocalDate) {
        state.update { it.copy(lastBackup = date) }
    }

    override suspend fun setBackupReminder(enabled: Boolean) {
        state.update { it.copy(backupReminder = enabled) }
    }

    override suspend fun setWorker(worker: WorkerProfile) {
        // The stored id never changes, as in the real store
        state.update { it.copy(worker = worker.copy(id = it.worker.id)) }
    }

    override suspend fun ensureWorkerId(id: String) {
        state.update { if (it.worker.id.isBlank()) it.copy(worker = it.worker.copy(id = id)) else it }
    }

    override suspend fun setWorkerPromptAsked() {
        state.update { it.copy(workerPromptAsked = true) }
    }

    override suspend fun restore(settings: AppSettings) {
        state.value = settings
    }
}
