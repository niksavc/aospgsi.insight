package com.bloxinsight.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.bloxinsight.core.model.ApkInspection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI state of the analysis status line; rendered with string resources so it follows the app language. */
private sealed interface AnalysisStatus {
    data object Idle : AnalysisStatus
    data object Analyzing : AnalysisStatus
    data object Done : AnalysisStatus
    data class Failed(val message: String?) : AnalysisStatus
}

@Composable
fun BloxInsightAndroidApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<ApkInspection?>(null) }
    var status by remember { mutableStateOf<AnalysisStatus>(AnalysisStatus.Idle) }
    val busy = status is AnalysisStatus.Analyzing

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            status = AnalysisStatus.Analyzing
            result = null
            scope.launch {
                status = try {
                    result = withContext(Dispatchers.IO) { ApkAnalyzer(context).analyze(uri) }
                    AnalysisStatus.Done
                } catch (e: Exception) {
                    AnalysisStatus.Failed(e.message)
                }
            }
        }
    }

    val unknown = stringResource(R.string.value_unknown)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("BloxInsight", style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.inspector_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.inspector_subtitle))
                Button(
                    onClick = {
                        picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                    },
                    enabled = !busy
                ) {
                    Text(stringResource(if (busy) R.string.analyzing_button else R.string.pick_apk))
                }
                val statusText = when (val s = status) {
                    AnalysisStatus.Idle -> stringResource(R.string.status_idle)
                    AnalysisStatus.Analyzing -> stringResource(R.string.status_analyzing)
                    AnalysisStatus.Done -> stringResource(R.string.status_done)
                    is AnalysisStatus.Failed ->
                        stringResource(R.string.status_failed, s.message ?: stringResource(R.string.error_unknown))
                }
                Text(statusText, style = MaterialTheme.typography.bodyMedium)

                result?.let { apk ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(stringResource(R.string.card_app_info), style = MaterialTheme.typography.titleLarge)
                            InfoRow(stringResource(R.string.label_file), apk.fileName ?: unknown)
                            InfoRow(stringResource(R.string.label_size), apk.fileSizeBytes?.let(::formatFileSize) ?: unknown)
                            InfoRow(stringResource(R.string.label_name), apk.appLabel ?: unknown)
                            InfoRow(stringResource(R.string.label_package), apk.packageName ?: unknown)
                            InfoRow(stringResource(R.string.label_version), apk.versionName ?: unknown)
                            InfoRow(stringResource(R.string.label_version_code), apk.versionCode?.toString() ?: unknown)
                            InfoRow(stringResource(R.string.label_min_sdk), apk.minSdk?.toString() ?: unknown)
                            InfoRow(stringResource(R.string.label_target_sdk), apk.targetSdk?.toString() ?: unknown)
                            InfoRow(stringResource(R.string.label_abi), apk.nativeAbis.joinToString().ifEmpty { "—" })
                            if (apk.isLegacyRobloxInspectionTarget) {
                                Text(stringResource(R.string.roblox_detected), color = MaterialTheme.colorScheme.primary)
                            }
                            // Warnings come from the core module and are currently English-only.
                            apk.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                stringResource(R.string.card_permissions, apk.requestedPermissions.size),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(stringResource(R.string.permissions_note))
                            if (apk.requestedPermissions.isEmpty()) Text(stringResource(R.string.permissions_none))
                            apk.requestedPermissions.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                stringResource(R.string.card_native_libs, apk.nativeLibraries.size),
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (apk.nativeLibraries.isEmpty()) Text(stringResource(R.string.native_libs_none))
                            apk.nativeLibraries.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                Text(stringResource(R.string.safety_note), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
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
