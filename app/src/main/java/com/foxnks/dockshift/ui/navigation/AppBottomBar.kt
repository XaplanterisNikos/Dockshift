package com.foxnks.dockshift.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import com.foxnks.dockshift.R

/** The top-level screens reachable from the bottom bar, in bar order. */
enum class TopLevel(val key: NavKey, @StringRes val label: Int, @DrawableRes val icon: Int) {
    TODAY(TodayKey, R.string.nav_today, R.drawable.ic_today),
    SHIFTS(ShiftListKey, R.string.nav_shifts, R.drawable.ic_list),
    CALENDAR(CalendarKey, R.string.nav_calendar, R.drawable.ic_calendar_month),
    MENU(MenuKey, R.string.nav_menu, R.drawable.ic_menu),
}

/**
 * Bottom navigation bar of the top-level screens. Each of those screens draws it
 * in its own Scaffold, so system-bar insets are handled once, by that Scaffold.
 *
 * @param selected the screen currently shown.
 * @param onSelect called with the tapped screen.
 */
@Composable
fun AppBottomBar(selected: TopLevel, onSelect: (TopLevel) -> Unit) {
    NavigationBar {
        TopLevel.entries.forEach { item ->
            NavigationBarItem(
                selected = item == selected,
                onClick = { onSelect(item) },
                icon = { Icon(painterResource(item.icon), contentDescription = null) },
                label = { Text(stringResource(item.label)) },
            )
        }
    }
}
