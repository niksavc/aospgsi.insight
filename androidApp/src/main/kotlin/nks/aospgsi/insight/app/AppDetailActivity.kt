package nks.aospgsi.insight.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nks.aospgsi.insight.core.apkinspector.ApkAnalyzer
import nks.aospgsi.insight.core.model.ApkInspection

/** Details of an installed app: the inspection an APK file gets, run on its installed base APK. */
class AppDetailActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val packageName = intent.getStringExtra(EXTRA_PACKAGE)
    setContent { AppTheme { AppDetailScreen(packageName, onBack = { finish() }) } }
  }

  companion object {
    private const val EXTRA_PACKAGE = "package"

    fun intent(context: Context, packageName: String): Intent =
      Intent(context, AppDetailActivity::class.java).putExtra(EXTRA_PACKAGE, packageName)
  }
}

private class InstalledDetails(
  val inspection: ApkInspection,
  val granted: Set<String>,
  val hasSplits: Boolean,
)

private sealed interface DetailsState {
  data object Loading : DetailsState

  data class Ready(val details: InstalledDetails) : DetailsState

  data class Failed(val message: String?) : DetailsState
}

@Suppress("DEPRECATION")
private fun load(context: Context, packageName: String): InstalledDetails {
  val pm = context.packageManager
  val info: PackageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
  val app = info.applicationInfo ?: throw IllegalStateException("No application info.")
  val inspection = ApkAnalyzer(context).analyzeInstalled(File(app.sourceDir))
  val flags = info.requestedPermissionsFlags
  val granted =
    info.requestedPermissions
      ?.filterIndexed { index, _ ->
        flags != null && (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
      }
      ?.toSet()
      .orEmpty()
  val hasSplits = !app.splitSourceDirs.isNullOrEmpty()
  return InstalledDetails(inspection, granted, hasSplits)
}

@Composable
private fun AppDetailScreen(packageName: String?, onBack: () -> Unit) {
  val context = LocalContext.current
  var state by remember { mutableStateOf<DetailsState>(DetailsState.Loading) }
  LaunchedEffect(packageName) {
    state =
      if (packageName == null) {
        DetailsState.Failed(null)
      } else {
        try {
          DetailsState.Ready(withContext(Dispatchers.IO) { load(context, packageName) })
        } catch (e: Exception) {
          DetailsState.Failed(e.message)
        }
      }
  }
  val title =
    (state as? DetailsState.Ready)?.details?.inspection?.appLabel ?: packageName.orEmpty()
  ScreenFrame(title, onBack) { padding ->
    ScrollingPage(padding) {
      when (val current = state) {
        DetailsState.Loading -> Text(stringResource(R.string.apps_loading))
        is DetailsState.Failed ->
          Text(
            stringResource(
              R.string.app_detail_failed,
              current.message ?: stringResource(R.string.error_unknown),
            ),
            color = MaterialTheme.colorScheme.error,
          )
        is DetailsState.Ready -> DetailsContent(current.details)
      }
    }
  }
}

@Composable
private fun DetailsContent(details: InstalledDetails) {
  val apk = details.inspection
  val unknown = stringResource(R.string.value_unknown)
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        apk.packageName?.let { AppIcon(it, size = 56.dp) }
        Column {
          Text(apk.appLabel ?: unknown, style = MaterialTheme.typography.titleLarge)
          Text(apk.packageName ?: unknown, style = MaterialTheme.typography.bodySmall)
        }
      }
      InfoRow(stringResource(R.string.label_version), apk.versionName ?: unknown)
      InfoRow(stringResource(R.string.label_version_code), apk.versionCode?.toString() ?: unknown)
      InfoRow(stringResource(R.string.label_min_sdk), apk.minSdk?.toString() ?: unknown)
      InfoRow(stringResource(R.string.label_target_sdk), apk.targetSdk?.toString() ?: unknown)
      InfoRow(stringResource(R.string.label_abi), apk.nativeAbis.joinToString().ifEmpty { "—" })
      AbiSummaryRows(apk.nativeAbis)
      BuildTagsRow(apk)
      apk.fileSizeBytes?.let { InfoRow(stringResource(R.string.label_size), formatFileSize(it)) }
      if (details.hasSplits) {
        Text(
          stringResource(R.string.app_detail_split_note),
          style = MaterialTheme.typography.bodySmall,
        )
      }
      apk.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
    }
  }
  ApplicationCard(apk.application)
  DistributionCard(apk.metaData)
  RecognizedLibrariesCard(apk)
  SdkCard(apk)
  DetailTabsCard(apk, granted = details.granted)
}
