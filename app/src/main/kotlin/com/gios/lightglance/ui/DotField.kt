package com.gios.lightglance.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gios.lightglance.notif.Dot
import com.gios.lightglance.notif.Glyph
import com.gios.lightglance.notif.MAX_SLOTS
import com.gios.lightglance.ui.theme.Dim
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import java.util.Date

private val CELL = 38.dp
private val GLYPH = 15.dp

/**
 * OLED burn-in is the one failure mode here that is permanent, so the whole field
 * walks around an eight-position ring. Twelve dp is enough to spread the load and
 * small enough that you never notice it moved.
 */
private val JITTER = listOf(
    Offset(0f, 0f), Offset(9f, -6f), Offset(-8f, -10f), Offset(11f, 7f),
    Offset(-11f, 4f), Offset(5f, 11f), Offset(-5f, -4f), Offset(8f, -11f),
)

@Composable
fun DotField(
    dots: List<Dot>,
    showClock: Boolean,
    modifier: Modifier = Modifier,
    jitterMillis: Long = 45_000L,
) {
    var jitterIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(jitterMillis) {
        while (true) {
            delay(jitterMillis)
            jitterIndex = (jitterIndex + 1) % JITTER.size
        }
    }
    val jitter = JITTER[jitterIndex]

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(x = jitter.x.dp, y = jitter.y.dp),
        ) {
            if (showClock) {
                Clock()
                Spacer(Modifier.height(30.dp))
            }
            SlotRow(dots)
        }
    }
}

@Composable
private fun Clock() {
    val ctx = LocalContext.current
    val pattern = if (DateFormat.is24HourFormat(ctx)) "H:mm" else "h:mm"
    var now by remember { mutableStateOf(DateFormat.format(pattern, Date()).toString()) }
    LaunchedEffect(pattern) {
        while (true) {
            now = DateFormat.format(pattern, Date()).toString()
            // Align to the next minute boundary so the digit changes when it should.
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    // Deliberately grey, not white. The clock is the largest lit area on the panel
    // and it is the part you are least likely to be squinting at.
    Text(now, style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = Dim)
}

/**
 * Every slot is rendered whether or not it is occupied. If empty slots collapsed, the
 * dot you learned to read by position would slide sideways depending on what else
 * happened to be pending, which defeats the entire identification scheme.
 */
@Composable
private fun SlotRow(dots: List<Dot>) {
    val bySlot = dots.associateBy { it.slot }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.Center) {
        for (i in 0 until MAX_SLOTS) {
            val dot = bySlot[i]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(CELL),
            ) {
                Box(
                    modifier = Modifier.size(GLYPH).drawBehind {
                        if (dot != null) drawGlyph(dot.glyph, Color.White)
                    },
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = if (dot != null && dot.count > 1) dot.count.toString() else " ",
                    fontSize = 11.sp,
                    color = Dim,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val s = size.minDimension
    val stroke = s * 0.18f
    when (glyph) {
        Glyph.DOT -> drawCircle(color, radius = s / 2f)

        Glyph.RING -> drawCircle(
            color, radius = s / 2f - stroke / 2f, style = Stroke(width = stroke),
        )

        // A square of equal width reads noticeably heavier than a circle, so inset it.
        Glyph.SQUARE -> {
            val a = s * 0.84f
            val o = (s - a) / 2f
            drawRect(color, topLeft = Offset(o, o), size = Size(a, a))
        }

        Glyph.FRAME -> {
            val a = s * 0.84f
            val o = (s - a) / 2f
            drawRect(
                color,
                topLeft = Offset(o + stroke / 2f, o + stroke / 2f),
                size = Size(a - stroke, a - stroke),
                style = Stroke(width = stroke),
            )
        }

        Glyph.BAR -> drawRect(
            color,
            topLeft = Offset(0f, s / 2f - stroke / 2f),
            size = Size(s, stroke),
        )

        Glyph.TRIANGLE -> {
            val p = Path().apply {
                moveTo(s / 2f, s * 0.08f)
                lineTo(s * 0.94f, s * 0.88f)
                lineTo(s * 0.06f, s * 0.88f)
                close()
            }
            drawPath(p, color)
        }
    }
}
