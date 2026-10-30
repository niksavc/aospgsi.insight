package nks.aospgsi.insight.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class Action {
  CLEAR_CACHE,
  FORCE_STOP,
}

/** "Clear cache" and "Force stop" for the app being inspected. */
@Composable
fun AppControlCard(packageName: String) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf<String?>(null) }

  val done = stringResource(R.string.app_control_done)
  val needsPermission = stringResource(R.string.app_control_needs_permission)
  val openedSettings = stringResource(R.string.app_control_opened_settings)
  val failed = stringResource(R.string.app_control_failed)

  fun start(action: Action) {
    busy = true
    message = null
    scope.launch {
      val result =
        when (action) {
          Action.CLEAR_CACHE -> AppControl.clearCache(context, packageName)
          Action.FORCE_STOP -> AppControl.forceStop(context, packageName)
        }
      message =
        when (result) {
          ControlResult.Done -> done
          ControlResult.NeedsPermission -> needsPermission
          ControlResult.OpenedSettings -> openedSettings
          is ControlResult.Failed -> failed.format(result.message)
        }
      busy = false
    }
  }

  InfoCard(stringResource(R.string.app_control_title)) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = { start(Action.CLEAR_CACHE) },
          enabled = !busy,
          modifier = Modifier.weight(1f),
        ) {
          Text(stringResource(R.string.app_control_clear_cache))
        }
        Button(
          onClick = { start(Action.FORCE_STOP) },
          enabled = !busy,
          modifier = Modifier.weight(1f),
        ) {
          Text(stringResource(R.string.app_control_force_stop))
        }
      }
      message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
      Text(
        stringResource(R.string.app_control_note),
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}
