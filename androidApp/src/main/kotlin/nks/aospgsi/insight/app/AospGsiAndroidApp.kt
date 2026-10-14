package nks.aospgsi.insight.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nks.aospgsi.insight.core.apkinspector.AbiRules
import nks.aospgsi.insight.core.apkinspector.AbiSupport
import nks.aospgsi.insight.core.apkinspector.AdSdkCatalog
import nks.aospgsi.insight.core.apkinspector.ApkAnalyzer
import nks.aospgsi.insight.core.apkinspector.DistributionRules
import nks.aospgsi.insight.core.apkinspector.EvidenceKind
import nks.aospgsi.insight.core.apkinspector.LibraryCatalog
import nks.aospgsi.insight.core.apkinspector.LibraryCategory
import nks.aospgsi.insight.core.apkinspector.SdkCatalog
import nks.aospgsi.insight.core.apkinspector.SdkCategory
import nks.aospgsi.insight.core.apktool.Aapt2Provider
import nks.aospgsi.insight.core.installer.DeviceProbe
import nks.aospgsi.insight.core.installer.InstallEvents
import nks.aospgsi.insight.core.installer.InstallStatus
import nks.aospgsi.insight.core.installer.PackageInstallerClient
import nks.aospgsi.insight.core.model.ApkInspection
import nks.aospgsi.insight.core.model.ApkResourceFile
import nks.aospgsi.insight.core.model.ApplicationAttributes
import nks.aospgsi.insight.core.model.ResourceKind
import nks.aospgsi.insight.core.patchapi.FixPlan
import nks.aospgsi.insight.core.patchapi.FixPlanner
import nks.aospgsi.insight.core.verification.DeviceInfo
import nks.aospgsi.insight.core.verification.InstallAssessment
import nks.aospgsi.insight.core.verification.InstallIssue
import nks.aospgsi.insight.core.verification.InstallIssueCode
import nks.aospgsi.insight.core.verification.InstallPolicy
import nks.aospgsi.insight.core.verification.InstalledApp

/**
 * UI state of the analysis status line; rendered with string resources so it follows the app
 * language.
 */
private sealed interface AnalysisStatus {
  data object Idle : AnalysisStatus

  data object Analyzing : AnalysisStatus

  data object Done : AnalysisStatus

  data class Failed(val message: String?) : AnalysisStatus
}

/** UI state of the install step that follows an inspection. */
private sealed interface InstallUi {
  data object Idle : InstallUi

  /** Streaming the file into the installer session. */
  data object Preparing : InstallUi

  /** Session committed; waiting for the system installer to report. */
  data object Waiting : InstallUi

  data class Failed(val message: String?) : InstallUi

  data class Reported(val status: InstallStatus) : InstallUi
}

/** Everything the screen learns about a picked file. */
private class Analysis(
  val inspection: ApkInspection,
  val check: InstallAssessment,
  val installed: InstalledApp?,
  val device: DeviceInfo,
)

private fun analyze(context: Context, uri: Uri): Analysis {
  val inspection = ApkAnalyzer(context).analyze(uri)
  val probe = DeviceProbe(context)
  val installed = inspection.packageName?.let(probe::installedApp)
  val device = probe.deviceInfo()
  return Analysis(
    inspection,
    InstallPolicy.evaluate(inspection, installed, device),
    installed,
    device,
  )
}

/**
 * Copies the picked file into the app's cache (checking it is still the file that was inspected),
 * so the patch service does not depend on a content URI permission that may expire.
 */
private fun stageSource(
  context: Context,
  uri: Uri,
  expectedSha256: String?,
  packageName: String,
  /** What to change, or null to only re-align and re-sign (repair). */
  plan: FixPlan.Ready?,
): PatchRequest {
  val target = File(context.cacheDir, "patch-source.apk")
  val digest = MessageDigest.getInstance("SHA-256")
  val input =
    context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open the file.")
  input.use { source ->
    target.outputStream().use { sink ->
      val buffer = ByteArray(64 * 1024)
      while (true) {
        val read = source.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
        sink.write(buffer, 0, read)
      }
    }
  }
  val actual = digest.digest().joinToString("") { "%02x".format(it) }
  if (expectedSha256 != null && !actual.equals(expectedSha256, ignoreCase = true)) {
    target.delete()
    throw IOException("The file changed after it was inspected. Inspect it again.")
  }
  return PatchRequest(
    target.path,
    packageName,
    plan?.targetSdk,
    plan?.newPackage,
    repairOnly = plan == null,
  )
}

@Composable
fun AospGsiAndroidApp() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var result by remember { mutableStateOf<ApkInspection?>(null) }
  var status by remember { mutableStateOf<AnalysisStatus>(AnalysisStatus.Idle) }
  var pickedUri by remember { mutableStateOf<Uri?>(null) }
  var assessment by remember { mutableStateOf<InstallAssessment?>(null) }
  var installUi by remember { mutableStateOf<InstallUi>(InstallUi.Idle) }
  var installedApp by remember { mutableStateOf<InstalledApp?>(null) }
  var device by remember { mutableStateOf<DeviceInfo?>(null) }
  var cloneChoice by remember { mutableStateOf(false) }
  var patchedNote by remember { mutableStateOf(false) }
  // Whether the last patch job was a repair, so its progress shows on the Repair card only.
  var repairRun by remember { mutableStateOf(false) }
  var pendingRequest by remember { mutableStateOf<PatchRequest?>(null) }
  val patchState by PatchEvents.state.collectAsState()
  val busy = status is AnalysisStatus.Analyzing

  fun show(analysis: Analysis, uri: Uri, patched: Boolean) {
    result = analysis.inspection
    assessment = analysis.check
    installedApp = analysis.installed
    device = analysis.device
    pickedUri = uri
    patchedNote = patched
    // Next to an installed original (signed with a different key) a clone is the way to go.
    cloneChoice = analysis.installed != null && !patched
  }

  val notificationPermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      // Patching works without the notification; the permission only decides whether it is shown.
      pendingRequest?.let { PatchService.start(context, it) }
      pendingRequest = null
    }

  val picker =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
      if (uri != null) {
        status = AnalysisStatus.Analyzing
        result = null
        assessment = null
        installUi = InstallUi.Idle
        pickedUri = uri
        PatchEvents.state.value = PatchState.Idle
        scope.launch {
          status =
            try {
              show(withContext(Dispatchers.IO) { analyze(context, uri) }, uri, patched = false)
              AnalysisStatus.Done
            } catch (e: Exception) {
              AnalysisStatus.Failed(e.message)
            }
        }
      }
    }

  val unknown = stringResource(R.string.value_unknown)

  // A finished patch replaces the picked file: inspect the signed result so the install check below
  // judges what would actually be installed.
  LaunchedEffect(patchState) {
    val done = patchState as? PatchState.Done ?: return@LaunchedEffect
    val patched = Uri.fromFile(File(done.path))
    status = AnalysisStatus.Analyzing
    installUi = InstallUi.Idle
    status =
      try {
        show(withContext(Dispatchers.IO) { analyze(context, patched) }, patched, patched = true)
        PatchEvents.state.value = PatchState.Idle
        AnalysisStatus.Done
      } catch (e: Exception) {
        AnalysisStatus.Failed(e.message)
      }
  }

  DisposableEffect(Unit) {
    InstallEvents.listener = { installUi = InstallUi.Reported(it) }
    onDispose { InstallEvents.listener = null }
  }

  AppTheme {
    Surface(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier =
          Modifier.fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("AOSP GSI Insight", style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.inspector_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.inspector_subtitle))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(
              R.string.nav_apps to InstalledAppsActivity::class.java,
              R.string.nav_settings to SettingsActivity::class.java,
              R.string.nav_about to AboutActivity::class.java,
            )
            .forEach { (label, target) ->
              OutlinedButton(
                onClick = { context.startActivity(Intent(context, target)) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
              ) {
                Text(stringResource(label), maxLines = 1)
              }
            }
        }
        Button(
          onClick = {
            picker.launch(
              arrayOf(
                "application/vnd.android.package-archive",
                "application/zip",
                "application/octet-stream",
              ),
            )
          },
          enabled = !busy,
        ) {
          Text(stringResource(if (busy) R.string.analyzing_button else R.string.pick_apk))
        }
        val statusText =
          when (val s = status) {
            AnalysisStatus.Idle -> stringResource(R.string.status_idle)
            AnalysisStatus.Analyzing -> stringResource(R.string.status_analyzing)
            AnalysisStatus.Done -> stringResource(R.string.status_done)
            is AnalysisStatus.Failed ->
              stringResource(
                R.string.status_failed,
                s.message ?: stringResource(R.string.error_unknown),
              )
          }
        Text(statusText, style = MaterialTheme.typography.bodyMedium)

        result?.let { apk ->
          Card(modifier = Modifier.fillMaxWidth()) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                stringResource(R.string.card_app_info),
                style = MaterialTheme.typography.titleLarge,
              )
              InfoRow(stringResource(R.string.label_file), apk.fileName ?: unknown)
              InfoRow(
                stringResource(R.string.label_size),
                apk.fileSizeBytes?.let(::formatFileSize) ?: unknown,
              )
              InfoRow(stringResource(R.string.label_sha256), apk.sha256 ?: unknown)
              InfoRow(stringResource(R.string.label_name), apk.appLabel ?: unknown)
              InfoRow(stringResource(R.string.label_package), apk.packageName ?: unknown)
              InfoRow(stringResource(R.string.label_version), apk.versionName ?: unknown)
              InfoRow(
                stringResource(R.string.label_version_code),
                apk.versionCode?.toString() ?: unknown,
              )
              InfoRow(stringResource(R.string.label_min_sdk), apk.minSdk?.toString() ?: unknown)
              InfoRow(
                stringResource(R.string.label_target_sdk),
                apk.targetSdk?.toString() ?: unknown,
              )
              InfoRow(
                stringResource(R.string.label_abi),
                apk.nativeAbis.joinToString().ifEmpty { "—" },
              )
              AbiSummaryRows(apk.nativeAbis)
              BuildTagsRow(apk)
              apk.multiArch?.let {
                InfoRow(
                  stringResource(R.string.label_multiarch),
                  stringResource(if (it) R.string.value_yes else R.string.value_no),
                )
              }
              if (apk.isBundle) {
                Text(
                  stringResource(R.string.bundle_note, apk.bundleApks.size),
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              if (apk.isLegacyRobloxInspectionTarget) {
                Text(
                  stringResource(R.string.roblox_detected),
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              // Warnings come from the core module and are currently English-only.
              apk.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
            }
          }

          if (patchedNote) {
            Text(
              stringResource(R.string.patched_note),
              color = MaterialTheme.colorScheme.primary,
            )
          }

          assessment?.let { check ->
            SectionCard(stringResource(R.string.card_install_check)) {
              if (check.issues.isEmpty()) Text(stringResource(R.string.install_no_issues))
              check.issues.forEach { issue ->
                val icon = if (issue.blocking) "⛔" else "⚠"
                Text("$icon ${issueText(issue)}", style = MaterialTheme.typography.bodyMedium)
              }
              val uri = pickedUri
              val installBusy = installUi is InstallUi.Preparing || installUi is InstallUi.Waiting
              Button(
                onClick = {
                  val sha = apk.sha256
                  if (uri != null && sha != null) {
                    installUi = InstallUi.Preparing
                    scope.launch {
                      installUi =
                        try {
                          withContext(Dispatchers.IO) {
                            PackageInstallerClient(context)
                              .install(uri, sha, apk.packageName, apk.bundleApks)
                          }
                          InstallUi.Waiting
                        } catch (e: Exception) {
                          InstallUi.Failed(e.message)
                        }
                    }
                  }
                },
                enabled = check.canInstall && !installBusy && apk.sha256 != null,
              ) {
                Text(stringResource(R.string.install_button))
              }
              Text(installStatusText(installUi), style = MaterialTheme.typography.bodyMedium)
            }
          }

          ProprietaryCard(apk.packageName)

          val deviceInfo = device
          val check = assessment
          if (deviceInfo != null && check != null) {
            val plan =
              FixPlanner.plan(
                apk,
                InstallPolicy.minimumInstallableTargetSdk(deviceInfo.sdk),
                cloneChoice,
              )
            val startPatch: (FixPlan.Ready?) -> Unit = { ready ->
              val uri = pickedUri
              val packageName = apk.packageName
              if (uri != null && packageName != null) {
                repairRun = ready == null
                PatchEvents.state.value =
                  PatchState.Running(0, if (ready == null) "align" else "decode")
                scope.launch {
                  try {
                    val request =
                      withContext(Dispatchers.IO) {
                        stageSource(context, uri, apk.sha256, packageName, ready)
                      }
                    val needsPermission =
                      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                          PackageManager.PERMISSION_GRANTED
                    if (needsPermission) {
                      pendingRequest = request
                      notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                      PatchService.start(context, request)
                    }
                  } catch (e: Exception) {
                    PatchEvents.state.value = PatchState.Failed("prepare", e.message)
                  }
                }
              }
            }
            FixCard(
              apk = apk,
              assessment = check,
              installed = installedApp,
              plan = plan,
              cloneAlongside = cloneChoice,
              onCloneChange = { cloneChoice = it },
              patchState = if (repairRun) PatchState.Idle else patchState,
              aapt2Ready = Aapt2Provider.find(context) != null,
              otherJobRunning = repairRun && patchState is PatchState.Running,
              onFix = { startPatch(it) },
            )
            RepairCard(
              apk = apk,
              patchState = if (repairRun) patchState else PatchState.Idle,
              anyRunning = patchState is PatchState.Running,
              onRepair = { startPatch(null) },
            )
          }

          SectionCard(stringResource(R.string.card_signatures)) {
            when (apk.signatureVerified) {
              true -> Text(stringResource(R.string.sig_verified_yes))
              else -> Text(stringResource(R.string.sig_verified_no))
            }
            if (apk.signatureSchemes.isNotEmpty()) {
              Text(stringResource(R.string.sig_schemes, apk.signatureSchemes.joinToString()))
            }
            if (apk.signerSha256.isEmpty()) Text(stringResource(R.string.sig_none))
            apk.signerSha256.forEach {
              Text("• SHA-256: $it", style = MaterialTheme.typography.bodySmall)
            }
          }

          SectionCard(stringResource(R.string.card_16kb)) {
            when (apk.sixteenKbReady) {
              true -> Text(stringResource(R.string.kb16_ready))
              false -> Text(stringResource(R.string.kb16_not_ready))
              null -> Text(stringResource(R.string.kb16_no_64bit))
            }
          }

          ApplicationCard(apk.application)
          DistributionCard(apk.metaData)
          RecognizedLibrariesCard(apk)
          SdkCard(apk)
          JsonResourcesCard(apk.jsonResources)
          DetailTabsCard(apk)

          if (apk.isBundle) {
            ListCard(stringResource(R.string.card_splits, apk.bundleApks.size), apk.bundleApks)
          }
        }
        SupportCard()

        Text(stringResource(R.string.safety_note), style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

private fun formatFileSize(bytes: Long): String =
  when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
  }

@Composable
private fun InfoRow(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("$label:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.38f))
    Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.62f))
  }
}

@Composable
private fun issueText(issue: InstallIssue): String {
  val res =
    when (issue.code) {
      InstallIssueCode.NO_PACKAGE_NAME -> R.string.issue_no_package_name
      InstallIssueCode.SIGNATURE_INVALID -> R.string.issue_signature_invalid
      InstallIssueCode.SIGNER_MISMATCH -> R.string.issue_signer_mismatch
      InstallIssueCode.DOWNGRADE -> R.string.issue_downgrade
      InstallIssueCode.MIN_SDK_TOO_HIGH -> R.string.issue_min_sdk_too_high
      InstallIssueCode.TARGET_SDK_TOO_LOW -> R.string.issue_target_sdk_too_low
      InstallIssueCode.NO_COMPATIBLE_ABI -> R.string.issue_no_compatible_abi
      InstallIssueCode.UNKNOWN_SIGNER -> R.string.issue_unknown_signer
      InstallIssueCode.SIXTEEN_KB_INCOMPATIBLE -> R.string.issue_16kb
    }
  return stringResource(res, *issue.args.toTypedArray())
}

@Composable
private fun installStatusText(state: InstallUi): String =
  when (state) {
    InstallUi.Idle -> ""
    InstallUi.Preparing -> stringResource(R.string.install_preparing)
    InstallUi.Waiting -> stringResource(R.string.install_waiting)
    is InstallUi.Failed ->
      stringResource(
        R.string.install_failed,
        state.message ?: stringResource(R.string.error_unknown),
      )
    is InstallUi.Reported ->
      when (val status = state.status) {
        InstallStatus.AwaitingConfirmation -> stringResource(R.string.install_confirm)
        is InstallStatus.Success ->
          stringResource(R.string.install_success, status.packageName ?: "")
        is InstallStatus.Failure ->
          stringResource(
            R.string.install_failed,
            status.message ?: stringResource(R.string.install_status_code, status.code),
          )
      }
  }

private fun mark(value: Boolean?): String =
  when (value) {
    true -> "✓"
    false -> "✗"
    null -> "–"
  }

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(title, style = MaterialTheme.typography.titleLarge)
      content()
    }
  }
}

@Composable
private fun ListCard(title: String, items: List<String>, pageSize: Int = PAGE_SIZE) {
  var query by rememberSaveable(title) { mutableStateOf("") }
  var shown by rememberSaveable(title, items.size, query) { mutableIntStateOf(pageSize) }
  val visible =
    remember(items, query) {
      if (query.isBlank()) items else items.filter { it.contains(query.trim(), ignoreCase = true) }
    }
  SectionCard(title) {
    if (items.size > pageSize) {
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.filter_hint)) },
      )
    }
    if (visible.isEmpty()) Text(stringResource(R.string.none_found))
    visible.take(shown).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
    val left = visible.size - shown
    if (left > 0) {
      OutlinedButton(onClick = { shown += pageSize }) {
        Text(stringResource(R.string.show_more, minOf(pageSize, left), left))
      }
    }
  }
}

private const val PAGE_SIZE = 25

@Composable
private fun RecognizedLibrariesCard(apk: ApkInspection) {
  val found =
    remember(apk.nativeLibraries) {
      apk.nativeLibraries
        .distinctBy { it.substringAfterLast('/') }
        .mapNotNull { path ->
          LibraryCatalog.identify(path)?.let { path.substringAfterLast('/') to it }
        }
        .sortedBy { it.first }
    }
  val framework =
    remember(apk.nativeLibraries) { LibraryCatalog.detectFramework(apk.nativeLibraries) }
  val ads =
    remember(apk) {
      val names = (apk.activities + apk.services + apk.receivers + apk.providers).map { it.name }
      AdSdkCatalog.detect(names, apk.metaData.keys)
    }
  if (found.isEmpty() && ads.isEmpty()) return
  if (found.isNotEmpty()) {
    SectionCard(stringResource(R.string.card_known_libs, found.size)) {
      framework?.let { Text(stringResource(R.string.framework_line, it)) }
      found.forEach { (file, lib) ->
        val category = stringResource(libraryCategoryLabel(lib.category))
        val proprietary =
          if (lib.proprietary) ", ${stringResource(R.string.lib_proprietary)}" else ""
        Text(
          "• $file — ${lib.name} ($category; ${lib.project}, ${lib.license}$proprietary)",
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
  SectionCard(stringResource(R.string.card_ad_sdks)) {
    if (ads.isEmpty()) {
      Text(stringResource(R.string.ad_sdks_none))
    } else {
      ads.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
    }
    Text(stringResource(R.string.ad_sdks_note), style = MaterialTheme.typography.bodySmall)
  }
}

@StringRes
private fun libraryCategoryLabel(category: LibraryCategory): Int =
  when (category) {
    LibraryCategory.APP_CORE -> R.string.lib_cat_app_core
    LibraryCategory.MAPS -> R.string.lib_cat_maps
    LibraryCategory.MACHINE_LEARNING -> R.string.lib_cat_ml
    LibraryCategory.ANDROIDX -> R.string.lib_cat_androidx
    LibraryCategory.MEDIA -> R.string.lib_cat_media
    LibraryCategory.GAME_ENGINE -> R.string.lib_cat_engine
    LibraryCategory.RUNTIME -> R.string.lib_cat_runtime
    LibraryCategory.CRASH_REPORTING -> R.string.lib_cat_crash
    LibraryCategory.STORAGE -> R.string.lib_cat_storage
  }

@Composable
private fun BuildTagsRow(apk: ApkInspection) {
  val tags =
    remember(apk) {
      apk.buildTags + listOfNotNull(LibraryCatalog.detectFramework(apk.nativeLibraries))
    }
  if (tags.isNotEmpty()) {
    Text(
      stringResource(R.string.build_tags, tags.joinToString(" · ")),
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

@Composable
private fun AbiSummaryRows(abis: List<String>) {
  val support = remember(abis) { AbiRules.support(abis) }
  val label =
    stringResource(
      when (support) {
        AbiSupport.NO_NATIVE -> R.string.abi_support_none
        AbiSupport.ONLY_32_BIT -> R.string.abi_support_32
        AbiSupport.ONLY_64_BIT -> R.string.abi_support_64
        AbiSupport.BOTH -> R.string.abi_support_both
      },
    )
  Text(label, style = MaterialTheme.typography.bodySmall)
  val device = remember { Build.SUPPORTED_ABIS?.toList().orEmpty() }
  val runs = remember(abis) { AbiRules.runsOnDevice(abis, device) }
  if (runs != null) {
    val verdict = stringResource(if (runs) R.string.abi_device_ok else R.string.abi_device_no)
    Text(
      stringResource(R.string.abi_this_device, device.joinToString()) + " — " + verdict,
      style = MaterialTheme.typography.bodySmall,
      color = if (runs) Color.Unspecified else MaterialTheme.colorScheme.error,
    )
  }
}

@Composable
private fun SdkCard(apk: ApkInspection) {
  val found =
    remember(apk) {
      val components =
        (apk.activities + apk.services + apk.receivers + apk.providers).map { it.name }
      SdkCatalog.detect(components, apk.metaData.keys, apk.requestedPermissions)
    }
  if (found.isEmpty()) return
  SectionCard(stringResource(R.string.card_sdks, found.size)) {
    found.forEach { sdk ->
      val category = stringResource(sdkCategoryLabel(sdk.category))
      val evidence = stringResource(sdkEvidenceLabel(sdk.evidence))
      Text(
        "• ${sdk.name} ($category; $evidence: ${sdk.matched})",
        style = MaterialTheme.typography.bodySmall,
      )
    }
    Text(stringResource(R.string.sdks_note), style = MaterialTheme.typography.bodySmall)
  }
}

@StringRes
private fun sdkCategoryLabel(category: SdkCategory): Int =
  when (category) {
    SdkCategory.ANALYTICS -> R.string.sdk_cat_analytics
    SdkCategory.CRASH_REPORTING -> R.string.sdk_cat_crash
    SdkCategory.PUSH -> R.string.sdk_cat_push
    SdkCategory.ATTRIBUTION -> R.string.sdk_cat_attribution
    SdkCategory.ENGAGEMENT -> R.string.sdk_cat_engagement
    SdkCategory.PAYMENTS -> R.string.sdk_cat_payments
    SdkCategory.LICENSING -> R.string.sdk_cat_licensing
    SdkCategory.ADVERTISING_ID -> R.string.sdk_cat_ad_id
    SdkCategory.SOCIAL -> R.string.sdk_cat_social
    SdkCategory.GOOGLE_SERVICES -> R.string.sdk_cat_google
    SdkCategory.HUAWEI_SERVICES -> R.string.sdk_cat_huawei
    SdkCategory.FRAMEWORK -> R.string.sdk_cat_framework
    SdkCategory.JETPACK -> R.string.sdk_cat_jetpack
    SdkCategory.LIBRARY -> R.string.sdk_cat_library
  }

@StringRes
private fun sdkEvidenceLabel(kind: EvidenceKind): Int =
  when (kind) {
    EvidenceKind.COMPONENT -> R.string.sdk_evidence_component
    EvidenceKind.META_DATA -> R.string.sdk_evidence_meta
    EvidenceKind.PERMISSION -> R.string.sdk_evidence_permission
  }

@Composable
private fun JsonResourcesCard(files: List<ApkResourceFile>) {
  if (files.isEmpty()) return
  val lottie = stringResource(R.string.resource_lottie)
  ListCard(
    stringResource(R.string.card_json_resources, files.size),
    files.map { f ->
      val kb = "%.1f KB".format(f.sizeBytes / 1024.0)
      if (f.kind == ResourceKind.LOTTIE) "${f.path} · $lottie · $kb" else "${f.path} · $kb"
    },
  )
}

@Composable
private fun DistributionCard(metaData: Map<String, String>) {
  val info = remember(metaData) { DistributionRules.from(metaData) } ?: return
  SectionCard(stringResource(R.string.card_distribution)) {
    if (info.claimsGooglePlay) Text(stringResource(R.string.distribution_play_stamp))
    info.stampType?.let { Text("• stamp: $it", style = MaterialTheme.typography.bodySmall) }
    if (info.playSplit) {
      Text(stringResource(R.string.distribution_split))
      info.derivedApkId?.let {
        Text("• derived APK id: $it", style = MaterialTheme.typography.bodySmall)
      }
    }
    if (info.splitsRequired) Text(stringResource(R.string.distribution_splits_required))
    Text(
      stringResource(R.string.distribution_not_proof),
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

@Composable
private fun ApplicationCard(app: ApplicationAttributes?) {
  if (app == null) return
  SectionCard(stringResource(R.string.card_application)) {
    listOf(
        "debuggable" to app.debuggable,
        "allowBackup" to app.allowBackup,
        "usesCleartextTraffic" to app.usesCleartextTraffic,
        "largeHeap" to app.largeHeap,
        "hasCode" to app.hasCode,
        "extractNativeLibs" to app.extractNativeLibs,
        "testOnly" to app.testOnly,
        "persistent" to app.persistent,
        "supportsRtl" to app.supportsRtl,
        "hardwareAccelerated" to app.hardwareAccelerated,
      )
      .forEach { (name, value) ->
        Text("${mark(value)} $name", style = MaterialTheme.typography.bodySmall)
      }
    listOf(
        "name" to app.applicationClass,
        "backupAgent" to app.backupAgent,
        "process" to app.process,
        "appCategory" to app.category,
      )
      .forEach { (name, value) ->
        value?.let { Text("• $name: $it", style = MaterialTheme.typography.bodySmall) }
      }
    Text(stringResource(R.string.application_note), style = MaterialTheme.typography.bodySmall)
  }
}
