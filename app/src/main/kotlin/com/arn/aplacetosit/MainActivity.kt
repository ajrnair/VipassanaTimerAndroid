package com.arn.aplacetosit

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arn.aplacetosit.audio.SittingService
import com.arn.aplacetosit.core.ActiveSession
import com.arn.aplacetosit.core.DurationFormatter
import com.arn.aplacetosit.core.MeditationRecord
import com.arn.aplacetosit.core.SessionClock
import com.arn.aplacetosit.core.SessionPhase
import com.arn.aplacetosit.core.TimerEngine
import com.arn.aplacetosit.data.AppStore
import com.arn.aplacetosit.ui.AwarenessScreen
import com.arn.aplacetosit.ui.AboutScreen
import com.arn.aplacetosit.ui.GanzfeldField
import com.arn.aplacetosit.ui.LogScreen2
import com.arn.aplacetosit.ui.LocalPalette
import com.arn.aplacetosit.ui.PlaceToSitTheme
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * V1, deliberately small: silent and guided sittings, a log with private
 * notes, one gate that says how it works. The same promises as the iPhone
 * app's first release, in the same visual language, sharing its audio
 * programs and its record schema byte for byte.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = AppStore(this)
        setContent {
            PlaceToSitTheme { AppRoot(store) }
        }
    }
}

private fun now() = SessionClock(System.currentTimeMillis(), SystemClock.elapsedRealtime())

@Composable
fun AppRoot(store: AppStore) {
    var session by remember { mutableStateOf(store.restoreActive()) }
    var records by remember { mutableStateOf(store.loadRecords()) }
    var route by remember { mutableStateOf("home") }
    var seenGate by remember {
        mutableStateOf(store.loadRecords().isNotEmpty() || store.restoreActive() != null)
    }

    fun end(completedAutomatically: Boolean) {
        val s = session ?: return
        SittingService.Companion.stop(storeContext())
        val credited = TimerEngine.creditedDuration(s, now())
        if (credited >= 60_000) {
            store.save(
                MeditationRecord.forSession(s, credited, System.currentTimeMillis(), completedAutomatically)
            )
        }
        store.persistActive(null)
        session = null
        records = store.loadRecords()
    }

    LaunchedEffect(Unit) {
        SittingService.onNaturalEnd = { end(completedAutomatically = true) }
    }

    var showsGate by remember { mutableStateOf(false) }
    val active = session
    when {
        !seenGate -> HowThisWorks { seenGate = true }
        showsGate -> HowThisWorks { showsGate = false }
        route == "about" -> AboutScreen(
            versionName = "0.1.0",
            onHowThisWorks = { showsGate = true },
            onBack = { route = "home" },
        )
        active != null -> SessionScreen(active, onEnd = { end(false) })
        route == "log" -> {
            var editing by remember { mutableStateOf<MeditationRecord?>(null) }
            LogScreen2(records, onBack = { route = "home" }, onOpen = { editing = it })
            editing?.let { record ->
                NoteEditor(
                    record,
                    onDismiss = { editing = null },
                    onDelete = {
                        store.delete(record.id)
                        editing = null
                        records = store.loadRecords()
                    },
                ) { note, minutes ->
                    store.save(
                        record.copy(
                            note = note.ifBlank { null },
                            creditedDurationSeconds = minutes * 60.0,
                            modifiedAt = MeditationRecord.iso(System.currentTimeMillis()),
                        )
                    )
                    editing = null
                    records = store.loadRecords()
                }
            }
        }
        route == "aware" -> AwarenessScreen(
            onBegin = { hours, interval ->
                val s = if (interval == null) {
                    TimerEngine.startAwarenessRandom(hours, now(), Random)
                } else {
                    TimerEngine.startAwarenessFixed(hours, interval, now())
                }
                store.persistActive(s)
                SittingService.Companion.start(storeContext(), s)
                session = s
            },
            onBack = { route = "home" },
        )
        else -> HomeScreen(
            onBegin = { minutes, guided ->
                val s = TimerEngine.startStandard(minutes, now(), guidedMinutes = if (guided) minutes else null)
                store.persistActive(s)
                SittingService.Companion.start(storeContext(), s)
                session = s
            },
            onLog = { route = "log" },
            onAware = { route = "aware" },
            onHelp = { showsGate = true },
            onAbout = { route = "about" },
        )
    }
}

private lateinit var appContext: android.content.Context
private fun storeContext(): android.content.Context = appContext

class App : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
    }
}

// MARK: shared pieces

@Composable
fun Eyebrow(text: String) {
    val p = LocalPalette.current
    Text(text, color = p.patina, fontSize = 11.sp, letterSpacing = 3.sp)
}

@Composable
fun SerifTitle(text: String) {
    val p = LocalPalette.current
    Text(
        text, color = p.text, fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 46.sp,
    )
}

@Composable
fun CapsuleButton(label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .border(1.dp, p.border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 40.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = p.text, fontSize = 17.sp) }
}

@Composable
fun UnderlinedChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = if (selected) p.text else p.patina, fontSize = 17.sp, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.height(1.dp).fillMaxWidth()
                .background(if (selected) p.text else androidx.compose.ui.graphics.Color.Transparent)
        )
    }
}

// MARK: screens

@Composable
fun HomeScreen(
    onBegin: (Int, Boolean) -> Unit,
    onLog: () -> Unit,
    onAware: () -> Unit,
    onHelp: () -> Unit,
    onAbout: () -> Unit,
) {
    val p = LocalPalette.current
    var minutes by remember { mutableStateOf(45) }
    var guided by remember { mutableStateOf(false) }
    GanzfeldField {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(30.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Eyebrow("VIPASSANA TIMER")
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Aware", color = p.patina, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onAware))
                    Text("Log", color = p.patina, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onLog))
                    Text("?", color = p.patina, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onHelp))
                    Text("i", color = p.patina, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onAbout))
                }
            }
            Spacer(Modifier.height(26.dp))
            SerifTitle("A place\nto sit.")
            Spacer(Modifier.height(12.dp))
            Text(
                if (guided) "Gongs and minimal voice guidance." else "One gong to begin. Three to finish.",
                color = p.muted, fontSize = 17.sp,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                UnderlinedChoice("Silent", !guided) { guided = false }
                UnderlinedChoice("Guided", guided) { guided = true; if (minutes == 120) minutes = 45 }
            }
            Spacer(Modifier.weight(0.6f))
            Box(
                Modifier.size(200.dp).align(Alignment.CenterHorizontally)
                    .border(1.dp, p.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$minutes", color = p.text, fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Light, fontSize = 76.sp,
                    )
                    Eyebrow("MINUTES")
                }
            }
            Spacer(Modifier.weight(0.5f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val presets = if (guided) listOf(15, 30, 45, 60) else listOf(15, 30, 45, 60, 120)
                presets.forEach { m ->
                    UnderlinedChoice("$m", minutes == m) { minutes = m }
                }
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.align(Alignment.CenterHorizontally)) {
                CapsuleButton("Begin") { onBegin(minutes, guided) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionScreen(session: ActiveSession, onEnd: () -> Unit) {
    val p = LocalPalette.current
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(session.id) {
        while (true) { tick = SystemClock.elapsedRealtime(); delay(500) }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    val snapshot = TimerEngine.snapshot(session, now())
    GanzfeldField(peak = 0.18f, centerY = 0.47f) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.7f))
            Eyebrow(
                when {
                    snapshot.phase == SessionPhase.PREPARING -> "PREPARING"
                    session.mode == com.arn.aplacetosit.core.SessionMode.AWARENESS -> "AWARENESS MODE"
                    else -> "MEDITATION"
                }
            )
            Spacer(Modifier.height(24.dp))
            Text(
                DurationFormatter.countdown(snapshot.remainingMillis),
                color = p.text, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Light, fontSize = 64.sp,
            )
            Eyebrow("REMAINING")
            if (session.mode == com.arn.aplacetosit.core.SessionMode.AWARENESS) {
                Spacer(Modifier.height(20.dp))
                Text(
                    if (session.gongOffsetsMillis != null) "At random"
                    else "Every ${'$'}{(session.intervalMillis ?: 0) / 60_000} minutes",
                    color = p.muted, fontSize = 15.sp,
                )
                Eyebrow("GONGS")
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .border(1.dp, p.border, RoundedCornerShape(50))
                    .combinedClickable(onClick = {}, onLongClick = onEnd)
                    .padding(horizontal = 40.dp, vertical = 14.dp),
            ) { Text("End Session", color = p.text, fontSize = 17.sp) }
            Spacer(Modifier.height(8.dp))
            Text("Press and hold to end", color = p.patina, fontSize = 12.sp)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun HowThisWorks(onBegin: () -> Unit) {
    val p = LocalPalette.current
    GanzfeldField(peak = 0.2f, centerY = 0.38f) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(30.dp)) {
            Spacer(Modifier.height(28.dp))
            SerifTitle("How this\nworks.")
            Spacer(Modifier.height(12.dp))
            Text("A privacy-first timer for Vipassana practice.", color = p.muted, fontSize = 16.sp)
            Spacer(Modifier.height(18.dp))
            listOf(
                "SILENT" to "One gong to begin, three to end. Nothing in between.",
                "GUIDED" to "The same sitting, with minimal spoken Vipassana guidance.",
                "AWARE" to "Gongs through your day, up to 24 hours — at your interval, or at moments you can't predict.",
                "LOG" to "Every sitting is saved on this phone; open one to leave a note.",
            ).forEach { (name, line) ->
                Column(Modifier.padding(vertical = 11.dp)) {
                    Text(name, color = p.accent, fontSize = 11.sp, letterSpacing = 3.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(line, color = p.muted, fontSize = 15.sp)
                    Spacer(Modifier.height(11.dp))
                    Box(Modifier.height(1.dp).fillMaxWidth().background(p.border.copy(alpha = 0.5f)))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Gongs play with the phone locked. Nothing leaves this phone.",
                color = p.patina, fontSize = 13.sp,
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.align(Alignment.CenterHorizontally)) { CapsuleButton("Begin", onBegin) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun NoteEditor(
    record: MeditationRecord,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    val p = LocalPalette.current
    var note by remember { mutableStateOf(record.note ?: "") }
    var minutes by remember { mutableStateOf((record.creditedMillis / 60_000).toInt().coerceAtLeast(1)) }
    var confirmingDelete by remember { mutableStateOf(false) }
    com.arn.aplacetosit.ui.GanzfeldField(peak = 0.22f, centerY = 0.45f) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(30.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SerifTitle("Edit\nsession")
                Text("Cancel", color = p.patina, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onDismiss))
            }
            Spacer(Modifier.height(28.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Duration", color = p.text, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier.size(38.dp).border(1.dp, p.border, CircleShape)
                            .clickable { if (minutes > 1) minutes -= 1 },
                        contentAlignment = Alignment.Center,
                    ) { Text("−", color = p.text, fontSize = 17.sp) }
                    Text(
                        "$minutes min", color = p.text, fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Light, fontSize = 21.sp,
                    )
                    Box(
                        Modifier.size(38.dp).border(1.dp, p.border, CircleShape)
                            .clickable { if (minutes < 1440) minutes += 1 },
                        contentAlignment = Alignment.Center,
                    ) { Text("+", color = p.text, fontSize = 17.sp) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.height(1.dp).fillMaxWidth().background(p.border.copy(alpha = 0.5f)))
            Spacer(Modifier.height(26.dp))
            Eyebrow("NOTE")
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = note,
                onValueChange = { note = it },
                textStyle = TextStyle(color = p.text, fontSize = 16.sp),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth().height(130.dp),
            )
            Box(Modifier.height(1.dp).fillMaxWidth().background(p.border.copy(alpha = 0.5f)))
            Spacer(Modifier.weight(1f))
            Box(Modifier.align(Alignment.CenterHorizontally)) { CapsuleButton("Save") { onSave(note.trim(), minutes) } }
            Spacer(Modifier.height(14.dp))
            Text(
                if (confirmingDelete) "Tap again to delete this session" else "Delete this session",
                color = if (confirmingDelete) p.accent else p.patina, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable {
                    if (confirmingDelete) onDelete() else confirmingDelete = true
                },
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}
