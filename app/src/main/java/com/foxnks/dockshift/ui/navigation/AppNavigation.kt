package com.foxnks.dockshift.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.foxnks.dockshift.ui.backup.BackupRoute
import com.foxnks.dockshift.ui.calendar.CalendarRoute
import com.foxnks.dockshift.ui.edit.EditShiftRoute
import com.foxnks.dockshift.ui.leave.EditLeaveRoute
import com.foxnks.dockshift.ui.leave.LeavesRoute
import com.foxnks.dockshift.ui.legacy.LegacyFirstRunPrompt
import com.foxnks.dockshift.ui.list.ShiftListRoute
import com.foxnks.dockshift.ui.machines.MachinesRoute
import com.foxnks.dockshift.ui.menu.MenuRoute
import com.foxnks.dockshift.ui.rotation.RotationPrompt
import com.foxnks.dockshift.ui.settings.SettingsRoute
import com.foxnks.dockshift.ui.statistics.StatisticsRoute
import com.foxnks.dockshift.ui.today.TodayRoute
import com.foxnks.dockshift.ui.vessels.VesselsRoute
import com.foxnks.dockshift.ui.worker.WorkerPrompt
import com.foxnks.dockshift.ui.worker.WorkerRoute
import java.time.LocalDate

/**
 * The app's navigation: one back stack of screen keys, shown by Navigation 3.
 *
 * - "Today" is always at the bottom of the stack, so back from any screen ends there.
 * - The bottom bar switches between "Today", "Shifts", "Calendar", "Vessels" and "Menu".
 * - Edit (shift or leave) is pushed on top of any screen; Machines, Statistics, Leave,
 *   Data & backup, Worker and Settings on top of the menu (Data & backup also from Today's
 *   reminder, Worker also from the first-run question).
 *   They are popped when closed, so back from them returns to where they were opened.
 * Each entry gets its own ViewModel store, so opening the edit screen for two
 * different days gives two independent ViewModels.
 */
@Composable
fun AppNavigation() {
    // Saved across rotation and process death (the keys are @Serializable)
    val backStack = rememberNavBackStack(TodayKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            // Keeps each screen's saved UI state (scroll positions, open sheets...)
            rememberSaveableStateHolderNavEntryDecorator(),
            // One ViewModel per screen entry, cleared when the entry is popped
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<TodayKey> {
                TodayRoute(
                    onEditDay = { backStack.openEdit(it) },
                    onOpenLeave = { backStack.openOnce(EditLeaveKey(it)) },
                    onOpenBackup = { backStack.openOnce(BackupKey) },
                    onOpenVessels = { backStack.selectTopLevel(TopLevel.VESSELS) },
                    onSelectTopLevel = { backStack.selectTopLevel(it) },
                )
            }
            entry<ShiftListKey> {
                ShiftListRoute(
                    onEditDay = { backStack.openEdit(it) },
                    onSelectTopLevel = { backStack.selectTopLevel(it) },
                    newShiftDate = { LocalDate.now() },
                )
            }
            entry<CalendarKey> {
                CalendarRoute(
                    onEditDay = { backStack.openEdit(it) },
                    onOpenLeave = { backStack.openOnce(EditLeaveKey(it)) },
                    onSelectTopLevel = { backStack.selectTopLevel(it) },
                )
            }
            entry<EditShiftKey> { key ->
                EditShiftRoute(date = LocalDate.parse(key.date), onDone = { backStack.removeLastOrNull() })
            }
            entry<VesselsKey> {
                VesselsRoute(onSelectTopLevel = { backStack.selectTopLevel(it) })
            }
            entry<MenuKey> {
                MenuRoute(
                    onOpen = { backStack.openOnce(it) },
                    onSelectTopLevel = { backStack.selectTopLevel(it) },
                )
            }
            entry<MachinesKey> {
                MachinesRoute(onBack = { backStack.removeLastOrNull() })
            }
            entry<StatisticsKey> {
                StatisticsRoute(onBack = { backStack.removeLastOrNull() })
            }
            entry<LeavesKey> {
                LeavesRoute(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenLeave = { id, day -> backStack.openOnce(EditLeaveKey(id, day?.toString())) },
                )
            }
            entry<EditLeaveKey> { key ->
                EditLeaveRoute(id = key.id, day = key.day?.let { LocalDate.parse(it) }, onDone = { backStack.removeLastOrNull() })
            }
            entry<BackupKey> {
                BackupRoute(onBack = { backStack.removeLastOrNull() })
            }
            entry<WorkerKey> {
                WorkerRoute(onDone = { backStack.removeLastOrNull() })
            }
            entry<SettingsKey> {
                SettingsRoute(onBack = { backStack.removeLastOrNull() })
            }
        },
    )

    // First-run "data from the first version?" question, over whatever screen is shown
    LegacyFirstRunPrompt()
    // First-run "fill in your profile?" question (after the one above)
    WorkerPrompt(onFillIn = { backStack.openOnce(WorkerKey) })
    // One-time "your shifts match the rotating schedule" question (after both above)
    RotationPrompt()
}

/** Opens the add/edit screen for [date], unless it is already on top (double tap). */
private fun NavBackStack<NavKey>.openEdit(date: LocalDate) {
    val key = EditShiftKey(date.toString())
    if (lastOrNull() != key) add(key)
}

/** Opens [key] on top, unless it is already there (a quick double tap opens it once). */
private fun NavBackStack<NavKey>.openOnce(key: NavKey) {
    if (lastOrNull() != key) add(key)
}

/**
 * Bottom-bar navigation: the stack becomes just "Today", or "Today" plus the
 * chosen top-level screen, so back always returns to "Today" first.
 */
private fun NavBackStack<NavKey>.selectTopLevel(item: TopLevel) {
    if (lastOrNull() == item.key) return
    // Never empty the stack: "Today" (index 0) always stays
    while (size > 1) removeLastOrNull()
    if (item.key != TodayKey) add(item.key)
}
