package nks.aospgsi.insight.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation

/** Minimum length for a password that protects an exported key file. */
internal const val MIN_EXPORT_PASSWORD = 6

/**
 * Asks for a password. With [confirm] it asks twice and only accepts a matching pair of at least
 * [MIN_EXPORT_PASSWORD] characters (a new password); without it, any non-empty entry (an existing
 * one).
 */
@Composable
internal fun PasswordDialog(
  title: String,
  message: String,
  confirm: Boolean,
  confirmButton: String,
  onSubmit: (CharArray) -> Unit,
  onDismiss: () -> Unit,
) {
  var first by remember { mutableStateOf("") }
  var second by remember { mutableStateOf("") }
  val valid =
    if (confirm) first.length >= MIN_EXPORT_PASSWORD && first == second else first.isNotEmpty()
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column {
        Text(message)
        OutlinedTextField(
          value = first,
          onValueChange = { first = it },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          label = { Text(stringResource(R.string.key_password)) },
        )
        if (confirm) {
          OutlinedTextField(
            value = second,
            onValueChange = { second = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text(stringResource(R.string.key_password_again)) },
            isError = second.isNotEmpty() && first != second,
          )
          Text(stringResource(R.string.key_password_rule, MIN_EXPORT_PASSWORD))
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onSubmit(first.toCharArray()) }, enabled = valid) {
        Text(confirmButton)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}
