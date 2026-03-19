package dev.setsuna.impakviewer.ui.theme


import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBg        = Color(0xFF22222A)
val DarkSidebar   = Color(0xFF1E1E26)
val DarkCanvas    = Color(0xFF0F0F13)
val DarkPatchBg   = Color(0xFF1A1A22)
val DarkBorder    = Color(0xFF2E2E3A)
val DarkThumbHov  = Color(0xFF2E2E3A)

val Accent        = Color(0xFFDF6F8E)
val AccentHover   = Color(0xFFFF8FAE)
val AccentMuted   = Color(0xFF6A4158)

val TextPrimary   = Color(0xFFE8E6F0)
val TextMuted     = Color(0xFF8A889A)
val TextDim       = Color(0xFF6A6878)

val Success       = Color(0xFF4CAF88)
val Warning       = Color(0xFFE8A44A)
val Danger        = Color(0xFFE85555)

val LightBg       = Color(0xFFE4E3EC)
val LightSidebar  = Color(0xFFEBEBF3)
val LightCanvas   = Color(0xFFD8D7E0)
val LightPatchBg  = Color(0xFFDDDCE8)
val LightBorder   = Color(0xFFC8C7D4)
val LightThumbHov = Color(0xFFD4D3E0)

val AccentLight       = Color(0xFFC0365A)
val AccentHoverLight  = Color(0xFFE0507A)
val AccentMutedLight  = Color(0xFFE8B4C0)

val TextPrimaryLight  = Color(0xFF1A1820)
val TextMutedLight    = Color(0xFF5A5870)
val TextDimLight      = Color(0xFF8A8898)

val SuccessLight  = Color(0xFF1E8A5A)
val WarningLight  = Color(0xFFB06820)
val DangerLight   = Color(0xFFB02020)

// ── Material3 schemes ─────────────────────────────────────────────────────────
private val DarkColors = darkColorScheme(
    background   = DarkBg,
    surface      = DarkBg,
    surfaceVariant = DarkSidebar,
    primary      = Accent,
    onPrimary    = TextPrimary,
    onBackground = TextPrimary,
    onSurface    = TextPrimary,
    outline      = DarkBorder,
    error        = Danger,
)

private val LightColors = lightColorScheme(
    background   = LightBg,
    surface      = LightBg,
    surfaceVariant = LightSidebar,
    primary      = AccentLight,
    onPrimary    = TextPrimaryLight,
    onBackground = TextPrimaryLight,
    onSurface    = TextPrimaryLight,
    outline      = LightBorder,
    error        = DangerLight,
)

@Composable
fun ImpakViewerTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
