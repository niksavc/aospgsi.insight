package nks.aospgsi.insight.core.apktool

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Chooses the key that signs patched and repaired APKs.
 *
 * By default that is the Android Keystore key ([deviceKey]): safe, but it cannot be exported and is
 * deleted with the app. The user can instead create an exportable key or import a PKCS #12 file; it
 * is then stored in [dir] with a random password that only the device's Keystore ([box]) can open.
 * Keys exported from here can be imported after a reinstall, so patched apps stay updatable.
 */
class SigningKeyManager(
  private val dir: File,
  private val box: SecretBox,
  private val deviceKey: () -> SigningKey,
) {
  enum class Source {
    DEVICE,
    STORED,
  }

  data class Info(
    val source: Source,
    val subject: String,
    val sha256: String,
    val notAfterMillis: Long,
    /** True for a stored key, which can be exported; the device key never can. */
    val exportable: Boolean,
    /** A stored key exists but could not be read, so the device key is used instead. */
    val storedKeyUnreadable: Boolean,
  )

  private val keyFile
    get() = File(dir, KEY_FILE)

  private val secretFile
    get() = File(dir, SECRET_FILE)

  /** The key to sign with: the stored one if there is a readable one, else the device key. */
  fun active(): SigningKey = loadStored() ?: deviceKey()

  fun info(): Info {
    val stored = loadStored()
    val key = stored ?: deviceKey()
    val certificate = key.certificates.first()
    return Info(
      source = if (stored != null) Source.STORED else Source.DEVICE,
      subject = certificate.subjectX500Principal.name,
      sha256 = sha256Hex(certificate.encoded),
      notAfterMillis = certificate.notAfter.time,
      exportable = stored != null,
      storedKeyUnreadable = stored == null && (keyFile.exists() || secretFile.exists()),
    )
  }

  /** Makes a new software RSA key, stores it and uses it from now on. */
  fun createExportable(subject: String = AndroidKeystoreSigningKey.SUBJECT): Info {
    val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val certificate = SelfSignedCertificate.create(pair, subject)
    store(SigningKey(pair.private, listOf(certificate)))
    return info()
  }

  /** Imports a PKCS #12 file and uses its key from now on. Throws [SigningKeyException]. */
  fun import(input: InputStream, password: CharArray, alias: String? = null): Info {
    store(Pkcs12.read(input, password, alias))
    return info()
  }

  /**
   * Writes the stored key as PKCS #12 protected by [password]. The device key cannot be exported.
   */
  fun export(out: OutputStream, password: CharArray) {
    val key =
      loadStored()
        ?: throw SigningKeyException(
          SigningKeyException.Reason.NOT_EXPORTABLE,
          "Only an exportable key can be exported.",
        )
    Pkcs12.write(key, password, out)
  }

  /** Forgets the stored key and goes back to the device key. */
  fun useDeviceKey() {
    keyFile.delete()
    secretFile.delete()
  }

  private fun store(key: SigningKey) {
    dir.mkdirs()
    val random = ByteArray(24).also { SecureRandom().nextBytes(it) }
    val password = random.joinToString("") { "%02x".format(it) }
    val keyTmp = File(dir, "$KEY_FILE.tmp")
    val secretTmp = File(dir, "$SECRET_FILE.tmp")
    try {
      keyTmp.outputStream().use { Pkcs12.write(key, password.toCharArray(), it) }
      secretTmp.writeBytes(box.seal(password.toByteArray(Charsets.UTF_8)))
      check(keyTmp.renameTo(keyFile) || (keyFile.delete() && keyTmp.renameTo(keyFile))) {
        "Could not store the key."
      }
      check(
        secretTmp.renameTo(secretFile) || (secretFile.delete() && secretTmp.renameTo(secretFile)),
      ) {
        "Could not store the key."
      }
    } finally {
      keyTmp.delete()
      secretTmp.delete()
    }
  }

  private fun loadStored(): SigningKey? =
    try {
      if (!keyFile.isFile || !secretFile.isFile) {
        null
      } else {
        val password = String(box.open(secretFile.readBytes()), Charsets.UTF_8).toCharArray()
        keyFile.inputStream().use { Pkcs12.read(it, password) }
      }
    } catch (_: Exception) {
      null
    }

  private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

  private companion object {
    const val KEY_FILE = "key.p12"
    const val SECRET_FILE = "key.secret"
  }
}
