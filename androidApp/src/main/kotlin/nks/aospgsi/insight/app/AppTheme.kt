package nks.aospgsi.insight.app

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import nks.aospgsi.insight.app.ui.AospGsiTheme

enum class ThemeMode {
  SYSTEM,
  LIGHT,
  DARK,
}

/** User choices, kept in SharedPreferences and observable from Compose. */
class AppSettings private constructor(context: Context) {
  private val prefs =
    context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

  var themeMode by
    mutableStateOf(
      ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) }
        ?: ThemeMode.SYSTEM,
    )
    private set

  var dynamicColor by mutableStateOf(prefs.getBoolean(KEY_DYNAMIC, false))
    private set

  fun updateThemeMode(mode: ThemeMode) {
    themeMode = mode
    prefs.edit().putString(KEY_THEME, mode.name).apply()
  }

  fun updateDynamicColor(enabled: Boolean) {
    dynamicColor = enabled
    prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
  }

  companion object {
    private const val KEY_THEME = "theme_mode"
    private const val KEY_DYNAMIC = "dynamic_color"

    @Volatile private var instance: AppSettings? = null

    fun get(context: Context): AppSettings =
      instance ?: synchronized(this) { instance ?: AppSettings(context).also { instance = it } }
  }
}

/** The app theme with the user's light / dark / dynamic-color choices applied. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
  val settings = AppSettings.get(LocalContext.current)
  val dark =
    when (settings.themeMode) {
      ThemeMode.SYSTEM -> isSystemInDarkTheme()
      ThemeMode.LIGHT -> false
      ThemeMode.DARK -> true
    }
  AospGsiTheme(darkTheme = dark, dynamicColor = settings.dynamicColor, content = content)
}
