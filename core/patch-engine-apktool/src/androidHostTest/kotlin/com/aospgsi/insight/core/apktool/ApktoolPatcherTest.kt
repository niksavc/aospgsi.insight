package com.aospgsi.insight.core.apktool

import brut.androlib.ApkBuilder
import brut.androlib.ApkDecoder
import brut.androlib.Config
import brut.androlib.meta.ApkInfo
import com.android.apksig.ApkVerifier
import com.aospgsi.insight.core.model.PatchProgress
import com.aospgsi.insight.core.model.PatchResult
import com.aospgsi.insight.core.patchapi.ApkPatch
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * End-to-end test of the real Apktool pipeline on the host JVM, using Apktool's bundled desktop
 * aapt2. A tiny APK is built from a hand-written project, patched, and then decoded again to check
 * the result independently of the code under test.
 */
class ApktoolPatcherTest {
  private fun tempDir(): File = kotlin.io.path.createTempDirectory("patcher").toFile()

  private fun generateKey(dir: File): SigningKey {
    val keystore = File(dir, "test.p12")
    val keytool = File(System.getProperty("java.home"), "bin/keytool").path
    val process =
      ProcessBuilder(
          keytool,
          "-genkeypair",
          "-alias",
          "t",
          "-keyalg",
          "RSA",
          "-keysize",
          "2048",
          "-validity",
          "3650",
          "-dname",
          "CN=Test",
          "-keystore",
          keystore.path,
          "-storetype",
          "PKCS12",
          "-storepass",
          "password",
          "-keypass",
          "password",
        )
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "keytool failed: $output" }
    val store = KeyStore.getInstance("PKCS12")
    keystore.inputStream().use { store.load(it, "password".toCharArray()) }
    val key = store.getKey("t", "password".toCharArray()) as PrivateKey
    val chain = store.getCertificateChain("t").map { it as X509Certificate }
    return SigningKey(key, chain)
  }

  private fun buildFixtureApk(dir: File, framework: File): File {
    val project = File(dir, "fixture-src")
    File(project, "res/values").mkdirs()
    File(project, "assets").mkdirs()
    File(project, "lib/arm64-v8a").mkdirs()
    File(project, "apktool.yml")
      .writeText(
        """
        version: ${ApktoolPatcher.APKTOOL_VERSION}
        apkFileName: fixture.apk
        usesFramework:
          ids:
          - 1
        sdkInfo:
          minSdkVersion: 24
          targetSdkVersion: 30
        versionInfo:
          versionCode: 1
          versionName: 1.0
        """
          .trimIndent() + "\n",
      )
    File(project, "AndroidManifest.xml")
      .writeText(
        """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.fixture">
          <uses-permission android:name="android.permission.INTERNET"/>
          <permission android:name="com.example.fixture.PRIVATE" android:protectionLevel="signature"/>
          <application android:label="@string/app_name" android:name=".App">
            <activity android:name=".Main" android:exported="true"/>
            <provider android:name="Files" android:authorities="com.example.fixture.files" android:exported="false"/>
          </application>
        </manifest>
        """
          .trimIndent(),
      )
    File(project, "res/values/strings.xml")
      .writeText(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
          "<resources><string name=\"app_name\">Fixture</string></resources>",
      )
    File(project, "assets/hello.txt").writeText("hello")
    File(project, "lib/arm64-v8a/libfake.so").writeBytes(ByteArray(3001) { it.toByte() })

    val config =
      Config(ApktoolPatcher.APKTOOL_VERSION).apply {
        setForced(true)
        setJobs(1)
        setFrameworkDirectory(framework.path)
      }
    val apk = File(dir, "fixture.apk")
    ApkBuilder(project, config).build(apk)
    return apk
  }

  private fun decodeManifest(apk: File, dir: File, framework: File): File {
    val config =
      Config(ApktoolPatcher.APKTOOL_VERSION).apply {
        setForced(true)
        setJobs(1)
        setFrameworkDirectory(framework.path)
        setDecodeSources(Config.DecodeSources.NONE)
        setDecodeResources(Config.DecodeResources.ONLY_MANIFEST)
      }
    val out = File(dir, "decoded-out")
    ApkDecoder(apk, config).decode(out)
    return out
  }

  @Test
  fun patchesRebuildsAlignsSignsAndVerifies() {
    val dir = tempDir()
    try {
      val framework = File(dir, "framework")
      framework.mkdirs()
      val fixture = buildFixtureApk(dir, framework)
      val key = generateKey(dir)

      val patcher = ApktoolPatcher(ApktoolEnvironment(File(dir, "work"), framework, jobs = 1))
      val stages = mutableListOf<PatchProgress>()
      val output = File(dir, "patched.apk")
      val result =
        patcher.patch(
          fixture,
          output,
          listOf(
            ApkPatch.SetPackageName("com.example.clone"),
            ApkPatch.SetTargetSdk(34),
            ApkPatch.SetAppLabel("Clone"),
            ApkPatch.AddPermission("android.permission.CAMERA"),
            ApkPatch.SetVersion(7, "2.0"),
          ),
          key,
        ) {
          stages += it
        }

      val success = assertIs<PatchResult.Success>(result, "patch failed: $result")
      assertEquals(output.absolutePath, success.outputPath)
      assertEquals(64, success.sha256?.length)
      assertEquals(100, stages.last().percent)
      assertTrue(stages.map { it.percent }.zipWithNext().all { (a, b) -> a <= b })

      // Independent checks of the result.
      assertTrue(ApkVerifier.Builder(output).build().verify().isVerified)

      val all = ZipOffsets.read(output)
      val offsets = all.associateBy { it.name }
      val layout = all.joinToString { "${it.name}@${it.dataOffset}/m${it.method}" }
      assertEquals(
        0L,
        offsets.getValue("resources.arsc").dataOffset % 4,
        "resources.arsc misaligned: $layout",
      )
      assertEquals(
        0L,
        offsets.getValue("lib/arm64-v8a/libfake.so").dataOffset % 16384,
        "libfake.so misaligned: $layout",
      )

      val decoded = decodeManifest(output, dir, framework)
      val doc = ManifestEditor.parse(File(decoded, "AndroidManifest.xml"))
      val manifest = doc.documentElement
      assertEquals("com.example.clone", manifest.getAttribute("package"))
      fun tagAttr(tag: String, attr: String): List<String> {
        val nodes = doc.getElementsByTagName(tag)
        return (0 until nodes.length).map {
          (nodes.item(it) as org.w3c.dom.Element).getAttributeNS(ManifestEditor.ANDROID_NS, attr)
        }
      }
      assertEquals(listOf("com.example.fixture.Main"), tagAttr("activity", "name"))
      assertEquals(listOf("com.example.clone.files"), tagAttr("provider", "authorities"))
      assertEquals(listOf("com.example.clone.PRIVATE"), tagAttr("permission", "name"))
      assertEquals(listOf("Clone"), tagAttr("application", "label"))
      assertTrue("android.permission.CAMERA" in tagAttr("uses-permission", "name"))

      val info = ApkInfo.load(decoded)
      assertEquals("34", info.sdkInfo.targetSdkVersion)
      assertEquals("24", info.sdkInfo.minSdkVersion)
      assertEquals(7, info.versionInfo.versionCode)
      assertEquals("2.0", info.versionInfo.versionName)

      // Scratch space is cleaned up.
      assertTrue(File(dir, "work").listFiles().orEmpty().isEmpty())
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun invalidPatchesFailBeforeAnyWork() {
    val dir = tempDir()
    try {
      val patcher =
        ApktoolPatcher(ApktoolEnvironment(File(dir, "work"), File(dir, "framework"), jobs = 1))
      val result =
        patcher.patch(
          File(dir, "missing.apk"),
          File(dir, "out.apk"),
          listOf(ApkPatch.SetPackageName("nope")),
          SigningKey(generateKey(dir).privateKey, emptyList()),
        )
      val failure = assertIs<PatchResult.Failure>(result)
      assertEquals("validate", failure.stage)
      assertTrue(!File(dir, "work").exists())
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun unreadableInputReportsTheFailingStage() {
    val dir = tempDir()
    try {
      val patcher =
        ApktoolPatcher(ApktoolEnvironment(File(dir, "work"), File(dir, "framework"), jobs = 1))
      val result =
        patcher.patch(
          File(dir, "missing.apk"),
          File(dir, "out.apk"),
          listOf(ApkPatch.SetDebuggable(true)),
          generateKey(dir),
        )
      val failure = assertIs<PatchResult.Failure>(result)
      assertEquals("decode", failure.stage)
    } finally {
      dir.deleteRecursively()
    }
  }
}
