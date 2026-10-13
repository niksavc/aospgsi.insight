package nks.aospgsi.insight.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** A yes/no question before an action that changes things. */
@Composable
fun ConfirmDialog(
  title: String,
  message: String,
  confirmText: String,
  dismissText: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(message) },
    confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
  )
}
