package nks.aospgsi.insight.core.apktool

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.security.auth.x500.X500Principal

/**
 * Builds a self-signed X.509 certificate for a software RSA key. Android has no public API for this
 * (only the Keystore can make certificates, and its keys cannot be exported), and the certificate
 * is only a label for the key, so a minimal DER encoder is enough: no extensions, SHA-256 with RSA.
 */
internal object SelfSignedCertificate {
  /** 2100-01-01, like the certificate of the device key. */
  const val DEFAULT_NOT_AFTER_MILLIS = 4_102_444_800_000L

  // sha256WithRSAEncryption (1.2.840.113549.1.1.11) with NULL parameters.
  private val SHA256_WITH_RSA =
    sequence(
      tlv(
        0x06,
        byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 1, 1, 11),
      ),
      tlv(0x05, ByteArray(0)),
    )

  fun create(
    keyPair: KeyPair,
    subject: String,
    notBefore: Date = Date(System.currentTimeMillis() - ONE_DAY_MILLIS),
    notAfter: Date = Date(DEFAULT_NOT_AFTER_MILLIS),
  ): X509Certificate {
    val name = X500Principal(subject).encoded
    val serial = BigInteger(127, SecureRandom()).add(BigInteger.ONE)
    val tbs =
      sequence(
        tlv(0xA0, tlv(0x02, byteArrayOf(2))), // version: v3
        tlv(0x02, serial.toByteArray()),
        SHA256_WITH_RSA,
        name, // issuer
        sequence(time(notBefore), time(notAfter)),
        name, // subject
        keyPair.public.encoded, // SubjectPublicKeyInfo
      )
    val signature =
      Signature.getInstance("SHA256withRSA").run {
        initSign(keyPair.private)
        update(tbs)
        sign()
      }
    val certificate = sequence(tbs, SHA256_WITH_RSA, tlv(0x03, byteArrayOf(0) + signature))
    return CertificateFactory.getInstance("X.509")
      .generateCertificate(ByteArrayInputStream(certificate)) as X509Certificate
  }

  private const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000

  private fun time(date: Date): ByteArray {
    val calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }
    // RFC 5280: UTCTime up to 2049, GeneralizedTime from 2050.
    val generalized = calendar.get(java.util.Calendar.YEAR) >= 2050
    val format =
      SimpleDateFormat(if (generalized) "yyyyMMddHHmmss'Z'" else "yyMMddHHmmss'Z'", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return tlv(if (generalized) 0x18 else 0x17, format.format(date).toByteArray(Charsets.US_ASCII))
  }

  private fun sequence(vararg parts: ByteArray): ByteArray =
    tlv(0x30, parts.fold(ByteArray(0)) { all, part -> all + part })

  private fun tlv(tag: Int, body: ByteArray): ByteArray =
    byteArrayOf(tag.toByte()) + length(body.size) + body

  private fun length(size: Int): ByteArray =
    when {
      size < 0x80 -> byteArrayOf(size.toByte())
      size < 0x100 -> byteArrayOf(0x81.toByte(), size.toByte())
      else -> byteArrayOf(0x82.toByte(), (size shr 8).toByte(), size.toByte())
    }
}
