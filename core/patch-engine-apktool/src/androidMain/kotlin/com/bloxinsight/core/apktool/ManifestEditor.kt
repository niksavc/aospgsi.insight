package com.bloxinsight.core.apktool

import com.bloxinsight.core.patchapi.ApkPatch
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Applies manifest-level patches to a decoded (plain XML) AndroidManifest.xml. SDK levels and
 * version info are not here: Apktool keeps those in apktool.yml, not in the decoded manifest.
 */
internal object ManifestEditor {
  const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

  private val COMPONENT_TAGS =
    listOf("activity", "activity-alias", "service", "receiver", "provider")
  private val PERMISSION_ATTRS = listOf("permission", "readPermission", "writePermission")

  fun apply(manifestFile: File, patches: List<ApkPatch>) {
    val doc = parse(manifestFile)
    for (patch in patches) {
      when (patch) {
        is ApkPatch.SetDebuggable ->
          application(doc)?.setAndroid(doc, "debuggable", patch.debuggable.toString())
        is ApkPatch.SetAppLabel -> application(doc)?.setAndroid(doc, "label", patch.label)
        is ApkPatch.AddPermission -> addPermission(doc, patch.name)
        is ApkPatch.RemovePermission -> removePermission(doc, patch.name)
        is ApkPatch.SetPackageName -> renamePackage(doc, patch.newPackage)
        is ApkPatch.SetTargetSdk,
        is ApkPatch.SetMinSdk,
        is ApkPatch.SetVersion -> Unit
      }
    }
    write(doc, manifestFile)
  }

  fun parse(file: File): Document {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    return factory.newDocumentBuilder().parse(file)
  }

  private fun write(doc: Document, file: File) {
    doc.xmlStandalone = true
    val transformer = TransformerFactory.newInstance().newTransformer()
    transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8")
    transformer.transform(DOMSource(doc), StreamResult(file))
  }

  private fun application(doc: Document): Element? = elements(doc, "application").firstOrNull()

  private fun elements(doc: Document, tag: String): List<Element> {
    val list = doc.getElementsByTagName(tag)
    return (0 until list.length).map { list.item(it) as Element }
  }

  private fun Element.androidAttr(name: String): String? =
    if (hasAttributeNS(ANDROID_NS, name)) getAttributeNS(ANDROID_NS, name) else null

  private fun Element.setAndroid(doc: Document, name: String, value: String) {
    val prefix = doc.documentElement.lookupPrefix(ANDROID_NS) ?: "android"
    setAttributeNS(ANDROID_NS, "$prefix:$name", value)
  }

  private fun addPermission(doc: Document, name: String) {
    val present = elements(doc, "uses-permission").any { it.androidAttr("name") == name }
    if (present) return
    val element = doc.createElement("uses-permission")
    element.setAndroid(doc, "name", name)
    val root = doc.documentElement
    val application = application(doc)
    if (application != null && application.parentNode == root) {
      root.insertBefore(element, application)
    } else {
      root.appendChild(element)
    }
  }

  private fun removePermission(doc: Document, name: String) {
    for (tag in listOf("uses-permission", "uses-permission-sdk-23")) {
      elements(doc, tag)
        .filter { it.androidAttr("name") == name }
        .forEach { it.parentNode.removeChild(it) }
    }
  }

  /** Expands a manifest class name that is relative to the package into a full class name. */
  fun qualify(oldPackage: String, name: String): String =
    when {
      name.startsWith(".") -> oldPackage + name
      !name.contains('.') -> "$oldPackage.$name"
      else -> name
    }

  private fun renamePackage(doc: Document, newPackage: String) {
    val root = doc.documentElement
    val old = root.getAttribute("package")
    require(old.isNotEmpty()) { "The manifest has no package attribute." }
    if (old == newPackage) return

    fun qualifyAttr(element: Element, attr: String) {
      element
        .androidAttr(attr)
        ?.takeIf { it.isNotEmpty() }
        ?.let {
          element.setAndroid(doc, attr, qualify(old, it))
        }
    }
    fun rename(value: String): String =
      if (value == old || value.startsWith("$old.")) newPackage + value.removePrefix(old) else value

    // 1. Class names relative to the old package must be made absolute first, otherwise they
    // would silently resolve against the new package.
    application(doc)?.let { app ->
      listOf("name", "backupAgent", "manageSpaceActivity").forEach { qualifyAttr(app, it) }
    }
    for (tag in COMPONENT_TAGS) {
      for (element in elements(doc, tag)) {
        listOf("name", "targetActivity", "parentActivityName").forEach { qualifyAttr(element, it) }
      }
    }

    // 2. Names that live in the app's own namespace must not collide with the original app.
    for (provider in elements(doc, "provider")) {
      provider.androidAttr("authorities")?.let { authorities ->
        provider.setAndroid(
          doc,
          "authorities",
          authorities.split(';').joinToString(";") { rename(it) },
        )
      }
    }
    val definedPermissions =
      elements(doc, "permission")
        .mapNotNull { it.androidAttr("name") }
        .filter { it.startsWith("$old.") }
        .associateWith { rename(it) }
    if (definedPermissions.isNotEmpty()) {
      val tags = listOf("permission", "uses-permission", "uses-permission-sdk-23")
      for (tag in tags) {
        for (element in elements(doc, tag)) {
          element.androidAttr("name")?.let { name ->
            definedPermissions[name]?.let { element.setAndroid(doc, "name", it) }
          }
        }
      }
      for (element in elements(doc, "application") + COMPONENT_TAGS.flatMap { elements(doc, it) }) {
        for (attr in PERMISSION_ATTRS) {
          element.androidAttr(attr)?.let { value ->
            definedPermissions[value]?.let { element.setAndroid(doc, attr, it) }
          }
        }
      }
    }

    root.setAttribute("package", newPackage)
  }
}
