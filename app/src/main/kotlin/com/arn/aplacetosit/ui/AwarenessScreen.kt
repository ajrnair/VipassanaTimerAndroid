package com.arn.aplacetosit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arn.aplacetosit.core.AwarenessScheduler

/**
 * Awareness setup, to the iOS branch's approved design: hours in the circle,
 * one interval row where Random is the last chip — an answer, not a mode —
 * and its one-line caption in reserved space so Begin holds still.
 */
@Composable
fun AwarenessScreen(
    onBegin: (hours: Int, intervalMinutes: Int?) -> Unit,
    onBack: () -> Unit,
) {
    val p = LocalPalette.current
    var hours by remember { mutableStateOf(8) }
    var intervalMinutes by remember { mutableStateOf<Int?>(10) } // null = Random
    val intervalChoices = listOf(1, 2, 5, 10, 15, 30, 60)

    GanzfeldField {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(30.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("AWARENESS MODE", color = p.patina, fontSize = 11.sp, letterSpacing = 3.sp)
                Text("Sit", color = p.patina, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onBack))
            }
            Spacer(Modifier.height(26.dp))
            Text(
                "Always be\naware.", color = p.text, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 46.sp,
            )
            Spacer(Modifier.weight(0.5f))

            Box(
                Modifier.size(180.dp).align(Alignment.CenterHorizontally)
                    .border(1.dp, p.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$hours", color = p.text, fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Light, fontSize = 64.sp,
                    )
                    Text("HOURS", color = p.patina, fontSize = 11.sp, letterSpacing = 3.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleGlyph("−") { if (hours > 1) hours -= 1 }
                Text("1 to 24 hours", color = p.patina, fontSize = 13.sp)
                CircleGlyph("+") { if (hours < 24) hours += 1 }
            }

            Spacer(Modifier.weight(0.7f))
            Text("GONG INTERVAL · MINUTES", color = p.patina, fontSize = 11.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                intervalChoices.forEach { minutes ->
                    Chip("$minutes", intervalMinutes == minutes, p.text) { intervalMinutes = minutes }
                }
                // Random is one more answer to "how often?" — accent-underlined,
                // the quiet mark that the app places the gongs.
                Chip("Random", intervalMinutes == null, p.accent) { intervalMinutes = null }
            }
            // Reserved either way, so Begin never moves between the two states.
            Text(
                if (intervalMinutes == null) randomCaption(hours) else " ",
                color = p.patina, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(Modifier.weight(0.7f))
            Box(Modifier.align(Alignment.CenterHorizontally)) {
                CapsuleLabel("Begin Awareness") { onBegin(hours, intervalMinutes) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun randomCaption(hours: Int): String {
    val (minimum, maximum) = AwarenessScheduler.randomBounds(hours * 3_600_000L)
    return "${minimum / 60_000} to ${maximum / 60_000} minutes apart, at random."
}

@Composable
private fun Chip(label: String, selected: Boolean, selectedColor: Color, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = if (selected) selectedColor else p.patina, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.height(1.dp).fillMaxWidth()
                .background(if (selected) selectedColor else Color.Transparent)
        )
    }
}

@Composable
private fun CircleGlyph(glyph: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(40.dp).border(1.dp, p.border, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Light) }
}

@Composable
private fun CapsuleLabel(label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .border(1.dp, p.border, androidx.compose.foundation.shape.RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 40.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = p.text, fontSize = 17.sp) }
}
