package com.arn.aplacetosit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Dawn and Night, ported from `Shared/BrandColors.xcassets` — the same token
 * values, the same rule: one language at two times of day. Only the field
 * inverts and the ink follows it.
 */
data class Palette(
    val text: Color,
    val muted: Color,
    val patina: Color,
    val border: Color,
    val accent: Color,
    val field1: Color, val field2: Color, val field3: Color, val field4: Color, val field5: Color,
    val aperture: Color, val apertureMid: Color, val apertureOuter: Color,
)

val Dawn = Palette(
    text = Color(0xFF171320),
    muted = Color(0xC7171320),
    patina = Color(0xB3171320),
    border = Color(0x94171320),
    accent = Color(0xFF4C3670),
    field1 = Color(0xFFE4E1EC), field2 = Color(0xFFEFE5E8), field3 = Color(0xFFF8EADB),
    field4 = Color(0xFFF5E0CF), field5 = Color(0xFFE6D2C8),
    aperture = Color(0xFFFFCE82), apertureMid = Color(0xFFF6BA98), apertureOuter = Color(0xFFC4AAD6),
)

val Night = Palette(
    text = Color(0xFFFFFFFF),
    muted = Color(0xCCFFFFFF),
    patina = Color(0xBDFFFFFF),
    border = Color(0x7AFFFFFF),
    accent = Color(0xFFF7C89B),
    field1 = Color(0xFF07091F), field2 = Color(0xFF16123A), field3 = Color(0xFF2E1D55),
    field4 = Color(0xFF221845), field5 = Color(0xFF0A0A1E),
    aperture = Color(0xFFFFBE87), apertureMid = Color(0xFFCE84BA), apertureOuter = Color(0xFF6E54B4),
)

val LocalPalette = staticCompositionLocalOf { Night }

@Composable
fun PlaceToSitTheme(content: @Composable () -> Unit) {
    val palette = if (isSystemInDarkTheme()) Night else Dawn
    CompositionLocalProvider(LocalPalette provides palette, content = content)
}

/**
 * The Ganzfeld field: the vertical wash, the warm aperture, the vignette.
 * A simplification of the iOS `GanzfeldField` — same stops, same intent:
 * screens never paint their own opaque background.
 */
@Composable
fun GanzfeldField(peak: Float = 0.22f, centerY: Float = 0.45f, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to p.field1, 0.30f to p.field2, 0.52f to p.field3,
                    0.74f to p.field4, 1f to p.field5,
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.62f
            drawCircle(
                brush = Brush.radialGradient(
                    0f to p.aperture.copy(alpha = peak),
                    0.32f to p.apertureMid.copy(alpha = peak * 0.53f),
                    0.56f to p.apertureOuter.copy(alpha = peak * 0.2f),
                    0.72f to Color.Transparent,
                    center = Offset(size.width / 2f, size.height * centerY),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(size.width / 2f, size.height * centerY),
            )
        }
        content()
    }
}
