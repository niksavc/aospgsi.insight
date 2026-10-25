package nks.aospgsi.insight.core.apkinspector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Builds a small binary manifest: string-typed attributes only, which is all the reader uses. */
private class Axml {
  private val pool = ArrayList<String>()
  private val body = ArrayList<Byte>()

  private fun s(text: String): Int =
    pool.indexOf(text).takeIf { it >= 0 }
      ?: run {
        pool += text
        pool.lastIndex
      }

  private fun put16(list: MutableList<Byte>, v: Int) {
    list += (v and 0xff).toByte()
    list += ((v shr 8) and 0xff).toByte()
  }

  private fun put32(list: MutableList<Byte>, v: Int) {
    put16(list, v and 0xffff)
    put16(list, (v ushr 16) and 0xffff)
  }

  fun start(name: String, vararg attrs: Pair<String, String>) {
    val chunk = ArrayList<Byte>()
    put16(chunk, 0x0102)
    put16(chunk, 16)
    put32(chunk, 36 + attrs.size * 20)
    put32(chunk, 1) // line
    put32(chunk, -1) // comment
    put32(chunk, -1) // namespace
    put32(chunk, s(name))
    put16(chunk, 20)
    put16(chunk, 20)
    put16(chunk, attrs.size)
    put16(chunk, 0)
    put16(chunk, 0)
    put16(chunk, 0)
    for ((key, value) in attrs) {
      put32(chunk, -1) // namespace
      put32(chunk, s(key))
      put32(chunk, s(value)) // raw value
      put16(chunk, 8)
      chunk += 0
      chunk += 0x03 // string
      put32(chunk, s(value))
    }
    body += chunk
  }

  fun end(name: String) {
    val chunk = ArrayList<Byte>()
    put16(chunk, 0x0103)
    put16(chunk, 16)
    put32(chunk, 24)
    put32(chunk, 1)
    put32(chunk, -1)
    put32(chunk, -1)
    put32(chunk, s(name))
    body += chunk
  }

  fun bytes(): ByteArray {
    val strings = ArrayList<Byte>()
    val offsets = ArrayList<Int>()
    for (text in pool) {
      offsets += strings.size
      put16(strings, text.length)
      for (c in text) put16(strings, c.code)
      put16(strings, 0)
    }
    while (strings.size % 4 != 0) strings += 0
    val stringsStart = 28 + pool.size * 4
    val poolChunk = ArrayList<Byte>()
    put16(poolChunk, 0x0001)
    put16(poolChunk, 28)
    put32(poolChunk, stringsStart + strings.size)
    put32(poolChunk, pool.size)
    put32(poolChunk, 0)
    put32(poolChunk, 0) // UTF-16
    put32(poolChunk, stringsStart)
    put32(poolChunk, 0)
    for (o in offsets) put32(poolChunk, o)
    poolChunk += strings
    val file = ArrayList<Byte>()
    put16(file, 0x0003)
    put16(file, 8)
    put32(file, 8 + poolChunk.size + body.size)
    file += poolChunk
    file += body
    return file.toByteArray()
  }
}

class SecretCodesTest {
  private fun manifest(build: Axml.() -> Unit): ByteArray =
    Axml()
      .apply {
        start("manifest", "package" to "com.example.app")
        start("application")
        build()
        end("application")
        end("manifest")
      }
      .bytes()

  private fun Axml.receiver(
    name: String,
    action: String = SecretCodes.ACTION,
    scheme: String = SecretCodes.SCHEME,
    vararg hosts: String,
  ) {
    start("receiver", "name" to name)
    start("intent-filter")
    start("action", "name" to action)
    end("action")
    start("data", "scheme" to scheme)
    end("data")
    for (host in hosts) {
      start("data", "host" to host)
      end("data")
    }
    end("intent-filter")
    end("receiver")
  }

  @Test
  fun readsCodeFromReceiverFilter() {
    val codes =
      SecretCodes.fromManifest(manifest { receiver(".DebugReceiver", hosts = arrayOf("4636")) })
    assertEquals(listOf(SecretCode("4636", "com.example.app.DebugReceiver")), codes)
    assertEquals("*#*#4636#*#*", codes.single().dialString)
  }

  @Test
  fun everyHostOfOneFilterIsACode() {
    val codes =
      SecretCodes.fromManifest(manifest { receiver("Hidden", hosts = arrayOf("111", "222")) })
    assertEquals(listOf("111", "222"), codes.map { it.code })
    assertEquals("com.example.app.Hidden", codes.first().component)
  }

  @Test
  fun fullyQualifiedNamesAreKept() {
    val codes =
      SecretCodes.fromManifest(manifest { receiver("org.other.Rx", hosts = arrayOf("7")) })
    assertEquals("org.other.Rx", codes.single().component)
  }

  @Test
  fun otherActionsAndSchemesAreIgnored() {
    val codes =
      SecretCodes.fromManifest(
        manifest {
          receiver("A", action = "android.intent.action.VIEW", hosts = arrayOf("1"))
          receiver("B", scheme = "https", hosts = arrayOf("2"))
          receiver("C") // right action and scheme, but no host: nothing to dial
        },
      )
    assertTrue(codes.isEmpty())
  }

  @Test
  fun sameCodeFromTwoComponentsIsListedForEach() {
    val codes =
      SecretCodes.fromManifest(
        manifest {
          receiver("A", hosts = arrayOf("9"))
          receiver("B", hosts = arrayOf("9"))
        },
      )
    assertEquals(listOf("com.example.app.A", "com.example.app.B"), codes.map { it.component })
  }

  @Test
  fun garbageIsNotACrash() {
    assertTrue(SecretCodes.fromManifest(ByteArray(0)).isEmpty())
    assertTrue(SecretCodes.fromManifest(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)).isEmpty())
    assertTrue(SecretCodes.fromManifest("<manifest/>".encodeToByteArray()).isEmpty())
  }
}
