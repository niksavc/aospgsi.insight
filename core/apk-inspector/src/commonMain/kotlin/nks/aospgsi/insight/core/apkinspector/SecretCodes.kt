package nks.aospgsi.insight.core.apkinspector

/** A dialer secret code an app listens for, and the component that handles it. */
data class SecretCode(
  /** The code between `*#*#` and `#*#*`, which an app declares as the host of its filter. */
  val code: String,
  /** Full class name of the receiver (or other component) that declares it. */
  val component: String,
) {
  /** What the user types into the dialer. */
  val dialString: String
    get() = SecretCodes.dialString(code)
}

/**
 * Secret codes are an Android dialer feature: typing `*#*#1234#*#*` makes the dialer broadcast
 * `android.provider.Telephony.SECRET_CODE` with the data `android_secret_code://1234`, and an app
 * whose manifest has an intent filter for that action, scheme and host receives it. This reads such
 * filters from a binary AndroidManifest.xml, which is the only place they are declared.
 */
object SecretCodes {
  const val ACTION = "android.provider.Telephony.SECRET_CODE"
  const val SCHEME = "android_secret_code"

  fun dialString(code: String): String = "*#*#$code#*#*"

  /** Secret codes declared in [manifest], in manifest order; empty if it cannot be read. */
  fun fromManifest(manifest: ByteArray): List<SecretCode> =
    if (!mentionsScheme(manifest)) {
      emptyList()
    } else {
      try {
        Reader(manifest).read()
      } catch (_: RuntimeException) {
        emptyList()
      }
    }

  /**
   * A cheap pre-check for scanning every installed app: the scheme is a string in the manifest's
   * string pool, stored as UTF-8 or as UTF-16, so a manifest without those bytes has no code.
   */
  private fun mentionsScheme(manifest: ByteArray): Boolean =
    contains(manifest, SCHEME.encodeToByteArray()) ||
      contains(
        manifest,
        ByteArray(SCHEME.length * 2) { if (it % 2 == 0) SCHEME[it / 2].code.toByte() else 0 },
      )

  private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
    if (needle.isEmpty() || haystack.size < needle.size) return false
    val first = needle[0]
    var i = 0
    val last = haystack.size - needle.size
    outer@ while (i <= last) {
      if (haystack[i] == first) {
        for (j in 1 until needle.size) if (haystack[i + j] != needle[j]) {
          i++
          continue@outer
        }
        return true
      }
      i++
    }
    return false
  }

  private val COMPONENTS = setOf("receiver", "activity", "activity-alias", "service", "provider")

  /** Walks the binary XML events once, tracking the component and intent filter being read. */
  private class Reader(private val data: ByteArray) {
    private var strings: List<String> = emptyList()
    private val found = LinkedHashSet<SecretCode>()

    private var packageName = ""
    private var component: String? = null
    private var inFilter = false
    private var actions = mutableListOf<String>()
    private var schemes = mutableListOf<String>()
    private var hosts = mutableListOf<String>()

    fun read(): List<SecretCode> {
      require(u16(0) == 0x0003) { "Not binary XML." }
      var offset = u16(2) // header size
      val total = minOf(u32(4), data.size)
      while (offset + 8 <= total) {
        val type = u16(offset)
        val size = u32(offset + 4)
        require(size >= 8 && offset + size <= data.size) { "Bad chunk." }
        when (type) {
          0x0001 -> strings = readStrings(offset)
          0x0102 -> startElement(offset)
          0x0103 -> endElement(offset)
        }
        offset += size
      }
      return found.toList()
    }

    private fun startElement(at: Int) {
      val name = string(i32(at + 20))
      val attrStart = u16(at + 24)
      val attrSize = u16(at + 26)
      val attrCount = u16(at + 28)
      val attrs = HashMap<String, String>()
      for (i in 0 until attrCount) {
        val base = at + 16 + attrStart + i * attrSize
        val attrName = string(i32(base + 4))
        val raw = i32(base + 8)
        val value =
          when {
            raw >= 0 -> string(raw)
            u8(base + 15) == TYPE_STRING -> string(i32(base + 16))
            else -> continue
          }
        attrs[attrName] = value
      }
      when {
        name == "manifest" -> packageName = attrs["package"].orEmpty()
        name in COMPONENTS -> component = qualified(attrs["name"].orEmpty())
        name == "intent-filter" && component != null -> {
          inFilter = true
          actions = mutableListOf()
          schemes = mutableListOf()
          hosts = mutableListOf()
        }
        inFilter && name == "action" -> attrs["name"]?.let { actions += it }
        inFilter && name == "data" -> {
          attrs["scheme"]?.let { schemes += it }
          attrs["host"]?.let { hosts += it }
        }
      }
    }

    private fun endElement(at: Int) {
      when (string(i32(at + 20))) {
        "intent-filter" ->
          if (inFilter) {
            inFilter = false
            // All <data> elements of one filter combine, so any scheme goes with any host.
            if (ACTION in actions && SCHEME in schemes) {
              for (host in hosts) if (host.isNotEmpty()) found += SecretCode(host, component!!)
            }
          }
        in COMPONENTS -> component = null
      }
    }

    private fun qualified(name: String): String =
      when {
        name.startsWith(".") -> packageName + name
        '.' !in name -> "$packageName.$name"
        else -> name
      }

    private fun readStrings(at: Int): List<String> {
      val count = u32(at + 8)
      val flags = u32(at + 16)
      val stringsStart = u32(at + 20)
      val utf8 = flags and 0x100 != 0
      return List(count) { index ->
        var p = at + stringsStart + u32(at + 28 + index * 4)
        if (utf8) {
          p += if (u8(p) and 0x80 != 0) 2 else 1 // UTF-16 length
          var length = u8(p++)
          if (length and 0x80 != 0) length = ((length and 0x7f) shl 8) or u8(p++)
          data.decodeToString(p, p + length)
        } else {
          var length = u16(p)
          p += 2
          if (length and 0x8000 != 0) {
            length = ((length and 0x7fff) shl 16) or u16(p)
            p += 2
          }
          CharArray(length) { Char(u16(p + it * 2)) }.concatToString()
        }
      }
    }

    private fun string(index: Int): String = if (index in strings.indices) strings[index] else ""

    private fun u8(at: Int): Int = data[at].toInt() and 0xff

    private fun u16(at: Int): Int = u8(at) or (u8(at + 1) shl 8)

    private fun u32(at: Int): Int = u16(at) or (u16(at + 2) shl 16)

    private fun i32(at: Int): Int = u32(at)

    private companion object {
      const val TYPE_STRING = 0x03
    }
  }
}
