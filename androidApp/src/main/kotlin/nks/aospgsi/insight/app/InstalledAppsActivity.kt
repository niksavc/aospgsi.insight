package nks.aospgsi.insight.app

import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nks.aospgsi.insight.core.verification.InstallPolicy
import nks.aospgsi.insight.core.verification.ProprietaryGoogle

class InstalledAppsActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { AppTheme { InstalledAppsScreen(onBack = { finish() }) } }
  }
}

private data class InstalledRow(
  val label: String,
  val packageName: String,
  val versionName: String?,
  val targetSdk: Int,
  val system: Boolean,
)

@Composable
private fun InstalledAppsScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  var rows by remember { mutableStateOf<List<InstalledRow>?>(null) }
  var query by rememberSaveable { mutableStateOf("") }
  var showSystem by rememberSaveable { mutableStateOf(false) }
  var onlyBelowFloor by rememberSaveable { mutableStateOf(false) }
  val floor = remember { InstallPolicy.minimumInstallableTargetSdk(Build.VERSION.SDK_INT) }

  LaunchedEffect(Unit) {
    rows =
      withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm
          .getInstalledPackages(0)
          .mapNotNull { info ->
            val app = info.applicationInfo ?: return@mapNotNull null
            InstalledRow(
              label = app.loadLabel(pm).toString(),
              packageName = info.packageName,
              versionName = info.versionName,
              targetSdk = app.targetSdkVersion,
              system = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            )
          }
          .sortedBy { it.label.lowercase() }
      }
  }

  ScreenFrame(stringResource(R.string.apps_title), onBack) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.filter_hint)) },
      )
      SwitchRow(stringResource(R.string.apps_show_system), showSystem) { showSystem = it }
      if (floor != null) {
        SwitchRow(stringResource(R.string.apps_only_below_floor, floor), onlyBelowFloor) {
          onlyBelowFloor = it
        }
      }
      val all = rows
      if (all == null) {
        Text(stringResource(R.string.apps_loading))
      } else {
        val visible = all.filter { row ->
          (showSystem || !row.system) &&
            (!onlyBelowFloor || (floor != null && row.targetSdk < floor)) &&
            (query.isBlank() ||
              row.label.contains(query.trim(), ignoreCase = true) ||
              row.packageName.contains(query.trim(), ignoreCase = true))
        }
        Text(
          stringResource(R.string.apps_count, visible.size),
          style = MaterialTheme.typography.bodySmall,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(visible, key = { it.packageName }) { AppRowCard(it, floor) }
        }
      }
    }
  }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    Switch(checked = checked, onCheckedChange = onChange)
  }
}

@Composable
private fun AppRowCard(row: InstalledRow, floor: Int?) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(row.label, style = MaterialTheme.typography.titleMedium)
      Text(row.packageName, style = MaterialTheme.typography.bodySmall)
      Text(
        stringResource(R.string.apps_version_target, row.versionName ?: "?", row.targetSdk),
        style = MaterialTheme.typography.bodySmall,
      )
      if (floor != null && row.targetSdk < floor) {
        Text(
          stringResource(R.string.apps_below_floor, floor),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
      if (ProprietaryGoogle.classify(row.packageName) != null) {
        Text(
          stringResource(R.string.apps_google_namespace),
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}
