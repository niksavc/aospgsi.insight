package com.bloxinsight.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bloxinsight.core.apkinspector.ApkAnalyzer
import com.bloxinsight.core.installer.DeviceProbe
import com.bloxinsight.core.installer.InstallEvents
import com.bloxinsight.core.installer.InstallStatus
import com.bloxinsight.core.installer.PackageInstallerClient
import com.bloxinsight.core.model.ApkComponent
import com.bloxinsight.core.model.ApkInspection
import com.bloxinsight.core.verification.InstallAssessment
import com.bloxinsight.core.verification.InstallIssue
import com.bloxinsight.core.verification.InstallIssueCode
import com.bloxinsight.core.verification.InstallPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

@Composable
fun BloxInsightAndroidApp() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var result by remember { mutableStateOf<ApkInspection?>(null) }
  var status by remember { mutableStateOf<AnalysisStatus>(AnalysisStatus.Idle) }
  var pickedUri by remember { mutableStateOf<Uri?>(null) }
  var assessment by remember { mutableStateOf<InstallAssessment?>(null) }
  var installUi by remember { mutableStateOf<InstallUi>(InstallUi.Idle) }
  val busy = status is AnalysisStatus.Analyzing

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
        scope.launch {
          status =
            try {
              val (inspection, check) =
                withContext(Dispatchers.IO) {
                  val inspection = ApkAnalyzer(context).analyze(uri)
                  val probe = DeviceProbe(context)
                  val installed = inspection.packageName?.let(probe::installedApp)
                  inspection to InstallPolicy.evaluate(inspection, installed, probe.deviceInfo())
                }
              result = inspection
              assessment = check
              AnalysisStatus.Done
            } catch (e: Exception) {
              AnalysisStatus.Failed(e.message)
            }
        }
      }
    }

  val unknown = stringResource(R.string.value_unknown)

  DisposableEffect(Unit) {
    InstallEvents.listener = { installUi = InstallUi.Reported(it) }
    onDispose { InstallEvents.listener = null }
  }

  MaterialTheme {
    Surface(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("BloxInsight", style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.inspector_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.inspector_subtitle))
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

          SectionCard(stringResource(R.string.card_permissions, apk.requestedPermissions.size)) {
            Text(stringResource(R.string.permissions_note))
            if (apk.requestedPermissions.isEmpty()) Text(stringResource(R.string.permissions_none))
            apk.requestedPermissions.forEach {
              Text("• $it", style = MaterialTheme.typography.bodySmall)
            }
          }

          ListCard(
            stringResource(R.string.card_declared_permissions, apk.declaredPermissions.size),
            apk.declaredPermissions,
          )

          SectionCard(stringResource(R.string.card_native_libs, apk.nativeLibraryDetails.size)) {
            if (apk.nativeLibraryDetails.isEmpty()) Text(stringResource(R.string.native_libs_none))
            apk.nativeLibraryDetails.forEach { lib ->
              val elf = mark(lib.elfAlignedTo16Kb)
              val zip = mark(lib.zipAlignedTo16Kb)
              Text(
                "• ${lib.path}" + if (lib.is64Bit) "  [ELF 16K: $elf, ZIP: $zip]" else "",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }

          ComponentCard(R.string.card_activities, apk.activities)
          ComponentCard(R.string.card_services, apk.services)
          ComponentCard(R.string.card_receivers, apk.receivers)
          ComponentCard(R.string.card_providers, apk.providers)

          ListCard(
            stringResource(R.string.card_metadata, apk.metaData.size),
            apk.metaData.map { (key, value) -> "$key = $value" },
          )

          if (apk.isBundle) {
            ListCard(stringResource(R.string.card_splits, apk.bundleApks.size), apk.bundleApks)
          }
        }
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
private fun ListCard(title: String, items: List<String>) {
  SectionCard(title) {
    if (items.isEmpty()) Text(stringResource(R.string.none_found))
    items.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
  }
}

@Composable
private fun ComponentCard(@StringRes titleRes: Int, components: List<ApkComponent>) {
  val exported = stringResource(R.string.exported_marker)
  ListCard(
    stringResource(titleRes, components.size),
    components.map { if (it.exported) "${it.name} ($exported)" else it.name },
  )
}
