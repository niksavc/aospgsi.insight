package nks.aospgsi.insight.app

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.util.Locale

class SettingsActivity : AppActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { AppTheme { SettingsScreen(onBack = { finish() }) } }
  }
}

private enum class Section {
  THEME,
  COLORS,
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val settings = AppSettings.get(context)
  // One section is open at a time, like the settings of the app this layout follows.
  var open by rememberSaveable { mutableStateOf<Section?>(Section.THEME) }
  var languageSheet by rememberSaveable { mutableStateOf(false) }

  ScreenFrame(stringResource(R.string.settings_title), onBack) { padding ->
    ScrollingPage(padding) {
      SettingsSection(
        title = stringResource(R.string.settings_theme),
        expanded = open == Section.THEME,
        onToggle = { open = if (open == Section.THEME) null else Section.THEME },
      ) {
        Column(Modifier.selectableGroup()) {
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
              Text(
                stringResource(themeModeLabel(mode)),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
              )
              RadioButton(selected = settings.themeMode == mode, onClick = null)
            }
          }
        }
      }
      SettingsSection(
        title = stringResource(R.string.settings_colors),
        expanded = open == Section.COLORS,
        onToggle = { open = if (open == Section.COLORS) null else Section.COLORS },
      ) {
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
      Card(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth().clickable { languageSheet = true }.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text(
              stringResource(R.string.settings_language),
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              languageName(AppLanguage.current(context)),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
          Chevron(Direction.RIGHT)
        }
      }
      SigningKeyCard()
    }
  }

  if (languageSheet) {
    LanguageSheet(
      selected = AppLanguage.current(context),
      onSelect = { choice ->
        languageSheet = false
        if (choice != AppLanguage.current(context)) {
          AppLanguage.apply(context as Activity, choice)
        }
      },
      onDismiss = { languageSheet = false },
    )
  }
}

/** A card whose header toggles its content, in the manner of the screenshots this follows. */
@Composable
private fun SettingsSection(
  title: String,
  expanded: Boolean,
  onToggle: () -> Unit,
  content: @Composable () -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Chevron(if (expanded) Direction.UP else Direction.DOWN)
      }
      if (expanded) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          content()
        }
      }
    }
  }
}

private enum class Direction {
  UP,
  DOWN,
  RIGHT,
}

/** A small arrow head, drawn so that no icon font or library is needed. */
@Composable
private fun Chevron(direction: Direction) {
  val color = MaterialTheme.colorScheme.onSurfaceVariant
  Canvas(Modifier.size(20.dp)) {
    val w = size.width
    val h = size.height
    val path =
      Path().apply {
        when (direction) {
          Direction.DOWN -> {
            moveTo(w * 0.15f, h * 0.35f)
            lineTo(w * 0.5f, h * 0.7f)
            lineTo(w * 0.85f, h * 0.35f)
          }
          Direction.UP -> {
            moveTo(w * 0.15f, h * 0.65f)
            lineTo(w * 0.5f, h * 0.3f)
            lineTo(w * 0.85f, h * 0.65f)
          }
          Direction.RIGHT -> {
            moveTo(w * 0.35f, h * 0.15f)
            lineTo(w * 0.7f, h * 0.5f)
            lineTo(w * 0.35f, h * 0.85f)
          }
        }
      }
    drawPath(
      path,
      color,
      style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
  }
}

/** The language's own name, and for a named language also its name in the current language. */
@Composable
private fun languageName(choice: AppLanguageChoice): String =
  if (choice == AppLanguageChoice.SYSTEM) {
    stringResource(R.string.language_default)
  } else {
    val locale = Locale.forLanguageTag(choice.tag)
    locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
  }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(
  selected: AppLanguageChoice,
  onSelect: (AppLanguageChoice) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp).selectableGroup()) {
      AppLanguageChoice.entries.forEach { choice ->
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .selectable(
                selected = choice == selected,
                onClick = { onSelect(choice) },
                role = Role.RadioButton,
              )
              .padding(horizontal = 24.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          RadioButton(selected = choice == selected, onClick = null)
          Column {
            Text(languageName(choice), style = MaterialTheme.typography.titleMedium)
            if (
              choice == AppLanguageChoice.SYSTEM &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
              Text(
                stringResource(R.string.language_restart_note),
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
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
    ThemeMode.BLACK -> R.string.theme_black
  }
