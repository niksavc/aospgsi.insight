package com.bloxinsight.core.apkinspector

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** Read-only metadata extractor for a user-selected APK. Call from a background dispatcher. */
class ApkAnalyzer(private val context: Context) {
    fun analyze(uri: Uri): ApkAnalysisResult {
        val tempApk = File.createTempFile("bloxinsight-analysis-", ".apk", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Не удалось открыть выбранный файл.")
            input.use { source -> tempApk.outputStream().use { sink -> source.copyTo(sink) } }

            val info = getPackageInfo(tempApk.absolutePath)
                ?: throw IOException("Android не смог прочитать метаданные APK.")
            val appInfo = info.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir = tempApk.absolutePath
                appInfo.publicSourceDir = tempApk.absolutePath
            }
            val label = try {
                if (appInfo != null) context.packageManager.getApplicationLabel(appInfo).toString()
                else info.packageName ?: "Unknown app"
            } catch (_: Exception) {
                info.packageName ?: "Unknown app"
            }

            return ApkAnalysisResult(
                fileName = queryDisplayName(uri) ?: "selected.apk",
                fileSizeBytes = tempApk.length(),
                appLabel = label,
                packageName = info.packageName ?: "Unknown",
                versionName = info.versionName ?: "Unknown",
                versionCode = getVersionCode(info),
                minSdk = appInfo?.minSdkVersion ?: 0,
                targetSdk = appInfo?.targetSdkVersion ?: 0,
                requestedPermissions = info.requestedPermissions?.toList().orEmpty().sorted(),
                nativeLibraries = readNativeLibraries(tempApk)
            )
        } finally {
            tempApk.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(path: String): PackageInfo? =
        context.packageManager.getPackageArchiveInfo(
            path,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA
        )

    @Suppress("DEPRECATION")
    private fun getVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    private fun readNativeLibraries(apk: File): List<String> = try {
        ZipFile(apk).use { zip ->
            val entries = zip.entries()
            buildList {
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement().name
                    if (name.startsWith("lib/") && name.endsWith(".so") && !name.endsWith("/")) add(name)
                }
            }.distinct().sorted()
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
    } catch (_: Exception) { null }
}

data class ApkAnalysisResult(
    val fileName: String,
    val fileSizeBytes: Long,
    val appLabel: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val requestedPermissions: List<String>,
    /** APK ZIP paths, e.g. lib/arm64-v8a/libexample.so */
    val nativeLibraries: List<String>
) {
    val fileSizeLabel: String
        get() = when {
            fileSizeBytes < 1024 -> "$fileSizeBytes B"
            fileSizeBytes < 1024 * 1024 -> "%.1f KB".format(fileSizeBytes / 1024.0)
            else -> "%.1f MB".format(fileSizeBytes / (1024.0 * 1024.0))
        }
}
