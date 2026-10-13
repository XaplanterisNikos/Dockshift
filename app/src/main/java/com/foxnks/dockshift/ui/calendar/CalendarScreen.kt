package com.foxnks.dockshift.ui.calendar

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.calendar.CalendarDay
import com.foxnks.dockshift.domain.calendar.DayStatus
import com.foxnks.dockshift.domain.settings.CalendarView
import com.foxnks.dockshift.ui.navigation.AppBottomBar
import com.foxnks.dockshift.ui.navigation.TopLevel
import com.foxnks.dockshift.ui.shift.ShiftFormat
import com.foxnks.dockshift.ui.theme.DockshiftTheme
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

/**
 * Entry point of the calendar.
 *
 * @param onEditDay open the shift of a day for adding or editing.
 * @param onOpenLeave open a planned leave (its id) for changing or deleting.
 * @param onSelectTopLevel switch screen from the bottom bar.
 * @param viewModel provided by Hilt, one per screen entry.
 */
@Composable
fun CalendarRoute(
    onEditDay: (LocalDate) -> Unit,
    onOpenLeave: (Long) -> Unit,
    onSelectTopLevel: (TopLevel) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    // Back from the background (maybe after midnight): move the "today" circle
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CalendarScreen(
        uiState = uiState,
        onMonthShown = viewModel::onMonthShown,
        onDaySelected = viewModel::onDaySelected,
        onToday = viewModel::onToday,
        onToggleView = viewModel::onToggleView,
        onRecordForecasts = viewModel::onRecordForecasts,
        onEditDay = onEditDay,
        onSelectTopLevel = onSelectTopLevel,
        onOpenLeave = onOpenLeave,
    )
}

/**
 * The month calendar, swiped sideways month by month.
 *
 * Upright, the panel (month summary or selected day) sits under the grid and the
 * whole screen scrolls; sideways, it sits beside the grid, which then fills the
 * height. The button in the top bar switches the panel of the current orientation.
 * With the summary, tapping a day opens it for editing; with the day details,
 * tapping selects it and the panel offers the edit button.
 *
 * @param uiState what to show.
 * @param onMonthShown the pager settled on a month.
 * @param onDaySelected a day was tapped in day-details mode.
 * @param onToday the "Today" button was pressed.
 * @param onToggleView the view button was pressed; true when in landscape.
 * @param onRecordForecasts the month's predicted days should be recorded (already confirmed).
 * @param onEditDay open a day for adding or editing.
 * @param onSelectTopLevel switch screen from the bottom bar.
 * @param onOpenLeave open a planned leave (its id): a leave day opens its leave, not the shift form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    onMonthShown: (YearMonth) -> Unit,
    onDaySelected: (LocalDate) -> Unit,
    onToday: () -> Unit,
    onToggleView: (Boolean) -> Unit,
    onRecordForecasts: () -> Unit,
    onEditDay: (LocalDate) -> Unit,
    onSelectTopLevel: (TopLevel) -> Unit,
    onOpenLeave: (Long) -> Unit = {},
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val showDetails = uiState.view(landscape) == CalendarView.DAY_DETAILS
    // Starts on the ViewModel's month, so a rotation or a return to the tab keeps the place
    val pagerState = rememberPagerState(initialPage = CalendarPages.pageOf(uiState.visibleMonth)) { CalendarPages.COUNT }
    val scope = rememberCoroutineScope()
    // The month in the title follows the swipe; the data follows only once it settles
    val pagerMonth = CalendarPages.monthOf(pagerState.currentPage)

    // Report the month the pager settled on, not every frame of a swipe
    val currentOnMonthShown by rememberUpdatedState(onMonthShown)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { currentOnMonthShown(CalendarPages.monthOf(it)) }
    }

    /** Swipes the pager to [month]. */
    fun scrollTo(month: YearMonth) {
        scope.launch { pagerState.animateScrollToPage(CalendarPages.pageOf(month)) }
    }

    val onDayClick: (CalendarDay) -> Unit = { day ->
        when {
            // A faded day: go to its month (and select it there in day-details mode)
            !day.inMonth -> {
                if (showDetails) onDaySelected(day.date)
                scrollTo(YearMonth.from(day.date))
            }
            showDetails -> onDaySelected(day.date)
            // A leave day cannot be worked: it opens its leave
            day.status is DayStatus.OnLeave -> onOpenLeave((day.status as DayStatus.OnLeave).leave.id)
            else -> onEditDay(day.date)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    MonthTitle(
                        month = pagerMonth,
                        onPrevious = { scrollTo(pagerMonth.minusMonths(1)) },
                        onNext = { scrollTo(pagerMonth.plusMonths(1)) },
                    )
                },
                actions = {
                    // Only useful away from the current month
                    if (pagerMonth != YearMonth.from(uiState.today)) {
                        TextButton(
                            onClick = {
                                onToday()
                                scrollTo(YearMonth.from(uiState.today))
                            },
                        ) { Text(stringResource(R.string.calendar_today)) }
                    }
                    IconButton(onClick = { onToggleView(landscape) }) {
                        // The icon shows what the button switches to
                        if (showDetails) {
                            Icon(painterResource(R.drawable.ic_view_summary), stringResource(R.string.calendar_show_summary))
                        } else {
                            Icon(painterResource(R.drawable.ic_view_day_details), stringResource(R.string.calendar_show_day_details))
                        }
                    }
                },
            )
        },
        bottomBar = { AppBottomBar(selected = TopLevel.CALENDAR, onSelect = onSelectTopLevel) },
    ) { innerPadding ->
        val clickLabel = stringResource(if (showDetails) R.string.calendar_cell_select else R.string.calendar_cell_open)
        val selectedDate = if (showDetails) uiState.selectedDate else null

        if (landscape) {
            // Grid on the left filling the height, panel on the right scrolling on its own
            Row(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CalendarPager(
                    pagerState = pagerState,
                    uiState = uiState,
                    selectedDate = selectedDate,
                    fillHeight = true,
                    clickLabel = clickLabel,
                    onDayClick = onDayClick,
                    modifier = Modifier.weight(1.4f).fillMaxHeight(),
                )
                Card(Modifier.weight(1f).fillMaxHeight()) {
                    CalendarPanel(
                        uiState = uiState,
                        showDetails = showDetails,
                        onEditDay = onEditDay,
                        onRecordForecasts = onRecordForecasts,
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                        onOpenLeave = onOpenLeave,
                    )
                }
            }
        } else {
            // Grid on top, panel underneath; the whole screen scrolls on short phones
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CalendarPager(
                    pagerState = pagerState,
                    uiState = uiState,
                    selectedDate = selectedDate,
                    fillHeight = false,
                    clickLabel = clickLabel,
                    onDayClick = onDayClick,
                )
                Card(Modifier.fillMaxWidth()) {
                    CalendarPanel(
                        uiState = uiState,
                        showDetails = showDetails,
                        onEditDay = onEditDay,
                        onRecordForecasts = onRecordForecasts,
                        modifier = Modifier.padding(16.dp),
                        onOpenLeave = onOpenLeave,
                    )
                }
            }
        }
    }
}

/** "‹ Οκτώβριος 2026 ›": the month on screen between the previous/next buttons. */
@Composable
private fun MonthTitle(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.calendar_previous_month))
        }
        Text(
            text = ShiftFormat.month(month.year, month.monthValue),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Shrinks (with "…") before pushing the next button off screen
            modifier = Modifier.weight(1f, fill = false),
        )
        IconButton(onClick = onNext) {
            Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.calendar_next_month))
        }
    }
}

/**
 * The week-day names and the swipeable month pages under them.
 *
 * @param pagerState the pager's position, shared with the top bar buttons.
 * @param uiState the loaded pages.
 * @param selectedDate the day to outline, or null.
 * @param fillHeight stretch the pages over the available height (landscape).
 * @param clickLabel what tapping a day does, for TalkBack.
 * @param onDayClick called with the tapped day.
 * @param modifier optional modifier from the caller.
 */
@Composable
private fun CalendarPager(
    pagerState: PagerState,
    uiState: CalendarUiState,
    selectedDate: LocalDate?,
    fillHeight: Boolean,
    clickLabel: String,
    onDayClick: (CalendarDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        WeekDayHeader()
        HorizontalPager(
            state = pagerState,
            modifier = if (fillHeight) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(),
            pageSpacing = 16.dp,
        ) { page ->
            MonthPage(
                page = uiState.page(CalendarPages.monthOf(page)),
                today = uiState.today,
                selectedDate = selectedDate,
                fillHeight = fillHeight,
                clickLabel = clickLabel,
                onDayClick = onDayClick,
                modifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The panel next to the grid: the month summary or the selected day. */
@Composable
private fun CalendarPanel(
    uiState: CalendarUiState,
    showDetails: Boolean,
    onEditDay: (LocalDate) -> Unit,
    onRecordForecasts: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLeave: (Long) -> Unit = {},
) {
    val visible = uiState.page(uiState.visibleMonth)
    if (showDetails) {
        DayDetailsPanel(day = visible.day(uiState.selectedDate), onEditDay = onEditDay, modifier = modifier, onOpenLeave = onOpenLeave)
    } else {
        MonthSummaryPanel(summary = visible.summary, modifier = modifier, onRecordForecasts = onRecordForecasts)
    }
}

/** A sample state for previews: October 2026 with shifts and forecasts. */
private fun previewState(view: CalendarView): CalendarUiState {
    val month = previewMonth()
    return CalendarUiState(
        today = LocalDate.of(2026, 10, 13),
        visibleMonth = month.month,
        months = mapOf(month.month to month),
        selectedDate = LocalDate.of(2026, 10, 6),
        viewPortrait = view,
        viewLandscape = view,
    )
}

/** Android Studio preview of the calendar with the month summary; not part of the running app. */
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun CalendarSummaryPreview() {
    DockshiftTheme(darkTheme = false, dynamicColor = false) {
        CalendarScreen(previewState(CalendarView.SUMMARY), {}, {}, {}, {}, {}, {}, {})
    }
}

/** Android Studio preview of the calendar with the selected day, in dark mode. */
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun CalendarDetailsDarkPreview() {
    DockshiftTheme(darkTheme = true, dynamicColor = false) {
        CalendarScreen(previewState(CalendarView.DAY_DETAILS), {}, {}, {}, {}, {}, {}, {})
    }
}
