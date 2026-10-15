package nks.aospgsi.insight.core.apktool

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts a small secret at rest. The key behind it must not leave the device. */
interface SecretBox {
  fun seal(plain: ByteArray): ByteArray

  fun open(sealed: ByteArray): ByteArray
}

/**
 * AES-GCM with a key kept in the Android Keystore. What it seals is the password of the stored
 * signing key, so the key file alone (for example in a device backup) is useless without this
 * device's Keystore.
 */
class AndroidKeystoreSecretBox(private val alias: String = "aospgsi-secretbox") : SecretBox {
  override fun seal(plain: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key())
    return cipher.iv + cipher.doFinal(plain)
  }

  override fun open(sealed: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(
      Cipher.DECRYPT_MODE,
      key(),
      GCMParameterSpec(TAG_BITS, sealed.copyOfRange(0, IV_BYTES)),
    )
    return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
  }

  private fun key(): SecretKey {
    val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
    (store.getKey(alias, null) as? SecretKey)?.let {
      return it
    }
    val spec =
      KeyGenParameterSpec.Builder(
          alias,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setKeySize(256)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .build()
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
      init(spec)
      generateKey()
    }
  }

  private companion object {
    const val PROVIDER = "AndroidKeyStore"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val IV_BYTES = 12
    const val TAG_BITS = 128
  }
}
