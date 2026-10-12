package com.bloxinsight.core.patchapi

import com.bloxinsight.core.model.ApkInspection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FixPlannerTest {
  private fun apk(
    packageName: String? = "com.roblox.client",
    minSdk: Int? = 16,
    targetSdk: Int? = 19,
    bundle: List<String> = emptyList(),
  ) =
    ApkInspection(
      packageName = packageName,
      versionName = "1",
      versionCode = 1,
      minSdk = minSdk,
      targetSdk = targetSdk,
      signerSha256 = listOf("aa"),
      nativeAbis = emptyList(),
      bundleApks = bundle,
    )

  @Test
  fun oldTargetSdkIsRaisedToTheDeviceMinimum() {
    val plan = assertIs<FixPlan.Ready>(FixPlanner.plan(apk(), minimumTargetSdk = 24, false))
    assertEquals(listOf<ApkPatch>(ApkPatch.SetTargetSdk(24)), plan.patches)
    assertEquals(24, plan.targetSdk)
    assertEquals(null, plan.newPackage)
    assertTrue(PatchValidation.validate(plan.patches).isEmpty())
  }

  @Test
  fun targetSdkNeverEndsBelowMinSdk() {
    val plan = FixPlanner.plan(apk(minSdk = 26, targetSdk = 19), minimumTargetSdk = 24, false)
    assertEquals(26, assertIs<FixPlan.Ready>(plan).targetSdk)
    assertTrue(PatchValidation.validate(plan.patches).isEmpty())
  }

  @Test
  fun cloneRenamesThePackageAndKeepsTheTargetSdkWhenItIsFine() {
    val plan =
      assertIs<FixPlan.Ready>(FixPlanner.plan(apk(targetSdk = 34), minimumTargetSdk = 24, true))
    assertEquals(
      listOf<ApkPatch>(ApkPatch.SetPackageName("com.roblox.client.bloxinsight")),
      plan.patches,
    )
    assertEquals(null, plan.targetSdk)
    assertEquals("com.roblox.client.bloxinsight", plan.newPackage)
    assertTrue(PatchValidation.validate(plan.patches).isEmpty())
  }

  @Test
  fun cloneAndTargetSdkCombine() {
    val plan = assertIs<FixPlan.Ready>(FixPlanner.plan(apk(), minimumTargetSdk = 23, true))
    assertEquals(2, plan.patches.size)
    assertTrue(PatchValidation.validate(plan.patches).isEmpty())
  }

  @Test
  fun cloneNameIsNotSuffixedTwice() {
    assertEquals("a.b.bloxinsight", FixPlanner.cloneName("a.b"))
    assertEquals("a.b.bloxinsight", FixPlanner.cloneName("a.b.bloxinsight"))
  }

  @Test
  fun nothingToFixWhenTheApkAlreadyInstallsAndNoCloneIsWanted() {
    assertEquals(
      FixPlan.Unavailable(FixUnavailableReason.NOTHING_TO_FIX),
      FixPlanner.plan(apk(targetSdk = 34), minimumTargetSdk = 24, false),
    )
    assertEquals(
      FixPlan.Unavailable(FixUnavailableReason.NOTHING_TO_FIX),
      FixPlanner.plan(apk(), minimumTargetSdk = null, false),
    )
  }

  @Test
  fun bundlesAndNamelessApksAreNotPatched() {
    assertEquals(
      FixPlan.Unavailable(FixUnavailableReason.BUNDLE),
      FixPlanner.plan(apk(bundle = listOf("base.apk")), 24, false),
    )
    assertEquals(
      FixPlan.Unavailable(FixUnavailableReason.NO_PACKAGE_NAME),
      FixPlanner.plan(apk(packageName = null), 24, false),
    )
  }
}
