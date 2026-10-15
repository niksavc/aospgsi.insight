package nks.aospgsi.insight.app

import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import java.text.Collator
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
  /** ApplicationInfo.category (API 26+); -1 when undefined or not available. */
  val category: Int,
  val updatedAt: Long,
)

private enum class SortMode(@StringRes val label: Int) {
  NAME(R.string.apps_sort_name),
  NAME_DESC(R.string.apps_sort_name_desc),
  TARGET_ASC(R.string.apps_sort_target_asc),
  UPDATED(R.string.apps_sort_updated);

  fun next(): SortMode = entries[(ordinal + 1) % entries.size]
}

/**
 * Orders rows by [mode]. Names compare ignoring case and accents, after dropping leading symbols
 * and spaces, so "(Test)" or " App" do not jump ahead of everything else.
 */
private fun sortRows(rows: List<InstalledRow>, mode: SortMode): List<InstalledRow> {
  val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
  fun key(row: InstalledRow) = row.label.trimStart { !it.isLetterOrDigit() }.ifEmpty { row.label }
  val byName = Comparator<InstalledRow> { a, b -> collator.compare(key(a), key(b)) }
  return when (mode) {
    SortMode.NAME -> rows.sortedWith(byName)
    SortMode.NAME_DESC -> rows.sortedWith(byName.reversed())
    SortMode.TARGET_ASC -> rows.sortedWith(compareBy<InstalledRow> { it.targetSdk }.then(byName))
    SortMode.UPDATED ->
      rows.sortedWith(compareByDescending<InstalledRow> { it.updatedAt }.then(byName))
  }
}

/** Labels for ApplicationInfo.CATEGORY_* values, by their documented integer values. */
@StringRes
private fun categoryLabel(category: Int): Int =
  when (category) {
    0 -> R.string.apps_category_game
    1 -> R.string.apps_category_audio
    2 -> R.string.apps_category_video
    3 -> R.string.apps_category_image
    4 -> R.string.apps_category_social
    5 -> R.string.apps_category_news
    6 -> R.string.apps_category_maps
    7 -> R.string.apps_category_productivity
    8 -> R.string.apps_category_accessibility
    else -> R.string.apps_category_none
  }

@Composable
private fun InstalledAppsScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  var rows by remember { mutableStateOf<List<InstalledRow>?>(null) }
  var query by rememberSaveable { mutableStateOf("") }
  var showSystem by rememberSaveable { mutableStateOf(false) }
  var onlyBelowFloor by rememberSaveable { mutableStateOf(false) }
  var selectedCategory by rememberSaveable { mutableStateOf<Int?>(null) }
  var sortMode by rememberSaveable { mutableStateOf(SortMode.NAME) }
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
              category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.category else -1,
              updatedAt = info.lastUpdateTime,
            )
          }
      }
  }

  ScreenFrame(stringResource(R.string.apps_title), onBack) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
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
        val inScope = all.filter { showSystem || !it.system }
        val categories =
          inScope.groupingBy { it.category.coerceAtLeast(-1) }.eachCount().toSortedMap()
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item {
            FilterChip(
              selected = selectedCategory == null,
              onClick = { selectedCategory = null },
              label = { Text(stringResource(R.string.apps_category_all) + " (${inScope.size})") },
            )
          }
          items(categories.entries.toList(), key = { it.key }) { (category, count) ->
            FilterChip(
              selected = selectedCategory == category,
              onClick = { selectedCategory = if (selectedCategory == category) null else category },
              label = { Text(stringResource(categoryLabel(category)) + " ($count)") },
            )
          }
        }
        val sorted = remember(all, sortMode) { sortRows(all, sortMode) }
        val visible = sorted.filter { row ->
          (selectedCategory == null || selectedCategory == row.category.coerceAtLeast(-1)) &&
            (showSystem || !row.system) &&
            (!onlyBelowFloor || (floor != null && row.targetSdk < floor)) &&
            (query.isBlank() ||
              row.label.contains(query.trim(), ignoreCase = true) ||
              row.packageName.contains(query.trim(), ignoreCase = true))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
          Text(
            stringResource(R.string.apps_count, visible.size),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = { sortMode = sortMode.next() }) {
            Text(stringResource(sortMode.label), style = MaterialTheme.typography.labelLarge)
          }
        }
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(6.dp),
          contentPadding = PaddingValues(bottom = 12.dp),
        ) {
          items(visible, key = { it.packageName }) { row ->
            AppRowCard(row, floor) {
              context.startActivity(AppDetailActivity.intent(context, row.packageName))
            }
          }
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
private fun AppRowCard(row: InstalledRow, floor: Int?, onOpen: () -> Unit) {
  Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      AppIcon(row.packageName)
      Column(Modifier.weight(1f)) {
        Text(row.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(row.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1)
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
}
