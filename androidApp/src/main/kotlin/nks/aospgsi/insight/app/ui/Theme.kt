package nks.aospgsi.insight.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Brand indigo. Works as the accent on both a white and a pure black background. */
private val Indigo = Color(0xFF3535D0)
private val IndigoLight = Color(0xFFB9B8FF)

private val LightColors =
  lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E0FF),
    onPrimaryContainer = Color(0xFF0A0A6B),
    secondary = Color(0xFF5B5B7D),
    tertiary = Color(0xFF7A4B6E),
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFEEEEF4),
    surfaceContainer = Color(0xFFF3F3F8),
  )

/** Dark scheme: near-black surfaces with a little lift between them. */
private val DarkColors =
  darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF1B1B8F),
    primaryContainer = Color(0xFF2323A8),
    onPrimaryContainer = Color(0xFFE2E0FF),
    secondary = Color(0xFFC4C3E8),
    tertiary = Color(0xFFE9B9D8),
    background = Color(0xFF121217),
    surface = Color(0xFF121217),
    surfaceVariant = Color(0xFF2B2B33),
    surfaceContainer = Color(0xFF1D1D24),
  )

/** The dark scheme on true black: saves power on OLED screens and keeps cards readable. */
private val BlackColors =
  DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF212126),
    surfaceContainer = Color(0xFF16161B),
  )

/**
 * App theme: indigo accent, white / dark / true-black surfaces. Dynamic (wallpaper) colors are off
 * by default so the brand colors stay stable, and available on Android 12+.
 */
@Composable
fun AospGsiTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  /** With [darkTheme]: true black surfaces instead of dark gray. */
  black: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colors =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        when {
          darkTheme && black ->
            dynamicDarkColorScheme(context)
              .copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainer = Color(0xFF16161B),
              )
          darkTheme -> dynamicDarkColorScheme(context)
          else -> dynamicLightColorScheme(context)
        }
      }
      darkTheme && black -> BlackColors
      darkTheme -> DarkColors
      else -> LightColors
    }
  MaterialTheme(colorScheme = colors, content = content)
}
