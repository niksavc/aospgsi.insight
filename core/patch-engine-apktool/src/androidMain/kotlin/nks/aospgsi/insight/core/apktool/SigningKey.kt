package nks.aospgsi.insight.core.apktool

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

/** A private key with its certificate chain, used to sign patched APKs. */
class SigningKey(val privateKey: PrivateKey, val certificates: List<X509Certificate>)

/**
 * Keeps the signing key inside the Android Keystore: it can sign but never be exported, and it
 * survives as long as the app is installed. The same key is reused so that a patched app can later
 * be updated by another patched build.
 */
object AndroidKeystoreSigningKey {
  private const val PROVIDER = "AndroidKeyStore"
  const val DEFAULT_ALIAS = "aospgsi-signing"

  /**
   * Who the certificate says it belongs to. It is only a label (Android trusts the key, not the
   * name), so it says plainly that the key is this app's own, not Google's or the original
   * developer's. Applies to keys created from now on; an existing key keeps its subject.
   */
  const val SUBJECT = "CN=AOSP GSI Insight, O=niksavc, C=RU"

  fun getOrCreate(alias: String = DEFAULT_ALIAS): SigningKey {
    val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
    if (!store.containsAlias(alias)) generate(alias)
    val key = store.getKey(alias, null) as PrivateKey
    val chain = store.getCertificateChain(alias).map { it as X509Certificate }
    return SigningKey(key, chain)
  }

  private fun generate(alias: String) {
    val spec =
      KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
        .setKeySize(2048)
        // SHA-1 is needed only for the v1 (JAR) signature of APKs whose minSdk is below 18, where
        // Android does not understand SHA-256 digests; v2/v3 use SHA-256 and above.
        .setDigests(
          KeyProperties.DIGEST_SHA1,
          KeyProperties.DIGEST_SHA256,
          KeyProperties.DIGEST_SHA512,
        )
        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
        .setCertificateSubject(X500Principal(SUBJECT))
        .setCertificateNotAfter(Date(4_102_444_800_000L)) // 2100-01-01
        .build()
    KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, PROVIDER).run {
      initialize(spec)
      generateKeyPair()
    }
  }
}
