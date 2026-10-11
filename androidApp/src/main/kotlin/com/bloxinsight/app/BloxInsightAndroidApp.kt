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
import androidx.compose.ui.unit.dp
import com.bloxinsight.core.apkinspector.ApkAnalyzer
import com.bloxinsight.core.model.ApkInspection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BloxInsightAndroidApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<ApkInspection?>(null) }
    var status by remember { mutableStateOf("Выбери APK-файл для локального анализа.") }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            busy = true
            status = "Анализ APK…"
            result = null
            scope.launch {
                try {
                    result = withContext(Dispatchers.IO) { ApkAnalyzer(context).analyze(uri) }
                    status = "Анализ завершён. Файл не изменялся."
                } catch (e: Exception) {
                    status = "Не удалось проанализировать APK: ${e.message ?: "неизвестная ошибка"}"
                } finally {
                    busy = false
                }
            }
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("BloxInsight", style = MaterialTheme.typography.headlineLarge)
                Text("APK Inspector", style = MaterialTheme.typography.titleLarge)
                Text("Read-only анализ манифеста, разрешений и нативных библиотек.")
                Button(
                    onClick = {
                        picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                    },
                    enabled = !busy
                ) {
                    Text(if (busy) "Анализ…" else "Выбрать APK")
                }
                Text(status, style = MaterialTheme.typography.bodyMedium)

                result?.let { apk ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Сведения о приложении", style = MaterialTheme.typography.titleLarge)
                            InfoRow("Файл", apk.fileName ?: UNKNOWN)
                            InfoRow("Размер", apk.fileSizeBytes?.let(::formatFileSize) ?: UNKNOWN)
                            InfoRow("Название", apk.appLabel ?: UNKNOWN)
                            InfoRow("Package ID", apk.packageName ?: UNKNOWN)
                            InfoRow("Версия", apk.versionName ?: UNKNOWN)
                            InfoRow("Version code", apk.versionCode?.toString() ?: UNKNOWN)
                            InfoRow("Min SDK", apk.minSdk?.toString() ?: UNKNOWN)
                            InfoRow("Target SDK", apk.targetSdk?.toString() ?: UNKNOWN)
                            InfoRow("ABI", apk.nativeAbis.joinToString().ifEmpty { "—" })
                            if (apk.isLegacyRobloxInspectionTarget) {
                                Text("Обнаружен известный package ID Roblox.", color = MaterialTheme.colorScheme.primary)
                            }
                            apk.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Запрошенные разрешения (${apk.requestedPermissions.size})", style = MaterialTheme.typography.titleLarge)
                            Text("Это разрешения из APK-манифеста, а не список выданных пользователем разрешений.")
                            if (apk.requestedPermissions.isEmpty()) Text("Разрешения не найдены.")
                            apk.requestedPermissions.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Нативные библиотеки (${apk.nativeLibraries.size})", style = MaterialTheme.typography.titleLarge)
                            if (apk.nativeLibraries.isEmpty()) Text("Нативные .so библиотеки не найдены.")
                            apk.nativeLibraries.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                Text(
                    "Безопасность: APK анализируется локально. BloxInsight не устанавливает, не запускает и не изменяет выбранный файл.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private const val UNKNOWN = "Неизвестно"

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
