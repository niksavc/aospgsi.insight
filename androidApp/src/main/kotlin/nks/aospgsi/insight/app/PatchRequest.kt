package nks.aospgsi.insight.app

import android.content.Context
import android.content.Intent
import nks.aospgsi.insight.core.patchapi.ApkPatch

/** What the patch service should do. The source APK is already staged in the app's cache. */
data class PatchRequest(
  val sourcePath: String,
  val packageName: String,
  val targetSdk: Int?,
  val newPackage: String?,
) {
  val patches: List<ApkPatch>
    get() =
      listOfNotNull(
        targetSdk?.let { ApkPatch.SetTargetSdk(it) },
        newPackage?.let { ApkPatch.SetPackageName(it) },
      )

  fun toIntent(context: Context): Intent =
    Intent(context, PatchService::class.java)
      .putExtra(EXTRA_SOURCE, sourcePath)
      .putExtra(EXTRA_PACKAGE, packageName)
      .putExtra(EXTRA_TARGET_SDK, targetSdk ?: NO_TARGET_SDK)
      .putExtra(EXTRA_NEW_PACKAGE, newPackage)

  companion object {
    private const val EXTRA_SOURCE = "source"
    private const val EXTRA_PACKAGE = "package"
    private const val EXTRA_TARGET_SDK = "targetSdk"
    private const val EXTRA_NEW_PACKAGE = "newPackage"
    private const val NO_TARGET_SDK = -1

    fun fromIntent(intent: Intent): PatchRequest? {
      val source = intent.getStringExtra(EXTRA_SOURCE) ?: return null
      val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return null
      val target =
        intent.getIntExtra(EXTRA_TARGET_SDK, NO_TARGET_SDK).takeIf { it != NO_TARGET_SDK }
      return PatchRequest(source, packageName, target, intent.getStringExtra(EXTRA_NEW_PACKAGE))
    }
  }
}
