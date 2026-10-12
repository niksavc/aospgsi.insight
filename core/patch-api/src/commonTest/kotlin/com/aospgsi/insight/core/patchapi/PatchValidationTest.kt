package com.aospgsi.insight.core.patchapi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PatchValidationTest {
  @Test
  fun validPatchesHaveNoProblems() {
    val patches =
      listOf(
        ApkPatch.SetPackageName("com.example.clone"),
        ApkPatch.SetTargetSdk(34),
        ApkPatch.SetMinSdk(24),
        ApkPatch.SetDebuggable(false),
        ApkPatch.SetAppLabel("Clone"),
        ApkPatch.AddPermission("android.permission.INTERNET"),
        ApkPatch.RemovePermission("android.permission.READ_CONTACTS"),
        ApkPatch.SetVersion(5, "1.5"),
      )
    assertTrue(PatchValidation.validate(patches).isEmpty())
  }

  @Test
  fun rejectsMalformedPackageNames() {
    for (bad in listOf("single", "1abc.def", "a..b", "a.b-c", "")) {
      assertEquals(
        1,
        PatchValidation.validate(listOf(ApkPatch.SetPackageName(bad))).size,
        "expected a problem for '$bad'",
      )
    }
  }

  @Test
  fun rejectsDuplicateSingletonPatches() {
    val problems =
      PatchValidation.validate(listOf(ApkPatch.SetTargetSdk(30), ApkPatch.SetTargetSdk(31)))
    assertEquals(listOf("Only one target SDK patch is allowed."), problems)
  }

  @Test
  fun rejectsSdkOutOfRangeAndTargetBelowMin() {
    assertEquals(1, PatchValidation.validate(listOf(ApkPatch.SetTargetSdk(0))).size)
    assertEquals(1, PatchValidation.validate(listOf(ApkPatch.SetMinSdk(99))).size)
    val problems =
      PatchValidation.validate(listOf(ApkPatch.SetMinSdk(30), ApkPatch.SetTargetSdk(24)))
    assertEquals(1, problems.size)
  }

  @Test
  fun rejectsContradictoryPermissionsAndEmptyVersion() {
    val problems =
      PatchValidation.validate(
        listOf(
          ApkPatch.AddPermission("a.b.C"),
          ApkPatch.RemovePermission("a.b.C"),
          ApkPatch.SetVersion(null, null),
        ),
      )
    assertEquals(2, problems.size)
  }
}
