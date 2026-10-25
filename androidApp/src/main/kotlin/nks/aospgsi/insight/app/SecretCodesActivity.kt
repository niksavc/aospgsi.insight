package nks.aospgsi.insight.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Every dialer secret code that the installed apps declare, found by reading their manifests. The
 * idea is from the open-source Secret Codes app by Simon Marquis (Apache-2.0); this is new code.
 */
class SecretCodesActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { AppTheme { SecretCodesScreen(onBack = { finish() }) } }
  }
}

@Composable
private fun SecretCodesScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  var apps by remember { mutableStateOf<List<AppCodes>?>(null) }
  var failed by remember { mutableStateOf<String?>(null) }
  var scanned by remember { mutableIntStateOf(0) }
  var total by remember { mutableIntStateOf(0) }
  var query by rememberSaveable { mutableStateOf("") }
  var showSystem by rememberSaveable { mutableStateOf(true) }

  LaunchedEffect(Unit) {
    try {
      apps =
        scanInstalledApps(context) { done, count ->
          scanned = done
          total = count
        }
    } catch (e: Exception) {
      failed = e.message ?: e.javaClass.simpleName
    }
  }

  ScreenFrame(stringResource(R.string.secret_codes_title), onBack) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.secret_codes_filter)) },
      )
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
          stringResource(R.string.apps_show_system),
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.bodyMedium,
        )
        Switch(checked = showSystem, onCheckedChange = { showSystem = it })
      }
      Text(stringResource(R.string.secret_codes_page_note), style = MaterialTheme.typography.bodySmall)

      val found = apps
      when {
        failed != null ->
          Text(
            stringResource(R.string.app_detail_failed, failed.orEmpty()),
            color = MaterialTheme.colorScheme.error,
          )
        found == null ->
          Text(stringResource(R.string.secret_codes_scanning, scanned, maxOf(total, scanned)))
        else -> {
          val needle = query.trim()
          val visible = found.filter { app ->
            (showSystem || !app.system) &&
              (needle.isEmpty() ||
                app.label.contains(needle, ignoreCase = true) ||
                app.packageName.contains(needle, ignoreCase = true) ||
                app.codes.any { it.code.contains(needle, ignoreCase = true) })
          }
          Text(
            stringResource(
              R.string.secret_codes_count,
              visible.sumOf { it.codes.size },
              visible.size,
              total,
            ),
            style = MaterialTheme.typography.labelLarge,
          )
          if (visible.isEmpty()) Text(stringResource(R.string.none_found))
          LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
          ) {
            items(visible, key = { it.packageName }) { app -> AppCodesCard(app) }
          }
        }
      }
    }
  }
}

@Composable
private fun AppCodesCard(app: AppCodes) {
  val context = LocalContext.current
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Card(
        onClick = { context.startActivity(AppDetailActivity.intent(context, app.packageName)) },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          AppIcon(app.packageName)
          Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1)
          }
        }
      }
      app.codes.forEach { code -> SecretCodeRow(code) }
    }
  }
}
