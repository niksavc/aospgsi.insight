package nks.aospgsi.insight.core.apktool

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.UnrecoverableKeyException
import java.security.cert.X509Certificate

/** Why a signing key could not be imported or exported. */
class SigningKeyException(val reason: Reason, message: String, cause: Throwable? = null) :
  Exception(message, cause) {
  enum class Reason {
    WRONG_PASSWORD,
    UNREADABLE,
    NO_KEY,
    KEY_CERTIFICATE_MISMATCH,
    NOT_EXPORTABLE,
  }
}

/** Reads and writes a [SigningKey] as a password-protected PKCS #12 file (.p12 / .pfx). */
object Pkcs12 {
  private const val TYPE = "PKCS12"
  const val DEFAULT_ALIAS = "aospgsi"

  fun write(
    key: SigningKey,
    password: CharArray,
    out: OutputStream,
    alias: String = DEFAULT_ALIAS,
  ) {
    val store = KeyStore.getInstance(TYPE)
    store.load(null, null)
    store.setKeyEntry(alias, key.privateKey, password, key.certificates.toTypedArray())
    store.store(out, password)
  }

  /**
   * Loads the first private key entry (or the one named [alias]) and checks that its certificate
   * really belongs to the key, so a wrong pairing is caught here and not when an APK is signed.
   */
  fun read(input: InputStream, password: CharArray, alias: String? = null): SigningKey {
    val store = KeyStore.getInstance(TYPE)
    try {
      store.load(input, password)
    } catch (e: IOException) {
      // BouncyCastle and the JDK both report a bad password as an IOException whose cause (or
      // message) says so; anything else is a file that is not PKCS #12 at all.
      val wrongPassword =
        e.cause is UnrecoverableKeyException ||
          e.message.orEmpty().let {
            "password" in it.lowercase() || "mac invalid" in it.lowercase()
          }
      throw SigningKeyException(
        if (wrongPassword) SigningKeyException.Reason.WRONG_PASSWORD
        else SigningKeyException.Reason.UNREADABLE,
        e.message ?: "The file could not be read.",
        e,
      )
    } catch (e: Exception) {
      throw SigningKeyException(
        SigningKeyException.Reason.UNREADABLE,
        e.message ?: "The file could not be read.",
        e,
      )
    }

    val name =
      alias?.takeIf { store.isKeyEntry(it) }
        ?: store.aliases().toList().firstOrNull { store.isKeyEntry(it) }
        ?: throw SigningKeyException(
          SigningKeyException.Reason.NO_KEY,
          "The file holds no private key.",
        )
    val key =
      try {
        store.getKey(name, password) as? PrivateKey
      } catch (e: Exception) {
        throw SigningKeyException(
          SigningKeyException.Reason.WRONG_PASSWORD,
          e.message ?: "The key password was incorrect.",
          e,
        )
      } ?: throw SigningKeyException(SigningKeyException.Reason.NO_KEY, "The entry has no key.")
    val chain = store.getCertificateChain(name)?.map { it as X509Certificate }.orEmpty()
    if (chain.isEmpty()) {
      throw SigningKeyException(
        SigningKeyException.Reason.NO_KEY,
        "The key has no certificate.",
      )
    }
    if (!belongsTo(key, chain.first())) {
      throw SigningKeyException(
        SigningKeyException.Reason.KEY_CERTIFICATE_MISMATCH,
        "The certificate does not belong to the private key.",
      )
    }
    return SigningKey(key, chain)
  }

  /** Signs a test message with [key] and verifies it with the certificate's public key. */
  private fun belongsTo(key: PrivateKey, certificate: X509Certificate): Boolean =
    try {
      val algorithm =
        when (key.algorithm.uppercase()) {
          "EC" -> "SHA256withECDSA"
          "DSA" -> "SHA256withDSA"
          else -> "SHA256withRSA"
        }
      val message = ByteArray(32) { it.toByte() }
      val signature =
        Signature.getInstance(algorithm).run {
          initSign(key)
          update(message)
          sign()
        }
      Signature.getInstance(algorithm).run {
        initVerify(certificate.publicKey)
        update(message)
        verify(signature)
      }
    } catch (_: Exception) {
      false
    }
}
