package com.arn.aplacetosit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arn.aplacetosit.core.DurationFormatter
import com.arn.aplacetosit.core.LogPresentation
import com.arn.aplacetosit.core.MeditationRecord
import com.arn.aplacetosit.core.MonthSection
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The log at years of scale, to the iOS branch's design: month eyebrows with
 * that month's sitting time, months before the current year collapsed to
 * their headers, and a calendar mode — dots only, no counts, no chains —
 * where tapping a practiced day returns to the list, opened to it.
 */
@Composable
fun LogScreen2(
    records: List<MeditationRecord>,
    onBack: () -> Unit,
    onOpen: (MeditationRecord) -> Unit,
) {
    val p = LocalPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val sections = remember(records) { LogPresentation.monthSections(records, zone) }
    var calendarMode by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var jumpDate by remember { mutableStateOf<LocalDate?>(null) }
    val currentYear = remember { LocalDate.now(zone).year }

    fun isOpen(section: MonthSection) =
        section.yearMonth.year == currentYear || section.id in expanded

    GanzfeldField(peak = 0.14f, centerY = 0.86f) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 30.dp)) {
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("PRACTICE", color = p.patina, fontSize = 11.sp, letterSpacing = 3.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (sections.isNotEmpty()) {
                        Text(
                            if (calendarMode) "List" else "Calendar",
                            color = p.patina, fontSize = 15.sp,
                            modifier = Modifier.clickable { calendarMode = !calendarMode },
                        )
                    }
                    Text("Sit", color = p.patina, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onBack))
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "Meditation\nlog", color = p.text, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 46.sp,
            )
            Spacer(Modifier.height(6.dp))

            if (sections.isEmpty()) {
                Spacer(Modifier.height(70.dp))
                Text(
                    "Your first completed sitting will appear here. Sessions stay on this device.",
                    color = p.patina, fontFamily = FontFamily.Serif, fontSize = 20.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            } else if (calendarMode) {
                CalendarBody(sections, zone) { date ->
                    jumpDate = date
                    expanded = expanded + sections
                        .filter { it.yearMonth == java.time.YearMonth.from(date) }
                        .map { it.id }
                    calendarMode = false
                }
            } else {
                LazyColumn {
                    sections.forEach { section ->
                        item(key = "h-" + section.id) {
                            MonthHeader(section, isOpen(section)) {
                                expanded = if (section.id in expanded) expanded - section.id
                                else expanded + section.id
                            }
                        }
                        if (isOpen(section)) {
                            section.records.forEach { record ->
                                item(key = record.id) {
                                    RecordRow(record, zone, highlight = jumpDate != null &&
                                        record.endedAtInstant.atZone(zone).toLocalDate() == jumpDate) {
                                        onOpen(record)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(section: MonthSection, open: Boolean, onToggle: () -> Unit) {
    val p = LocalPalette.current
    val currentYear = section.yearMonth.year == LocalDate.now().year
    Column(
        Modifier.fillMaxWidth()
            .then(if (currentYear) Modifier else Modifier.clickable(onClick = onToggle))
    ) {
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                section.title.uppercase(), color = p.patina,
                fontSize = 11.sp, letterSpacing = 2.5.sp,
            )
            Text(
                DurationFormatter.concise(section.totalMillis),
                color = p.patina, fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        if (!open) Box(Modifier.height(1.dp).fillMaxWidth().background(p.border.copy(alpha = 0.5f)))
    }
}

@Composable
private fun RecordRow(
    record: MeditationRecord,
    zone: ZoneId,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val ended = record.endedAtInstant.atZone(zone)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    ended.format(DateTimeFormatter.ofPattern("EEE d")),
                    color = p.text, fontFamily = FontFamily.Serif, fontSize = 19.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ended.format(DateTimeFormatter.ofPattern("h:mm a")),
                        color = p.patina, fontSize = 12.sp,
                    )
                    if (!record.note.isNullOrEmpty()) {
                        Spacer(Modifier.size(6.dp))
                        Box(Modifier.size(3.dp).background(p.patina, CircleShape))
                    }
                }
            }
            Text(
                DurationFormatter.concise(record.creditedMillis),
                color = if (highlight) p.text else p.accent, fontSize = 15.sp,
            )
        }
        Spacer(Modifier.height(11.dp))
        Box(Modifier.height(1.dp).fillMaxWidth().background(p.border.copy(alpha = 0.5f)))
    }
}

@Composable
private fun CalendarBody(
    sections: List<MonthSection>,
    zone: ZoneId,
    onDay: (LocalDate) -> Unit,
) {
    val p = LocalPalette.current
    val firstDay = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val symbols = remember {
        (0..6).map { firstDay.plus(it.toLong()).getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()) }
    }
    LazyColumn {
        sections.forEach { section ->
            item(key = "cal-" + section.id) {
                Column {
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(section.title.uppercase(), color = p.patina, fontSize = 11.sp, letterSpacing = 2.5.sp)
                        Text(DurationFormatter.concise(section.totalMillis), color = p.patina, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    val grid = LogPresentation.monthGrid(section.yearMonth, section.records, zone, firstDay)
                    Row(Modifier.fillMaxWidth()) {
                        symbols.forEach { s ->
                            Text(
                                s, color = p.patina, fontSize = 9.sp,
                                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    val cells: List<MonthGridCell> =
                        List(grid.leadingBlanks) { MonthGridCell.Blank } +
                            grid.days.map { MonthGridCell.Day(it) }
                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { cell ->
                                Box(Modifier.weight(1f).padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
                                    if (cell is MonthGridCell.Day) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = if (cell.day.practiced)
                                                Modifier.clickable { onDay(cell.day.date) } else Modifier,
                                        ) {
                                            Text("${cell.day.dayNumber}", color = p.muted, fontSize = 11.sp)
                                            Spacer(Modifier.height(3.dp))
                                            Box(
                                                Modifier.size(3.dp).background(
                                                    if (cell.day.practiced) p.patina else Color.Transparent,
                                                    CircleShape,
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

private sealed interface MonthGridCell {
    data object Blank : MonthGridCell
    data class Day(val day: com.arn.aplacetosit.core.MonthGrid.Day) : MonthGridCell
}
