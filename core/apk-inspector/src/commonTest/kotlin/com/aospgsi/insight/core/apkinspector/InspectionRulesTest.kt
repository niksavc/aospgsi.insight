package com.aospgsi.insight.core.apkinspector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InspectionRulesTest {
  @Test
  fun missingPackageNameProducesWarning() {
    assertNotNull(InspectionRules.packageNameWarning(null))
    assertNotNull(InspectionRules.packageNameWarning("  "))
  }

  @Test
  fun legacyRobloxPackageIdsAreRecognized() {
    for (id in listOf("com.roblox.client", "com.roblox.roblox_client")) {
      val warning = assertNotNull(InspectionRules.packageNameWarning(id))
      assertTrue(warning.contains("Roblox"))
    }
  }

  @Test
  fun otherPackageNamesHaveNoWarning() {
    assertNull(InspectionRules.packageNameWarning("org.example.app"))
  }

  @Test
  fun unsupportedAbisAreReported() {
    assertEquals(setOf("mips"), InspectionRules.unsupportedAbis(setOf("arm64-v8a", "mips")))
    assertTrue(InspectionRules.unsupportedAbis(InspectionRules.knownAbis).isEmpty())
  }

  @Test
  fun abisAreExtractedSortedAndDistinct() {
    val libs =
      listOf(
        "lib/x86_64/libb.so",
        "lib/arm64-v8a/liba.so",
        "lib/arm64-v8a/libb.so",
        "assets/lib/ignored.so",
        "lib/readme.txt",
      )
    assertEquals(listOf("arm64-v8a", "x86_64"), InspectionRules.abisFromNativeLibraries(libs))
  }
}
