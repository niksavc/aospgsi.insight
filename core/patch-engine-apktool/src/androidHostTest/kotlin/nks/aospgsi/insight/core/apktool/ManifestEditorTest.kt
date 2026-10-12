package nks.aospgsi.insight.core.apktool

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import nks.aospgsi.insight.core.patchapi.ApkPatch

class ManifestEditorTest {
  private val manifest =
    """
    <?xml version="1.0" encoding="utf-8"?>
    <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.app" android:sharedUserId="com.example.uid">
      <uses-permission android:name="android.permission.INTERNET"/>
      <uses-permission android:name="android.permission.READ_CONTACTS"/>
      <uses-permission android:name="com.example.app.PRIVATE"/>
      <permission android:name="com.example.app.PRIVATE" android:protectionLevel="signature"/>
      <permission android:name="org.shared.permission.PUSH" android:protectionLevel="signature"/>
      <application android:name=".App" android:label="@string/app_name" android:permission="com.example.app.PRIVATE">
        <activity android:name=".Main" android:parentActivityName="Home" android:exported="true"/>
        <activity-alias android:name="Alias" android:targetActivity=".Main"/>
        <service android:name="org.other.Svc" android:process=":remote"/>
        <provider android:name="Files" android:authorities="com.example.app.files;other.authority"/>
      </application>
    </manifest>
    """
      .trimIndent()

  private fun edit(vararg patches: ApkPatch): org.w3c.dom.Document {
    val file = File.createTempFile("manifest", ".xml")
    try {
      file.writeText(manifest)
      ManifestEditor.apply(file, patches.toList())
      return ManifestEditor.parse(file)
    } finally {
      file.delete()
    }
  }

  private fun org.w3c.dom.Document.all(tag: String): List<org.w3c.dom.Element> {
    val nodes = getElementsByTagName(tag)
    return (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
  }

  private fun org.w3c.dom.Element.attr(name: String): String? =
    if (hasAttributeNS(ManifestEditor.ANDROID_NS, name)) {
      getAttributeNS(ManifestEditor.ANDROID_NS, name)
    } else {
      null
    }

  @Test
  fun qualifyExpandsRelativeNames() {
    assertEquals("com.example.app.Main", ManifestEditor.qualify("com.example.app", ".Main"))
    assertEquals("com.example.app.Main", ManifestEditor.qualify("com.example.app", "Main"))
    assertEquals("org.other.Svc", ManifestEditor.qualify("com.example.app", "org.other.Svc"))
  }

  @Test
  fun packageRenameKeepsClassesResolvable() {
    val doc = edit(ApkPatch.SetPackageName("com.example.clone"))
    assertEquals("com.example.clone", doc.documentElement.getAttribute("package"))
    assertEquals("com.example.app.App", doc.all("application").single().attr("name"))
    val activities = doc.all("activity")
    assertEquals("com.example.app.Main", activities.single().attr("name"))
    assertEquals("com.example.app.Home", activities.single().attr("parentActivityName"))
    val alias = doc.all("activity-alias").single()
    assertEquals("com.example.app.Alias", alias.attr("name"))
    assertEquals("com.example.app.Main", alias.attr("targetActivity"))
    assertEquals("org.other.Svc", doc.all("service").single().attr("name"))
    assertEquals("com.example.app.Files", doc.all("provider").single().attr("name"))
  }

  @Test
  fun packageRenameMovesAuthoritiesAndOwnPermissions() {
    val doc = edit(ApkPatch.SetPackageName("com.example.clone"))
    assertEquals(
      "com.example.clone.files;other.authority.com.example.clone",
      doc.all("provider").single().attr("authorities"),
    )
    assertEquals(
      listOf("com.example.clone.PRIVATE", "org.shared.permission.PUSH.com.example.clone"),
      doc.all("permission").map { it.attr("name") },
    )
    val used = doc.all("uses-permission").mapNotNull { it.attr("name") }
    assertEquals(
      listOf(
        "android.permission.INTERNET",
        "android.permission.READ_CONTACTS",
        "com.example.clone.PRIVATE",
      ),
      used,
    )
    assertEquals("com.example.clone.PRIVATE", doc.all("application").single().attr("permission"))
  }

  @Test
  fun packageRenameDropsSharedUserId() {
    val doc = edit(ApkPatch.SetPackageName("com.example.clone"))
    assertNull(
      doc.documentElement.getAttributeNS(ManifestEditor.ANDROID_NS, "sharedUserId").ifEmpty {
        null
      },
    )
  }

  @Test
  fun permissionsCanBeAddedAndRemoved() {
    val doc =
      edit(
        ApkPatch.AddPermission("android.permission.CAMERA"),
        ApkPatch.AddPermission("android.permission.INTERNET"),
        ApkPatch.RemovePermission("android.permission.READ_CONTACTS"),
      )
    val used = doc.all("uses-permission").mapNotNull { it.attr("name") }
    assertEquals(1, used.count { it == "android.permission.INTERNET" })
    assertEquals(1, used.count { it == "android.permission.CAMERA" })
    assertEquals(0, used.count { it == "android.permission.READ_CONTACTS" })
  }

  @Test
  fun labelAndDebuggableAreSet() {
    val doc = edit(ApkPatch.SetAppLabel("My clone"), ApkPatch.SetDebuggable(true))
    val app = doc.all("application").single()
    assertEquals("My clone", app.attr("label"))
    assertEquals("true", app.attr("debuggable"))
  }

  @Test
  fun untouchedManifestKeepsItsPackage() {
    val doc = edit(ApkPatch.SetTargetSdk(34))
    assertEquals("com.example.app", doc.documentElement.getAttribute("package"))
    assertNull(doc.all("application").single().attr("debuggable"))
  }

  @Test
  fun renameNeedsAPackageAttribute() {
    val file = File.createTempFile("manifest", ".xml")
    try {
      file.writeText(
        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"/>",
      )
      assertFailsWith<IllegalArgumentException> {
        ManifestEditor.apply(file, listOf(ApkPatch.SetPackageName("a.b")))
      }
    } finally {
      file.delete()
    }
  }
}
