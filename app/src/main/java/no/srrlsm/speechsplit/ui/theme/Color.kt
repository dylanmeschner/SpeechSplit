package no.srrlsm.speechsplit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Speech Split palette.
 *
 * The UI itself is calm and neutral (soft off-white or soft charcoal, never pure white/black),
 * so the only strong colours on screen are the ones that MEAN something:
 * green = under time, amber = warning, red = over time, blue = adjusted time.
 */
@Immutable
data class AppColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val textMain: Color,
    val textSub: Color,
    val divider: Color,
    /** Main buttons and selected chips. Neutral ink, so it never competes with the status colours. */
    val accent: Color,
    val onAccent: Color,
    val green: Color,
    val onGreen: Color,
    val warning: Color,
    val onWarning: Color,
    val over: Color,
    val onOver: Color,
    val adjusted: Color,
    /** Confetti colours. */
    val confetti: List<Color>,
)

val LightAppColors = AppColors(
    isDark = false,
    bg = Color(0xFFF3F4F6),
    surface = Color(0xFFFBFBFC),
    surfaceHigh = Color(0xFFE9EBEE),
    textMain = Color(0xFF1F2328),
    textSub = Color(0xFF5F6670),
    divider = Color(0xFFDADDE2),
    accent = Color(0xFF2F3B48),
    onAccent = Color(0xFFFFFFFF),
    green = Color(0xFF2E7D4F),
    onGreen = Color(0xFFFFFFFF),
    warning = Color(0xFFB26A00),
    onWarning = Color(0xFFFFFFFF),
    over = Color(0xFFC0392B),
    onOver = Color(0xFFFFFFFF),
    adjusted = Color(0xFF2F6FB0),
    confetti = listOf(
        Color(0xFF2E7D4F), Color(0xFF2F6FB0), Color(0xFFE0A100),
        Color(0xFFD9573F), Color(0xFF7A5BC7), Color(0xFF1E9AA0),
    ),
)

val DarkAppColors = AppColors(
    isDark = true,
    bg = Color(0xFF131517),
    surface = Color(0xFF1D2023),
    surfaceHigh = Color(0xFF272B2F),
    textMain = Color(0xFFE4E6E8),
    textSub = Color(0xFF979DA4),
    divider = Color(0xFF363B40),
    accent = Color(0xFFD5DBE1),
    onAccent = Color(0xFF15181B),
    green = Color(0xFF6CCB8B),
    onGreen = Color(0xFF0E2416),
    warning = Color(0xFFF2C14E),
    onWarning = Color(0xFF2A1F00),
    over = Color(0xFFEF7064),
    onOver = Color(0xFF2B0B07),
    adjusted = Color(0xFF8AB4F8),
    confetti = listOf(
        Color(0xFF6CCB8B), Color(0xFF8AB4F8), Color(0xFFF2C14E),
        Color(0xFFEF7064), Color(0xFFB39DFF), Color(0xFF5FD1C9),
    ),
)
