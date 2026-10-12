package nks.aospgsi.insight.core.patchapi

import nks.aospgsi.insight.core.model.ApkInput
import nks.aospgsi.insight.core.model.ApkInspection
import nks.aospgsi.insight.core.model.PatchPlan
import nks.aospgsi.insight.core.model.PatchProfile
import nks.aospgsi.insight.core.model.PatchProgress
import nks.aospgsi.insight.core.model.PatchResult

interface ApkPatchingEngine {
  suspend fun inspect(input: ApkInput): ApkInspection

  suspend fun plan(input: ApkInput, profile: PatchProfile): PatchPlan

  suspend fun execute(plan: PatchPlan, progress: suspend (PatchProgress) -> Unit): PatchResult
}
