package nks.aospgsi.insight.core.apkinspector

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.android.apksig.ApkVerifier
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import nks.aospgsi.insight.core.model.ApkComponent
import nks.aospgsi.insight.core.model.ApkInspection
import nks.aospgsi.insight.core.model.ApkResourceFile
import nks.aospgsi.insight.core.model.NativeLibraryInfo

/**
 * Read-only inspector for a user-selected APK or APK bundle (.apks, .xapk, .apkm). Call from a
 * background dispatcher.
 */
class ApkAnalyzer(private val context: Context) {
  fun analyze(uri: Uri): ApkInspection {
    val picked = File.createTempFile("aospgsi-picked-", ".bin", context.cacheDir)
    var extractedBase: File? = null
    try {
      val sha256 = copyWithSha256(uri, picked)

      val bundleApks = listBundleApks(picked)
      val apk =
        if (bundleApks == null) {
          picked
        } else {
          File.createTempFile("aospgsi-base-", ".apk", context.cacheDir).also {
            extractedBase = it
            extractEntry(picked, chooseBaseApk(bundleApks), it)
          }
        }

      val info =
        getPackageInfo(apk.absolutePath)
          ?: throw IOException("Android could not read the APK manifest.")
      val appInfo = info.applicationInfo
      if (appInfo != null) {
        appInfo.sourceDir = apk.absolutePath
        appInfo.publicSourceDir = apk.absolutePath
      }
      val label =
        try {
          if (appInfo != null) {
            context.packageManager.getApplicationLabel(appInfo).toString()
          } else {
            info.packageName ?: "Unknown app"
          }
        } catch (_: Exception) {
          info.packageName ?: "Unknown app"
        }

      val libraries = inspectNativeLibraries(apk)
      val abis = libraries.map { it.abi }.distinct().sorted()
      val signature = verifySignatures(apk)
      val application = appInfo?.let {
        ApplicationRules.fromFlags(
          flags = it.flags,
          targetSdk = it.targetSdkVersion,
          applicationClass = it.className,
          backupAgent = it.backupAgentName,
          process = it.processName,
          category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) it.category else null,
        )
      }
      val warnings = buildList {
        application?.let { addAll(ApplicationRules.warnings(it)) }
        InspectionRules.packageNameWarning(info.packageName)?.let { add(it) }
        val unsupported = InspectionRules.unsupportedAbis(abis.toSet())
        if (unsupported.isNotEmpty()) {
          add("Unrecognized ABIs: ${unsupported.sorted().joinToString()}")
        }
        addAll(InspectionRules.sixteenKbWarnings(libraries))
        if (!signature.verified) add("The APK signature could not be verified.")
      }

      return ApkInspection(
        packageName = info.packageName,
        versionName = info.versionName,
        versionCode = getVersionCode(info),
        minSdk = appInfo?.minSdkVersion,
        targetSdk = appInfo?.targetSdkVersion,
        signerSha256 = signature.certificateSha256,
        nativeAbis = abis,
        warnings = warnings,
        fileName = queryDisplayName(uri) ?: "selected.apk",
        fileSizeBytes = picked.length(),
        appLabel = label,
        requestedPermissions = info.requestedPermissions?.toList().orEmpty().sorted(),
        nativeLibraries = libraries.map { it.path },
        nativeLibraryDetails = libraries,
        declaredPermissions = info.permissions?.map { it.name }.orEmpty().sorted(),
        activities = info.activities?.map { ApkComponent(it.name, it.exported) }.orEmpty(),
        services = info.services?.map { ApkComponent(it.name, it.exported) }.orEmpty(),
        receivers = info.receivers?.map { ApkComponent(it.name, it.exported) }.orEmpty(),
        providers = info.providers?.map { ApkComponent(it.name, it.exported) }.orEmpty(),
        metaData = readMetaData(appInfo?.metaData),
        jsonResources = inspectJsonResources(apk),
        sha256 = sha256,
        signatureVerified = signature.verified,
        signatureSchemes = signature.schemes,
        bundleApks = bundleApks.orEmpty(),
        multiArch = appInfo?.let { (it.flags and FLAG_MULTIARCH) != 0 },
        application = application,
      )
    } finally {
      picked.delete()
      extractedBase?.delete()
    }
  }

  /** Copies the picked document to [dest] and returns its SHA-256 as lowercase hex. */
  private fun copyWithSha256(uri: Uri, dest: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val input =
      context.contentResolver.openInputStream(uri)
        ?: throw IOException("Could not open the selected file.")
    input.use { source ->
      dest.outputStream().use { sink ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
          val read = source.read(buffer)
          if (read < 0) break
          digest.update(buffer, 0, read)
          sink.write(buffer, 0, read)
        }
      }
    }
    return digest.digest().toHex()
  }

  /**
   * Returns the .apk entry names when [file] is a bundle of APKs (no AndroidManifest.xml of its
   * own), or null when [file] is a plain APK.
   */
  private fun listBundleApks(file: File): List<String>? =
    ZipFile(file).use { zip ->
      val names = zip.entries().asSequence().map { it.name }.toList()
      if ("AndroidManifest.xml" in names) {
        null
      } else {
        val apks = names.filter { it.endsWith(".apk", ignoreCase = true) }.sorted()
        if (apks.isEmpty()) throw IOException("The file is neither an APK nor a bundle of APKs.")
        apks
      }
    }

  private fun chooseBaseApk(apks: List<String>): String {
    fun simple(name: String) = name.substringAfterLast('/')
    return apks.firstOrNull { simple(it).equals("base.apk", ignoreCase = true) }
      ?: apks.firstOrNull { name ->
        val simple = simple(name)
        !simple.startsWith("split_") && !simple.startsWith("config.")
      }
      ?: apks.first()
  }

  private fun extractEntry(zipFile: File, entryName: String, dest: File) {
    ZipFile(zipFile).use { zip ->
      val entry = zip.getEntry(entryName) ?: throw IOException("Missing entry $entryName.")
      zip.getInputStream(entry).use { source ->
        dest.outputStream().use { sink -> source.copyTo(sink) }
      }
    }
  }

  @Suppress("DEPRECATION")
  private fun getPackageInfo(path: String): PackageInfo? =
    context.packageManager.getPackageArchiveInfo(
      path,
      PackageManager.GET_PERMISSIONS or
        PackageManager.GET_ACTIVITIES or
        PackageManager.GET_SERVICES or
        PackageManager.GET_RECEIVERS or
        PackageManager.GET_PROVIDERS or
        PackageManager.GET_META_DATA,
    )

  @Suppress("DEPRECATION")
  private fun getVersionCode(info: PackageInfo): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
    else info.versionCode.toLong()

  @Suppress("DEPRECATION")
  private fun readMetaData(bundle: android.os.Bundle?): Map<String, String> {
    if (bundle == null) return emptyMap()
    return bundle.keySet().sorted().associateWith { key -> bundle.get(key)?.toString().orEmpty() }
  }

  private fun inspectNativeLibraries(apk: File): List<NativeLibraryInfo> =
    try {
      val layout = ZipLayout.read(apk) { isNativeLibraryPath(it) }
      ZipFile(apk).use { zip ->
        zip
          .entries()
          .asSequence()
          .filter { isNativeLibraryPath(it.name) }
          .map { entry ->
            val abi = entry.name.split('/')[1]
            val stored = entry.method == ZipEntry.STORED
            val zipAligned =
              if (stored) {
                layout[entry.name]?.let { it.dataOffset % ElfReader.SIXTEEN_KB == 0L }
              } else {
                null
              }
            val elfAligned =
              ElfReader.parse(readHead(zip, entry, ELF_HEADER_BYTES))?.let {
                ElfReader.isLoadAlignedTo(it)
              }
            NativeLibraryInfo(
              path = entry.name,
              abi = abi,
              is64Bit = InspectionRules.is64BitAbi(abi),
              storedUncompressed = stored,
              zipAlignedTo16Kb = zipAligned,
              elfAlignedTo16Kb = elfAligned,
            )
          }
          .sortedBy { it.path }
          .toList()
      }
    } catch (_: Exception) {
      emptyList()
    }

  /** JSON files (res/raw, assets, ...); Lottie animations are told apart by their header keys. */
  private fun inspectJsonResources(apk: File): List<ApkResourceFile> =
    try {
      ZipFile(apk).use { zip ->
        zip
          .entries()
          .asSequence()
          .filter { !it.isDirectory && ResourceRules.isJsonResource(it.name) }
          .take(MAX_JSON_RESOURCES)
          .map { entry ->
            val head = readHead(zip, entry, JSON_HEAD_BYTES).toString(Charsets.UTF_8)
            ApkResourceFile(entry.name, entry.size, ResourceRules.kindOf(head))
          }
          .sortedBy { it.path }
          .toList()
      }
    } catch (_: Exception) {
      emptyList()
    }

  private fun isNativeLibraryPath(name: String): Boolean =
    name.startsWith("lib/") && name.endsWith(".so") && name.split('/').size == 3

  /** Reads up to [limit] bytes from the start of a zip entry. */
  private fun readHead(zip: ZipFile, entry: ZipEntry, limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    var total = 0
    zip.getInputStream(entry).use { stream ->
      while (total < limit) {
        val read = stream.read(buffer, total, limit - total)
        if (read < 0) break
        total += read
      }
    }
    return buffer.copyOf(total)
  }

  private class SignatureResult(
    val verified: Boolean,
    val schemes: List<String>,
    val certificateSha256: List<String>,
  )

  private fun verifySignatures(apk: File): SignatureResult =
    try {
      val builder = ApkVerifier.Builder(apk)
      // apksig's v1 (JAR) verifier calls Type.getTypeName(), which exists only from API 26. On
      // Android 7.x the platform itself ignores v1 when a v2/v3 block is present, so check only
      // what this device would use.
      if (Build.VERSION.SDK_INT in 1 until Build.VERSION_CODES.O) { // 0 on host JVM tests
        builder.setMinCheckedPlatformVersion(Build.VERSION.SDK_INT)
      }
      val result = builder.build().verify()
      val schemes = buildList {
        if (result.isVerifiedUsingV1Scheme) add("v1")
        if (result.isVerifiedUsingV2Scheme) add("v2")
        if (result.isVerifiedUsingV3Scheme) add("v3")
      }
      val certificates =
        result.signerCertificates.orEmpty().map {
          MessageDigest.getInstance("SHA-256").digest(it.encoded).toHex()
        }
      SignatureResult(result.isVerified, schemes, certificates)
    } catch (_: Exception) {
      SignatureResult(false, emptyList(), emptyList())
    } catch (_: LinkageError) {
      // Missing platform API on an old Android release: report "not verified", never crash.
      SignatureResult(false, emptyList(), emptyList())
    }

  private fun queryDisplayName(uri: Uri): String? =
    try {
      context.contentResolver
        .query(
          uri,
          arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
          null,
          null,
          null,
        )
        ?.use { cursor ->
          if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
          } else {
            null
          }
        }
    } catch (_: Exception) {
      null
    }

  private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

  private companion object {
    const val ELF_HEADER_BYTES = 64 * 1024
    const val JSON_HEAD_BYTES = 4 * 1024
    const val MAX_JSON_RESOURCES = 500

    /** Value of the hidden ApplicationInfo.FLAG_MULTIARCH (android:multiarch), bit 31. */
    const val FLAG_MULTIARCH = 1 shl 31
  }
}
