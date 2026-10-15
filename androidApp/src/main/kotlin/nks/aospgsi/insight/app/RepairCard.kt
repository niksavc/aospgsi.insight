package nks.aospgsi.insight.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nks.aospgsi.insight.app.ui.ConfirmDialog
import nks.aospgsi.insight.core.model.ApkInspection

/**
 * "Repair APK": re-aligns and re-signs the picked APK without changing its content. For files that
 * an archiver app or another tool saved again, after which Android reports them as corrupt.
 */
@Composable
internal fun RepairCard(
  apk: ApkInspection,
  /** State of this card's own run; [PatchState.Idle] when the last run was not a repair. */
  patchState: PatchState,
  /** True while any patch job runs, so only one starts at a time. */
  anyRunning: Boolean,
  onRepair: () -> Unit,
) {
  if (apk.packageName == null) return
  val running = patchState is PatchState.Running
  var confirming by rememberSaveable { mutableStateOf(false) }
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(stringResource(R.string.card_repair), style = MaterialTheme.typography.titleLarge)
      if (apk.isBundle) {
        Text(stringResource(R.string.repair_bundle_unavailable))
        return@Column
      }
      Text(stringResource(R.string.repair_intro))
      Text(stringResource(R.string.repair_limits), style = MaterialTheme.typography.bodySmall)

      if (confirming) {
        ConfirmDialog(
          title = stringResource(R.string.repair_confirm_title),
          message = stringResource(R.string.repair_confirm_message),
          confirmText = stringResource(R.string.repair_button),
          dismissText = stringResource(R.string.cancel),
          onConfirm = {
            confirming = false
            onRepair()
          },
          onDismiss = { confirming = false },
        )
      }
      OutlinedButton(onClick = { confirming = true }, enabled = !anyRunning) {
        Text(stringResource(if (running) R.string.repair_running else R.string.repair_button))
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
              R.string.repair_failed,
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
