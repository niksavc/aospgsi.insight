package nks.aospgsi.insight.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

class SettingsActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { AppTheme { SettingsScreen(onBack = { finish() }) } }
  }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
  val settings = AppSettings.get(LocalContext.current)
  ScreenFrame(stringResource(R.string.settings_title), onBack) { padding ->
    ScrollingPage(padding) {
      InfoCard(stringResource(R.string.settings_theme)) {
        androidx.compose.foundation.layout.Column(Modifier.selectableGroup()) {
          ThemeMode.entries.forEach { mode ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .selectable(
                    selected = settings.themeMode == mode,
                    onClick = { settings.updateThemeMode(mode) },
                    role = Role.RadioButton,
                  )
                  .padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(selected = settings.themeMode == mode, onClick = null)
              Text(
                stringResource(themeModeLabel(mode)),
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
              )
            }
          }
        }
      }
      InfoCard(stringResource(R.string.settings_colors)) {
        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            stringResource(R.string.settings_dynamic_color),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
          )
          Switch(
            checked = settings.dynamicColor && supported,
            onCheckedChange = { settings.updateDynamicColor(it) },
            enabled = supported,
          )
        }
        if (!supported) {
          Text(
            stringResource(R.string.settings_dynamic_color_unsupported),
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }
  }
}

private fun themeModeLabel(mode: ThemeMode): Int =
  when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
  }
