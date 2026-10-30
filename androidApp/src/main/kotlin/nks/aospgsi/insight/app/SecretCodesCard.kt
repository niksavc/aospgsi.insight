package nks.aospgsi.insight.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import nks.aospgsi.insight.core.apkinspector.SecretCode

/**
 * Dialer secret codes the app declares (the idea is from the open-source Secret Codes app by Simon
 * Marquis, Apache-2.0; this is written from scratch). A code is run by handing `*#*#code#*#*` to
 * the system dialer, which is the only thing allowed to send the protected broadcast.
 */
@Composable
internal fun SecretCodesCard(codes: List<SecretCode>) {
  InfoCard(stringResource(R.string.card_secret_codes, codes.size)) {
    Text(stringResource(R.string.secret_codes_note), style = MaterialTheme.typography.bodySmall)
    codes.forEach { code -> SecretCodeRow(code) }
  }
}

/** One code with its handler and a button that opens the dialer; long-press copies the code. */
@Composable
internal fun SecretCodeRow(code: SecretCode) {
  val context = LocalContext.current
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier =
      Modifier.fillMaxWidth()
        .combinedClickable(onClick = {}, onLongClick = { copyCode(context, code.dialString) })
        .padding(vertical = 4.dp),
  ) {
    Column(Modifier.weight(1f)) {
      Text(
        code.dialString,
        style = MaterialTheme.typography.bodyLarge,
        fontFamily = FontFamily.Monospace,
      )
      Text(code.component, style = MaterialTheme.typography.bodySmall)
    }
    OutlinedButton(onClick = { dial(context, code) }) {
      Text(stringResource(R.string.secret_code_dial))
    }
  }
}

private fun dial(context: Context, code: SecretCode) {
  try {
    context.startActivity(
      Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", code.dialString, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
  } catch (_: ActivityNotFoundException) {
    Toast.makeText(context, R.string.secret_code_no_dialer, Toast.LENGTH_LONG).show()
  }
}

private fun copyCode(context: Context, text: String) {
  val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
  clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
  // Android 13 and later show their own confirmation.
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
  }
}
