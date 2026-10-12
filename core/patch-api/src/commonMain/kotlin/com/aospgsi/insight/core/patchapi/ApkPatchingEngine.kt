package com.aospgsi.insight.core.patchapi

import com.aospgsi.insight.core.model.ApkInput
import com.aospgsi.insight.core.model.ApkInspection
import com.aospgsi.insight.core.model.PatchPlan
import com.aospgsi.insight.core.model.PatchProfile
import com.aospgsi.insight.core.model.PatchProgress
import com.aospgsi.insight.core.model.PatchResult

interface ApkPatchingEngine {
  suspend fun inspect(input: ApkInput): ApkInspection

  suspend fun plan(input: ApkInput, profile: PatchProfile): PatchPlan

  suspend fun execute(plan: PatchPlan, progress: suspend (PatchProgress) -> Unit): PatchResult
}
