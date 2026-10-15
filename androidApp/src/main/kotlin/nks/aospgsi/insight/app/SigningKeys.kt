package nks.aospgsi.insight.app

import android.content.Context
import java.io.File
import nks.aospgsi.insight.core.apktool.AndroidKeystoreSecretBox
import nks.aospgsi.insight.core.apktool.AndroidKeystoreSigningKey
import nks.aospgsi.insight.core.apktool.SigningKeyManager

/**
 * The one place that decides where the signing key lives, shared by Settings and the patch service.
 */
internal fun signingKeyManager(context: Context): SigningKeyManager =
  SigningKeyManager(
    dir = File(context.filesDir, "signing"),
    box = AndroidKeystoreSecretBox(),
    deviceKey = { AndroidKeystoreSigningKey.getOrCreate() },
  )
