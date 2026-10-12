package com.bloxinsight.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bloxinsight.core.model.ApkInspection
import com.bloxinsight.core.patchapi.FixPlan
import com.bloxinsight.core.patchapi.FixUnavailableReason
import com.bloxinsight.core.verification.InstallAssessment
import com.bloxinsight.core.verification.InstallIssueCode
import com.bloxinsight.core.verification.InstalledApp

/**
 * "Fix for this Android version": raises an old APK's target SDK to what the device accepts and/or
 * re-roots it under a new package ID, then re-signs it with the on-device key. Shown only when
 * there is something to offer.
 */
@Composable
internal fun FixCard(
  apk: ApkInspection,
  assessment: InstallAssessment,
  installed: InstalledApp?,
  plan: FixPlan,
  cloneAlongside: Boolean,
  onCloneChange: (Boolean) -> Unit,
  patchState: PatchState,
  aapt2Ready: Boolean,
  onFix: (FixPlan.Ready) -> Unit,
) {
  val fixable =
    assessment.issues.any {
      it.code == InstallIssueCode.TARGET_SDK_TOO_LOW || it.code == InstallIssueCode.SIGNER_MISMATCH
    }
  val visible =
    when (plan) {
      is FixPlan.Ready -> true
      is FixPlan.Unavailable ->
        when (plan.reason) {
          FixUnavailableReason.NOTHING_TO_FIX -> installed != null
          FixUnavailableReason.BUNDLE -> fixable
          FixUnavailableReason.NO_PACKAGE_NAME -> false
        }
    }
  if (!visible) return

  val running = patchState is PatchState.Running
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(stringResource(R.string.card_fix), style = MaterialTheme.typography.titleLarge)

      if (plan is FixPlan.Unavailable && plan.reason == FixUnavailableReason.BUNDLE) {
        Text(stringResource(R.string.fix_bundle_unavailable))
        return@Column
      }

      Text(stringResource(R.string.fix_intro))
      if (plan is FixPlan.Ready) {
        plan.targetSdk?.let {
          Text("• " + stringResource(R.string.fix_change_target_sdk, apk.targetSdk ?: 0, it))
        }
        plan.newPackage?.let { Text("• " + stringResource(R.string.fix_change_package, it)) }
      } else {
        Text(stringResource(R.string.fix_nothing_but_clone))
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(stringResource(R.string.fix_clone_toggle), modifier = Modifier.weight(1f))
        Switch(checked = cloneAlongside, onCheckedChange = onCloneChange, enabled = !running)
      }
      if (installed != null && !cloneAlongside) {
        Text(
          stringResource(R.string.fix_replace_warning),
          style = MaterialTheme.typography.bodySmall,
        )
      }
      if (plan is FixPlan.Ready && plan.targetSdk != null) {
        Text(
          stringResource(R.string.fix_target_sdk_warning),
          style = MaterialTheme.typography.bodySmall,
        )
      }
      Text(stringResource(R.string.fix_key_note), style = MaterialTheme.typography.bodySmall)

      if (!aapt2Ready) Text(stringResource(R.string.fix_no_aapt2))

      Button(
        onClick = { if (plan is FixPlan.Ready) onFix(plan) },
        enabled = plan is FixPlan.Ready && aapt2Ready && !running,
      ) {
        Text(stringResource(if (running) R.string.fix_running else R.string.fix_button))
      }

      val context = LocalContext.current
      when (patchState) {
        is PatchState.Running -> {
          LinearProgressIndicator(
            progress = { patchState.percent / 100f },
            modifier = Modifier.fillMaxWidth(),
          )
          Text(
            "${patchState.percent}% · ${stageLabel(context, patchState.stage)}",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        is PatchState.Failed ->
          Text(
            stringResource(
              R.string.fix_failed,
              stageLabel(context, patchState.stage),
              patchState.message ?: stringResource(R.string.error_unknown),
            ),
            color = MaterialTheme.colorScheme.error,
          )
        else -> Unit
      }
    }
  }
}
