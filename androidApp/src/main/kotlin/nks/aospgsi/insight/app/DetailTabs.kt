package nks.aospgsi.insight.app

import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import nks.aospgsi.insight.core.apkinspector.LibraryCatalog
import nks.aospgsi.insight.core.apkinspector.SdkCatalog
import nks.aospgsi.insight.core.model.ApkComponent
import nks.aospgsi.insight.core.model.ApkInspection

/** One line in a detail tab. [group] becomes a header above the rows that share it. */
private data class DetailLabels(
  val exported: String,
  val dangerous: String,
  val signature: String,
  val normal: String,
  val unknown: String,
  val declared: String,
  val granted: String,
  val notGranted: String,
)

private const val PAGE = 50

private data class DetailRow(
  val title: String,
  val subtitle: String? = null,
  /** Short name of the SDK or library the row belongs to, shown as a badge. */
  val badge: String? = null,
  /** True for components that the manifest disables; drawn struck through. */
  val struck: Boolean = false,
  val group: String? = null,
)

private enum class DetailTab(@StringRes val label: Int) {
  NATIVE_LIBS(R.string.tab_native_libs),
  SERVICES(R.string.tab_services),
  ACTIVITIES(R.string.tab_activities),
  RECEIVERS(R.string.tab_receivers),
  PROVIDERS(R.string.tab_providers),
  PERMISSIONS(R.string.tab_permissions),
  METADATA(R.string.tab_metadata),
}

/**
 * Tabbed detail view in the manner of LibChecker's app screen: one tab per kind of manifest entry,
 * an item count, an optional A–Z sort, an SDK badge on rows we recognize, components grouped by
 * process, disabled components struck through, and permissions grouped by protection level.
 */
@Composable
internal fun DetailTabsCard(
  apk: ApkInspection,
  /** For an installed app: the permissions it holds. Null for an APK file (nothing is granted). */
  granted: Set<String>? = null,
) {
  val context = LocalContext.current
  var selected by rememberSaveable { mutableIntStateOf(0) }
  var sorted by rememberSaveable { mutableStateOf(false) }
  var shown by rememberSaveable(selected, sorted) { mutableIntStateOf(PAGE) }

  val labels =
    DetailLabels(
      exported = stringResource(R.string.exported_marker),
      dangerous = stringResource(R.string.perm_group_dangerous),
      signature = stringResource(R.string.perm_group_signature),
      normal = stringResource(R.string.perm_group_normal),
      unknown = stringResource(R.string.perm_group_unknown),
      declared = stringResource(R.string.perm_group_declared),
      granted = stringResource(R.string.perm_group_granted),
      notGranted = stringResource(R.string.perm_group_not_granted),
    )
  val rowsByTab =
    remember(apk, labels, granted) {
      val pm = context.packageManager
      val pkg = apk.packageName.orEmpty()
      DetailTab.entries.associateWith { tab ->
        when (tab) {
          DetailTab.NATIVE_LIBS -> nativeRows(apk)
          DetailTab.SERVICES -> componentRows(apk.services, pkg, labels)
          DetailTab.ACTIVITIES -> componentRows(apk.activities, pkg, labels)
          DetailTab.RECEIVERS -> componentRows(apk.receivers, pkg, labels)
          DetailTab.PROVIDERS -> componentRows(apk.providers, pkg, labels)
          DetailTab.PERMISSIONS -> permissionRows(pm, apk, labels, granted)
          DetailTab.METADATA ->
            apk.metaData.map { (key, value) -> DetailRow(title = key, subtitle = value) }
        }
      }
    }

  val tab = DetailTab.entries[selected.coerceIn(0, DetailTab.entries.lastIndex)]
  val rows = rowsByTab.getValue(tab)
  val ordered =
    if (sorted) {
      rows.groupBy { it.group }.values.flatMap { group -> group.sortedBy { it.title.lowercase() } }
    } else {
      rows
    }

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(DetailTab.entries) { index, entry ->
          FilterChip(
            selected = index == selected,
            onClick = { selected = index },
            label = {
              Text(stringResource(entry.label) + " (${rowsByTab.getValue(entry).size})")
            },
          )
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
          stringResource(R.string.items_count, rows.size),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { sorted = !sorted }) {
          Text(stringResource(if (sorted) R.string.sort_az_on else R.string.sort_az_off))
        }
      }
      if (tab == DetailTab.PERMISSIONS) {
        Text(
          stringResource(R.string.permissions_note),
          style = MaterialTheme.typography.bodySmall,
        )
      }
      if (rows.isEmpty()) Text(stringResource(R.string.none_found))

      var lastGroup: String? = null
      ordered.take(shown).forEach { row ->
        if (row.group != null && row.group != lastGroup) {
          Text(
            row.group,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
        lastGroup = row.group
        DetailRowView(row)
      }
      val left = ordered.size - shown
      if (left > 0) {
        OutlinedButton(onClick = { shown += PAGE }) {
          Text(stringResource(R.string.show_more, minOf(PAGE, left), left))
        }
      }
      if (tab == DetailTab.NATIVE_LIBS || tab == DetailTab.SERVICES) {
        Text(
          stringResource(R.string.sdks_note),
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

@Composable
private fun DetailRowView(row: DetailRow) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.weight(1f)) {
      Text(
        row.title,
        style = MaterialTheme.typography.bodyMedium,
        textDecoration = if (row.struck) TextDecoration.LineThrough else null,
      )
      row.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    row.badge?.let { Badge(it) }
  }
}

@Composable
private fun Badge(text: String) {
  Surface(
    shape = RoundedCornerShape(50),
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
  ) {
    Text(
      text,
      style = MaterialTheme.typography.labelMedium,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      maxLines = 1,
    )
  }
}

private fun nativeRows(apk: ApkInspection): List<DetailRow> =
  apk.nativeLibraryDetails.map { lib ->
    val sixteen =
      if (lib.is64Bit) "16K ELF: ${tick(lib.elfAlignedTo16Kb)}, ZIP: ${tick(lib.zipAlignedTo16Kb)}"
      else null
    DetailRow(
      title = lib.path.substringAfterLast('/'),
      subtitle = sixteen,
      badge = LibraryCatalog.identify(lib.path)?.project,
      group = lib.abi,
    )
  }

/** Rows for components, grouped by process: the main process first, the others by name. */
private fun componentRows(
  components: List<ApkComponent>,
  packageName: String,
  labels: DetailLabels,
): List<DetailRow> {
  fun groupOf(component: ApkComponent): String? {
    val process = component.processName
    return when {
      process == null || process == packageName -> null
      process.startsWith("$packageName:") -> process.removePrefix(packageName)
      else -> process
    }
  }
  return components
    .map { component ->
      DetailRow(
        title = component.name,
        subtitle = if (component.exported) labels.exported else null,
        badge = SdkCatalog.identify(component.name)?.name,
        struck = !component.enabled,
        group = groupOf(component),
      )
    }
    .sortedWith(compareBy<DetailRow, String?>(nullsFirst()) { it.group })
}

private fun tick(value: Boolean?): String =
  when (value) {
    true -> "✓"
    false -> "✗"
    null -> "–"
  }

private enum class PermissionLevel(val order: Int) {
  DANGEROUS(0),
  SIGNATURE(1),
  NORMAL(2),
  UNKNOWN(3),
}

private fun permissionLevel(pm: PackageManager, name: String): PermissionLevel =
  try {
    @Suppress("DEPRECATION") val level = pm.getPermissionInfo(name, 0).protectionLevel
    when (level and PermissionInfo.PROTECTION_MASK_BASE) {
      PermissionInfo.PROTECTION_DANGEROUS -> PermissionLevel.DANGEROUS
      PermissionInfo.PROTECTION_SIGNATURE,
      PermissionInfo.PROTECTION_SIGNATURE_OR_SYSTEM -> PermissionLevel.SIGNATURE
      else -> PermissionLevel.NORMAL
    }
  } catch (_: PackageManager.NameNotFoundException) {
    PermissionLevel.UNKNOWN
  } catch (_: RuntimeException) {
    PermissionLevel.UNKNOWN
  }

/**
 * Requested permissions grouped by how this device classifies them. A permission the device does
 * not know (such as ohos.* or another app's custom one) is listed apart. Nothing here says whether
 * a permission was granted: the APK is not installed.
 */
private fun permissionRows(
  pm: PackageManager,
  apk: ApkInspection,
  labels: DetailLabels,
  granted: Set<String>?,
): List<DetailRow> {
  fun label(level: PermissionLevel) =
    when (level) {
      PermissionLevel.DANGEROUS -> labels.dangerous
      PermissionLevel.SIGNATURE -> labels.signature
      PermissionLevel.NORMAL -> labels.normal
      PermissionLevel.UNKNOWN -> labels.unknown
    }
  val requested =
    apk.requestedPermissions
      .map { it to permissionLevel(pm, it) }
      .sortedWith(
        compareBy({ granted != null && it.first in granted }, { it.second.order }, { it.first }),
      )
      .map { (name, level) ->
        val group =
          when {
            granted == null -> label(level)
            name in granted -> labels.granted
            else -> labels.notGranted
          }
        DetailRow(
          title = name,
          subtitle = if (granted == null) null else label(level),
          group = group,
        )
      }
  val declared = apk.declaredPermissions.map { DetailRow(title = it, group = labels.declared) }
  return requested + declared
}
