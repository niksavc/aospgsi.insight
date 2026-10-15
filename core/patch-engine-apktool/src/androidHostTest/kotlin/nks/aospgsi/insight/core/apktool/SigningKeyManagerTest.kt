package nks.aospgsi.insight.core.apktool

import com.android.apksig.ApkVerifier
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Stands in for the Keystore-backed box: reversible, and different from the plain text. */
private class XorBox : SecretBox {
  override fun seal(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()

  override fun open(sealed: ByteArray) = seal(sealed)
}

class SigningKeyManagerTest {
  private fun tempDir(): File = kotlin.io.path.createTempDirectory("signing").toFile()

  private fun softwareKey(subject: String = "CN=Device Key, O=test"): SigningKey {
    val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    return SigningKey(pair.private, listOf(SelfSignedCertificate.create(pair, subject)))
  }

  private fun manager(dir: File, device: SigningKey = softwareKey()) =
    SigningKeyManager(File(dir, "signing"), XorBox()) { device }

  private fun sha256(key: SigningKey): String =
    MessageDigest.getInstance("SHA-256").digest(key.certificates.first().encoded).joinToString("") {
      "%02x".format(it)
    }

  @Test
  fun selfSignedCertificateIsValidAndBelongsToItsKey() {
    val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val certificate = SelfSignedCertificate.create(pair, "CN=AOSP GSI Insight, O=niksavc, C=RU")
    certificate.verify(pair.public)
    certificate.checkValidity()
    assertEquals("CN=AOSP GSI Insight,O=niksavc,C=RU", certificate.subjectX500Principal.name)
    assertEquals(certificate.subjectX500Principal, certificate.issuerX500Principal)
    assertEquals(
      2100,
      java.util.Calendar.getInstance().run {
        time = certificate.notAfter
        get(1)
      },
    )
    assertTrue(certificate.serialNumber.signum() > 0)
  }

  @Test
  fun pkcs12RoundTripKeepsKeyAndCertificate() {
    val key = softwareKey()
    val out = ByteArrayOutputStream()
    Pkcs12.write(key, "secret1".toCharArray(), out)
    val read = Pkcs12.read(ByteArrayInputStream(out.toByteArray()), "secret1".toCharArray())
    assertEquals(sha256(key), sha256(read))
    assertContentEquals(key.privateKey.encoded, read.privateKey.encoded)
  }

  @Test
  fun wrongPasswordAndGarbageAreTold() {
    val out = ByteArrayOutputStream()
    Pkcs12.write(softwareKey(), "secret1".toCharArray(), out)
    val wrong =
      assertFailsWith<SigningKeyException> {
        Pkcs12.read(ByteArrayInputStream(out.toByteArray()), "other".toCharArray())
      }
    assertEquals(SigningKeyException.Reason.WRONG_PASSWORD, wrong.reason)
    val garbage =
      assertFailsWith<SigningKeyException> {
        Pkcs12.read(ByteArrayInputStream(ByteArray(300) { 7 }), "x".toCharArray())
      }
    assertEquals(SigningKeyException.Reason.UNREADABLE, garbage.reason)
  }

  @Test
  fun aCertificateThatDoesNotMatchTheKeyIsRejected() {
    val mismatched = SigningKey(softwareKey().privateKey, softwareKey().certificates)
    val out = ByteArrayOutputStream()
    Pkcs12.write(mismatched, "secret1".toCharArray(), out)
    val error =
      assertFailsWith<SigningKeyException> {
        Pkcs12.read(ByteArrayInputStream(out.toByteArray()), "secret1".toCharArray())
      }
    assertEquals(SigningKeyException.Reason.KEY_CERTIFICATE_MISMATCH, error.reason)
  }

  @Test
  fun readsAFileMadeByKeytool() {
    val dir = tempDir()
    try {
      val file = File(dir, "tool.p12")
      val keytool = File(System.getProperty("java.home"), "bin/keytool").path
      val process =
        ProcessBuilder(
            keytool,
            "-genkeypair",
            "-alias",
            "mykey",
            "-keyalg",
            "RSA",
            "-keysize",
            "2048",
            "-validity",
            "3650",
            "-dname",
            "CN=Tool",
            "-keystore",
            file.path,
            "-storetype",
            "PKCS12",
            "-storepass",
            "toolpass",
            "-keypass",
            "toolpass",
          )
          .redirectErrorStream(true)
          .start()
      process.inputStream.readBytes()
      check(process.waitFor() == 0) { "keytool failed" }
      val key = file.inputStream().use { Pkcs12.read(it, "toolpass".toCharArray()) }
      assertEquals("CN=Tool", key.certificates.first().subjectX500Principal.name)
      val named = file.inputStream().use { Pkcs12.read(it, "toolpass".toCharArray(), "mykey") }
      assertEquals(sha256(key), sha256(named))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun startsOnTheDeviceKeyWhichCannotBeExported() {
    val dir = tempDir()
    try {
      val device = softwareKey()
      val manager = manager(dir, device)
      val info = manager.info()
      assertEquals(SigningKeyManager.Source.DEVICE, info.source)
      assertEquals(false, info.exportable)
      assertEquals(sha256(device), info.sha256)
      val error =
        assertFailsWith<SigningKeyException> {
          manager.export(ByteArrayOutputStream(), "secret1".toCharArray())
        }
      assertEquals(SigningKeyException.Reason.NOT_EXPORTABLE, error.reason)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun exportedKeyCanBeImportedAfterAReinstallAndStillSigns() {
    val first = tempDir()
    val second = tempDir()
    try {
      val before = manager(first)
      val created = before.createExportable()
      assertEquals(SigningKeyManager.Source.STORED, created.source)
      assertTrue(created.exportable)
      assertTrue(created.subject.contains("AOSP GSI Insight"))
      assertNotEquals(sha256(softwareKey()), created.sha256)

      val file = ByteArrayOutputStream()
      before.export(file, "my backup pass".toCharArray())

      // A fresh install: new directory, another device key.
      val after = manager(second)
      assertNotEquals(created.sha256, after.info().sha256)
      val imported =
        after.import(ByteArrayInputStream(file.toByteArray()), "my backup pass".toCharArray())
      assertEquals(created.sha256, imported.sha256)

      // The imported key signs an APK that Android's verifier accepts, under the same certificate.
      val input = File(second, "in.apk")
      ZipOutputStream(input.outputStream()).use {
        it.putNextEntry(ZipEntry("AndroidManifest.xml"))
        it.write(emptyManifest())
        it.closeEntry()
        it.putNextEntry(ZipEntry("classes.dex"))
        it.write(ByteArray(64) { b -> b.toByte() })
        it.closeEntry()
      }
      val output = File(second, "out.apk")
      ApkSigning.sign(input, output, after.active(), minSdk = 24)
      val result = ApkVerifier.Builder(output).setMinCheckedPlatformVersion(24).build().verify()
      assertTrue(result.isVerified, "${result.errors} ${result.allErrors}")
      assertEquals(
        created.sha256,
        MessageDigest.getInstance("SHA-256")
          .digest(result.signerCertificates.first().encoded)
          .joinToString("") { "%02x".format(it) },
      )
    } finally {
      first.deleteRecursively()
      second.deleteRecursively()
    }
  }

  @Test
  fun aFailedImportLeavesTheCurrentKeyAlone() {
    val dir = tempDir()
    try {
      val manager = manager(dir)
      val created = manager.createExportable()
      val other = ByteArrayOutputStream()
      Pkcs12.write(softwareKey(), "secret1".toCharArray(), other)
      assertFailsWith<SigningKeyException> {
        manager.import(ByteArrayInputStream(other.toByteArray()), "wrong".toCharArray())
      }
      assertEquals(created.sha256, manager.info().sha256)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun goingBackToTheDeviceKeyForgetsTheStoredOne() {
    val dir = tempDir()
    try {
      val device = softwareKey()
      val manager = manager(dir, device)
      manager.createExportable()
      manager.useDeviceKey()
      val info = manager.info()
      assertEquals(SigningKeyManager.Source.DEVICE, info.source)
      assertEquals(sha256(device), info.sha256)
      assertTrue(!File(dir, "signing/key.p12").exists())
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun anUnreadableStoredKeyFallsBackToTheDeviceKeyAndSaysSo() {
    val dir = tempDir()
    try {
      val device = softwareKey()
      val manager = manager(dir, device)
      manager.createExportable()
      File(dir, "signing/key.secret").writeBytes(ByteArray(40) { 1 })
      val info = manager.info()
      assertEquals(SigningKeyManager.Source.DEVICE, info.source)
      assertTrue(info.storedKeyUnreadable)
      assertEquals(sha256(device), sha256(manager.active()))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun theStoredKeyFileIsNotProtectedByAGuessablePassword() {
    val dir = tempDir()
    try {
      manager(dir).createExportable()
      val file = File(dir, "signing/key.p12")
      val store = KeyStore.getInstance("PKCS12")
      assertFailsWith<java.io.IOException> {
        file.inputStream().use { store.load(it, "".toCharArray()) }
      }
      assertFailsWith<java.io.IOException> {
        file.inputStream().use { store.load(it, "android".toCharArray()) }
      }
    } finally {
      dir.deleteRecursively()
    }
  }

  /** The smallest binary manifest apksig accepts: an empty `<manifest/>` element. */
  private fun emptyManifest(): ByteArray {
    val buffer = ByteBuffer.allocate(120).order(ByteOrder.LITTLE_ENDIAN)
    // File header.
    buffer.putShort(0x0003).putShort(8).putInt(120)
    // String pool with one UTF-16 string, "manifest".
    buffer.putShort(0x0001).putShort(28).putInt(52)
    buffer.putInt(1).putInt(0).putInt(0).putInt(32).putInt(0)
    buffer.putInt(0)
    buffer.putShort(8)
    "manifest".forEach { buffer.putChar(it) }
    buffer.putShort(0)
    // <manifest>
    buffer.putShort(0x0102).putShort(16).putInt(36).putInt(1).putInt(-1)
    buffer.putInt(-1).putInt(0)
    buffer.putShort(20).putShort(20).putShort(0).putShort(0).putShort(0).putShort(0)
    // </manifest>
    buffer.putShort(0x0103).putShort(16).putInt(24).putInt(1).putInt(-1)
    buffer.putInt(-1).putInt(0)
    return buffer.array()
  }
}
