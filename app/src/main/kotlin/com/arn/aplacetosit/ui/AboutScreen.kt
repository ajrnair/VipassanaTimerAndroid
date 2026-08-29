package com.arn.aplacetosit.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * About: the privacy story stated plainly, the links, and the door back to
 * "How this works". The same words the iPhone app and the website use — one
 * voice across three surfaces.
 */
@Composable
fun AboutScreen(versionName: String, onHowThisWorks: () -> Unit, onBack: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current

    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    GanzfeldField {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())
                .padding(30.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("Done", color = p.patina, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onBack))
            }
            Spacer(Modifier.height(20.dp))
            Text("QUIET PRACTICE", color = p.accent, fontSize = 11.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "Private by\ndesign.", color = p.text, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 46.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "A free, open-source practice timer with no account, scores, streaks, " +
                    "advertising, analytics, or tracking.",
                color = p.muted, fontSize = 16.sp,
            )

            Spacer(Modifier.height(26.dp))
            Divider()
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onHowThisWorks).padding(vertical = 16.dp),
            ) { Text("How this works", color = p.text, fontSize = 16.sp) }
            Divider()

            Spacer(Modifier.height(26.dp))
            Text("YOUR PRACTICE STAYS YOURS", color = p.patina, fontSize = 11.sp, letterSpacing = 2.5.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "Your practice data is not collected. The timer works offline, and timer state, " +
                    "history, and private notes remain in app-owned storage on this device.",
                color = p.muted, fontSize = 15.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "The app requests no network access at all, so nothing can leave the phone.",
                color = p.muted, fontSize = 15.sp,
            )

            Spacer(Modifier.height(26.dp))
            Text("FREE & OPEN SOURCE", color = p.patina, fontSize = 11.sp, letterSpacing = 2.5.sp)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Read the privacy policy", color = p.text, fontSize = 15.sp,
                    modifier = Modifier.clickable { open("https://aplacetosit.in/privacy.html") },
                )
                Text(
                    "Source code", color = p.text, fontSize = 15.sp,
                    modifier = Modifier.clickable { open("https://github.com/ajrnair/VipassanaTimerAndroid") },
                )
                Text(
                    "MIT License", color = p.text, fontSize = 15.sp,
                    modifier = Modifier.clickable {
                        open("https://github.com/ajrnair/VipassanaTimerAndroid/blob/main/LICENSE")
                    },
                )
            }

            Spacer(Modifier.height(26.dp))
            Text("INDEPENDENT", color = p.patina, fontSize = 11.sp, letterSpacing = 2.5.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "An independent practice tool. Not affiliated with any meditation school, " +
                    "teacher, lineage, or teaching organisation, and not a substitute for instruction.",
                color = p.muted, fontSize = 15.sp,
            )

            Spacer(Modifier.height(30.dp))
            Text("Version $versionName", color = p.patina, fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Divider() {
    val p = LocalPalette.current
    Box(Modifier.height(1.dp).fillMaxWidth().background(p.border.copy(alpha = 0.5f)))
}
