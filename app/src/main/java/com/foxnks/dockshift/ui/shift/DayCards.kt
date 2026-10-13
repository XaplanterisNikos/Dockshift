package com.foxnks.dockshift.ui.shift

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.holidays.GreekHoliday
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.shift.MachineAssignment
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.ui.theme.DockshiftTheme
import java.time.LocalDate

/** Corner radius of every day card. */
private val CARD_SHAPE = RoundedCornerShape(12.dp)

/**
 * The colors of one day card, decided once from what the day is.
 *
 * @property card background of the whole card.
 * @property border outline of the card, or null.
 * @property tile background of the date tile (portrait card) / whole mini card.
 * @property onTile text drawn on [tile].
 * @property accent color of the type name (gold for a forecast).
 */
@Immutable
private data class DayCardColors(
    val card: Color,
    val border: BorderStroke?,
    val tile: Color,
    val onTile: Color,
    val accent: Color,
)

/** Picks the colors: shift color when recorded, gold when predicted, neutral when empty. */
@Composable
private fun dayCardColors(shift: Shift?, forecast: ShiftType?): DayCardColors {
    val shiftColors = DockshiftTheme.shiftColors
    val scheme = MaterialTheme.colorScheme
    return when {
        shift != null -> DayCardColors(
            card = scheme.surfaceContainerLow,
            border = BorderStroke(0.5.dp, scheme.outlineVariant),
            tile = shiftColors.container(shift.type),
            onTile = shiftColors.onContainer(shift.type),
            accent = scheme.onSurface,
        )
        forecast != null -> DayCardColors(
            card = shiftColors.forecastContainer,
            border = BorderStroke(1.5.dp, shiftColors.forecast),
            tile = shiftColors.forecastContainer,
            onTile = shiftColors.forecast,
            accent = shiftColors.forecast,
        )
        else -> DayCardColors(
            card = Color.Transparent,
            border = BorderStroke(1.dp, scheme.outlineVariant),
            tile = scheme.surfaceContainerHigh,
            onTile = scheme.onSurfaceVariant,
            accent = scheme.onSurfaceVariant,
        )
    }
}

/**
 * A small day card for lists in portrait: a colored date tile on the left
 * (week day and day number), the type and machines in the middle, a "+4" badge on
 * the right for overtime. A public holiday adds a red line; an empty day is a
 * faint outline with a "+" to add it; a predicted day is gold with sparkles.
 *
 * @param date the day.
 * @param shift its stored shift, or null.
 * @param onClick called on tap (opens the day for adding or editing).
 * @param modifier optional modifier from the caller.
 * @param holiday its public holiday, or null.
 * @param forecast the type predicted by the rotating schedule when nothing is stored,
 * or null (nothing predicts until the rotating schedule arrives).
 */
@Composable
fun DayCard(
    date: LocalDate,
    shift: Shift?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    holiday: GreekHoliday? = null,
    forecast: ShiftType? = null,
) {
    val colors = dayCardColors(shift, forecast)
    Surface(onClick = onClick, modifier = modifier, shape = CARD_SHAPE, color = colors.card, border = colors.border) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DateTile(date, colors, isForecast = shift == null && forecast != null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DayTitle(shift, forecast, colors.accent)
                // Machines of the shift, e.g. "RTG 12 · QC 31"
                val machines = shift?.machines.orEmpty()
                if (machines.isNotEmpty()) {
                    Text(
                        text = machines.joinToString(" · ") { it.machine.label },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (shift == null && forecast != null) {
                    Text(stringResource(R.string.calendar_forecast), style = MaterialTheme.typography.bodySmall, color = colors.accent)
                }
                holiday?.let {
                    Text(
                        text = it.label(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                shift?.overtime == true -> OvertimeBadge()
                // An empty day invites to add it
                shift == null && forecast == null -> Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
    }
}

/**
 * A mini day card for horizontal rows in landscape: week day, day number, the
 * type's short name and "+4" stacked in the shift's color. A public holiday shows
 * a red dot (and a red number when the card is not colored).
 *
 * @param date the day.
 * @param shift its stored shift, or null.
 * @param onClick called on tap (opens the day for adding or editing).
 * @param modifier width from the caller (all cards of a row share it).
 * @param holiday its public holiday, or null.
 * @param forecast the predicted type when nothing is stored, or null.
 */
@Composable
fun MiniDayCard(
    date: LocalDate,
    shift: Shift?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    holiday: GreekHoliday? = null,
    forecast: ShiftType? = null,
) {
    val colors = dayCardColors(shift, forecast)
    // The whole mini card takes the tile color, so there is no separate card background
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CARD_SHAPE,
        color = if (shift != null) colors.tile else colors.card,
        contentColor = colors.onTile,
        border = if (shift != null) null else colors.border,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(ShiftFormat.weekday(date), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                // Red on a neutral card; on a shift color the dot below marks the holiday instead
                color = if (holiday != null && shift == null) MaterialTheme.colorScheme.error else colors.onTile,
            )
            // Holiday dot, or the same space, so all cards of a row line up
            Box(
                Modifier
                    .padding(vertical = 2.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (holiday != null) MaterialTheme.colorScheme.error else Color.Transparent),
            )
            val type = shift?.type ?: forecast
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (shift == null && forecast != null) {
                    Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null, modifier = Modifier.size(10.dp))
                }
                Text(
                    text = type?.abbreviation() ?: "+",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Text(
                // Keeps the height even when there is no overtime
                text = if (shift?.overtime == true) stringResource(R.string.calendar_overtime_mark) else " ",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Week day over day number, on the day's color; gold-outlined for a forecast. */
@Composable
private fun DateTile(date: LocalDate, colors: DayCardColors, isForecast: Boolean) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .width(48.dp)
            .clip(shape)
            .background(colors.tile)
            .then(if (isForecast) Modifier.border(1.5.dp, colors.onTile, shape) else Modifier)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(ShiftFormat.weekday(date), style = MaterialTheme.typography.labelSmall, color = colors.onTile, maxLines = 1)
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.onTile,
        )
    }
}

/** The card's main line: the type, "✦ type" for a forecast, or "Not recorded". */
@Composable
private fun DayTitle(shift: Shift?, forecast: ShiftType?, color: Color) {
    when {
        shift != null -> Text(shift.type.label(), style = MaterialTheme.typography.titleSmall, color = color)
        forecast != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Text(forecast.label(), style = MaterialTheme.typography.titleSmall, color = color)
        }
        else -> Text(stringResource(R.string.day_not_recorded), style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** "+4" in a small tinted pill: the shift had overtime. */
@Composable
private fun OvertimeBadge() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text = stringResource(R.string.calendar_overtime_mark),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Android Studio preview of both card styles; not part of the running app. */
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DayCardsPreview() {
    val rtg = Machine(1, MachineCategory.RTG, "12")
    val qc = Machine(2, MachineCategory.QC, "31")
    val day = LocalDate.of(2026, 10, 27)
    val shift = Shift(day, ShiftType.AFTERNOON, overtime = true, machines = listOf(MachineAssignment(rtg), MachineAssignment(qc)))
    DockshiftTheme(darkTheme = false, dynamicColor = false) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DayCard(day, shift, onClick = {})
            DayCard(day.plusDays(1), Shift(day.plusDays(1), ShiftType.DAY_OFF), onClick = {}, holiday = GreekHoliday.OHI_DAY)
            DayCard(day.plusDays(2), null, onClick = {}, forecast = ShiftType.MORNING)
            DayCard(day.plusDays(3), null, onClick = {})
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniDayCard(day, shift, onClick = {}, modifier = Modifier.width(64.dp))
                MiniDayCard(day.plusDays(1), Shift(day.plusDays(1), ShiftType.DAY_OFF), onClick = {}, modifier = Modifier.width(64.dp), holiday = GreekHoliday.OHI_DAY)
                MiniDayCard(day.plusDays(2), null, onClick = {}, modifier = Modifier.width(64.dp), forecast = ShiftType.MORNING)
                MiniDayCard(day.plusDays(3), null, onClick = {}, modifier = Modifier.width(64.dp))
            }
        }
    }
}
