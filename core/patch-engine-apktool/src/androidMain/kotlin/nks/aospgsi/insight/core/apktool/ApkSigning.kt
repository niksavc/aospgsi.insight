package nks.aospgsi.insight.core.apktool

import android.os.Build
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest

/** Signing, verification and hashing shared by the patch and repair pipelines. */
internal object ApkSigning {
  /**
   * Signs with v1, v2 and v3. [minSdk] null lets apksig read it from the APK's own manifest, which
   * is what a repair of an arbitrary APK needs.
   */
  fun sign(input: File, output: File, key: SigningKey, minSdk: Int?) {
    val signer = ApkSigner.SignerConfig.Builder("aospgsi", key.privateKey, key.certificates).build()
    val builder =
      ApkSigner.Builder(listOf(signer))
        .setInputApk(input)
        .setOutputApk(output)
        .setV1SigningEnabled(true)
        .setV2SigningEnabled(true)
        .setV3SigningEnabled(true)
        .setV4SigningEnabled(false)
        .setCreatedBy("AOSP GSI Insight")
        // Keep the 4-byte / 16 KiB alignment that ZipAligner established.
        .setAlignmentPreserved(true)
        .setLibraryPageAlignmentBytes(ZipAligner.NATIVE_LIB_ALIGNMENT)
    if (minSdk != null) builder.setMinSdkVersion(minSdk)
    builder.build().sign()
  }

  /**
   * apksig's v1 verifier needs Type.getTypeName() (API 26+). On older releases only the schemes
   * that release itself would use are checked, and if the verifier still cannot run, the platform's
   * own package installer is the check: it rejects a bad signature at install time.
   */
  fun verify(apk: File): Boolean =
    try {
      val builder = ApkVerifier.Builder(apk)
      if (Build.VERSION.SDK_INT in 1 until Build.VERSION_CODES.O) { // 0 on host JVM tests
        builder.setMinCheckedPlatformVersion(Build.VERSION.SDK_INT)
      }
      builder.build().verify().isVerified
    } catch (_: LinkageError) {
      true
    }

  fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(64 * 1024)
      while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  /** The message plus the chain of causes, since wrappers such as apksig hide the real reason. */
  fun describe(error: Throwable): String {
    val parts = mutableListOf<String>()
    var current: Throwable? = error
    while (current != null && parts.size < 4) {
      parts += current.message ?: current.javaClass.simpleName
      current = current.cause?.takeIf { it !== current }
    }
    return parts.joinToString(" <- ")
  }
}
