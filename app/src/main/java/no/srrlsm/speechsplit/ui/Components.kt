package no.srrlsm.speechsplit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.srrlsm.speechsplit.core.AppSettings
import no.srrlsm.speechsplit.core.EnglishStrings
import no.srrlsm.speechsplit.core.ReleaseInfo
import no.srrlsm.speechsplit.core.Strings
import no.srrlsm.speechsplit.core.TimeStatus
import no.srrlsm.speechsplit.core.formatDiff
import no.srrlsm.speechsplit.ui.theme.AppTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ============================================================================
// PLATFORM HOOKS FOR THE UI
// The screens only talk to the device through this interface. MainActivity provides the
// Android version; a Windows/iPad version would provide its own.
// ============================================================================
interface UiActions {
    fun share(text: String, chooserTitle: String)
    fun toast(text: String)
    /** False when the user turned animations off in the system's accessibility settings. */
    fun animationsEnabled(): Boolean
    fun hasNotificationPermission(): Boolean
    fun requestNotificationPermission(onResult: (Boolean) -> Unit)
    fun openDndSettings()
    /** Opens a web page in the browser. */
    fun openUrl(url: String)
    /** Gets the new version the easiest way this platform allows. */
    fun installUpdate(release: ReleaseInfo)
}

val LocalUiActions = staticCompositionLocalOf<UiActions> { error("UiActions not provided") }
val LocalStrings = staticCompositionLocalOf { EnglishStrings }
val LocalSettings = staticCompositionLocalOf { AppSettings() }

// AppBackHandler (system back button) is defined per platform:
// Android: platform/AndroidBack.kt. Desktop: desktop module.

// ============================================================================
// SHARED UI PIECES
// ============================================================================

/** Tabular (fixed-width) digits so the clock doesn't wobble as numbers change. */
val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** Colour for a status in the current light/dark palette. */
@Composable
@ReadOnlyComposable
fun TimeStatus.color(): Color = when (this) {
    TimeStatus.ON_TRACK -> AppTheme.colors.textMain
    TimeStatus.WARNING -> AppTheme.colors.warning
    TimeStatus.OVER -> AppTheme.colors.over
}

@Composable
fun DiffPill(diffSeconds: Int, fontSize: TextUnit, modifier: Modifier = Modifier, isWarning: Boolean = false) {
    val c = AppTheme.colors
    val (bg, fg) = when {
        diffSeconds > 0 -> c.over to c.onOver
        isWarning -> c.warning to c.onWarning
        else -> c.green to c.onGreen
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            text = formatDiff(diffSeconds),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            style = Tabular,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, size: TextUnit = 11.sp) {
    Text(text, fontSize = size, color = AppTheme.colors.textSub, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = modifier)
}

/**
 * One line of text that shrinks to fit instead of wrapping.
 * Used for button labels: in a thin split-screen pane, "START TIMER" used to wrap and
 * the second word got clipped away under the first. Now the whole label just gets smaller.
 */
@Composable
fun FitText(
    text: String,
    maxSize: TextUnit,
    modifier: Modifier = Modifier,
    minSize: TextUnit = 11.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = Color.Unspecified,
    style: TextStyle = TextStyle.Default,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(
            TextStyle(
                color = if (color != Color.Unspecified) color else LocalContentColor.current,
                fontWeight = fontWeight,
                textAlign = TextAlign.Center,
            )
        ),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = maxSize, stepSize = 0.5.sp),
    )
}

// ============================================================================
// CONFETTI
// ============================================================================
private class ConfettiPiece(
    val x: Float,          // start position, 0..1 of the width
    val y: Float,          // start position, 0..1 of the height (above the top edge)
    val vx: Float,         // sideways speed, fraction of width per second
    val vy: Float,         // falling speed, fraction of height per second
    val spin: Float,       // degrees per second
    val wobble: Float,     // sideways sway amount
    val phase: Float,
    val w: Float,          // size in dp
    val h: Float,
    val color: Color,
)

/**
 * Silent confetti that rains over the screen for a few seconds, then calls [onDone].
 * Drawn on a Canvas on top of everything; it doesn't block touches.
 */
@Composable
fun ConfettiOverlay(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors.confetti
    val ui = LocalUiActions.current
    val pieces = remember {
        List(140) {
            ConfettiPiece(
                x = Random.nextFloat(),
                y = -Random.nextFloat() * 0.6f - 0.05f,
                vx = (Random.nextFloat() - 0.5f) * 0.15f,
                vy = 0.28f + Random.nextFloat() * 0.3f,
                spin = (Random.nextFloat() - 0.5f) * 720f,
                wobble = 0.01f + Random.nextFloat() * 0.03f,
                phase = Random.nextFloat() * 6.28f,
                w = 6f + Random.nextFloat() * 6f,
                h = 10f + Random.nextFloat() * 8f,
                color = colors[it % colors.size],
            )
        }
    }
    var seconds by remember { mutableFloatStateOf(0f) }
    val durationSec = 4.5f
    val currentOnDone by rememberUpdatedState(onDone)

    LaunchedEffect(Unit) {
        if (!ui.animationsEnabled()) { currentOnDone(); return@LaunchedEffect }
        val start = withFrameNanos { it }
        while (seconds < durationSec) {
            withFrameNanos { seconds = (it - start) / 1_000_000_000f }
        }
        currentOnDone()
    }

    Canvas(modifier) {
        val t = seconds
        // Fade everything out over the last second
        val fade = (durationSec - t).coerceIn(0f, 1f)
        pieces.forEach { p ->
            val px = (p.x + p.vx * t + p.wobble * sin(p.phase + t * 4f)) * size.width
            val py = (p.y + p.vy * t) * size.height
            if (py < -40f || py > size.height + 40f) return@forEach
            val w = p.w.dp.toPx()
            // Pieces "flip" as they fall: their height pulses, like real paper confetti
            val h = p.h.dp.toPx() * (0.35f + 0.65f * abs(cos(p.phase + t * 5f)))
            rotate(degrees = p.spin * t, pivot = Offset(px, py)) {
                drawRect(
                    color = p.color,
                    topLeft = Offset(px - w / 2, py - h / 2),
                    size = Size(w, h),
                    alpha = fade,
                )
            }
        }
    }
}
