package com.bloxinsight.core.patchapi

import com.bloxinsight.core.model.ApkInput
import com.bloxinsight.core.model.ApkInspection
import com.bloxinsight.core.model.PatchPlan
import com.bloxinsight.core.model.PatchProfile
import com.bloxinsight.core.model.PatchProgress
import com.bloxinsight.core.model.PatchResult

interface ApkPatchingEngine {
  suspend fun inspect(input: ApkInput): ApkInspection

  suspend fun plan(input: ApkInput, profile: PatchProfile): PatchPlan

  suspend fun execute(plan: PatchPlan, progress: suspend (PatchProgress) -> Unit): PatchResult
}
