package nks.aospgsi.insight.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nks.aospgsi.insight.app.ui.ConfirmDialog
import nks.aospgsi.insight.core.apktool.SigningKeyException
import nks.aospgsi.insight.core.apktool.SigningKeyManager

private enum class KeyDialog {
  CREATE,
  EXPORT_PASSWORD,
  IMPORT_PASSWORD,
  USE_DEVICE_KEY,
}

/**
 * Settings card for the key that signs patched and repaired APKs: shows which key is active and
 * lets the user make an exportable key, export it to a password-protected .p12 file, import one, or
 * go back to the device key.
 */
@Composable
internal fun SigningKeyCard() {
  val context = LocalContext.current
  val manager = remember { signingKeyManager(context) }
  val scope = rememberCoroutineScope()
  var info by remember { mutableStateOf<SigningKeyManager.Info?>(null) }
  var message by remember { mutableStateOf<String?>(null) }
  var failed by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var dialog by remember { mutableStateOf<KeyDialog?>(null) }
  var importUri by remember { mutableStateOf<Uri?>(null) }
  var exportPassword by remember { mutableStateOf<CharArray?>(null) }

  fun run(work: () -> String) {
    busy = true
    message = null
    scope.launch {
      val outcome =
        withContext(Dispatchers.IO) {
          try {
            Result.success(work())
          } catch (e: Exception) {
            Result.failure(e)
          }
        }
      failed = outcome.isFailure
      message = outcome.getOrElse { errorText(context, it) }
      info = withContext(Dispatchers.IO) { runCatching { manager.info() }.getOrNull() }
      busy = false
    }
  }

  LaunchedEffect(Unit) {
    info = withContext(Dispatchers.IO) { runCatching { manager.info() }.getOrNull() }
  }

  val exportLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument("application/x-pkcs12"),
    ) { uri ->
      val password = exportPassword
      exportPassword = null
      if (uri != null && password != null) {
        run {
          val out =
            context.contentResolver.openOutputStream(uri) ?: throw IOException("Cannot write file.")
          out.use { manager.export(it, password) }
          context.getString(R.string.key_exported)
        }
      }
    }
  val importLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        importUri = uri
        dialog = KeyDialog.IMPORT_PASSWORD
      }
    }

  InfoCard(stringResource(R.string.card_signing_key)) {
    val current = info
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (current == null) {
        Text(stringResource(R.string.apps_loading))
      } else {
        Text(
          stringResource(
            if (current.source == SigningKeyManager.Source.DEVICE) R.string.key_source_device
            else R.string.key_source_stored,
          ),
          style = MaterialTheme.typography.titleMedium,
        )
        Text(current.subject, style = MaterialTheme.typography.bodyMedium)
        Text("SHA-256: ${current.sha256}", style = MaterialTheme.typography.bodySmall)
        Text(
          stringResource(
            R.string.key_valid_until,
            DateFormat.getDateInstance().format(Date(current.notAfterMillis)),
          ),
          style = MaterialTheme.typography.bodySmall,
        )
        if (current.storedKeyUnreadable) {
          Text(
            stringResource(R.string.key_unreadable),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
      Text(stringResource(R.string.key_explainer), style = MaterialTheme.typography.bodySmall)

      Button(
        onClick = { dialog = KeyDialog.CREATE },
        enabled = !busy && current != null,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(stringResource(R.string.key_create))
      }
      OutlinedButton(
        onClick = { dialog = KeyDialog.EXPORT_PASSWORD },
        enabled = !busy && current?.exportable == true,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(stringResource(R.string.key_export))
      }
      OutlinedButton(
        onClick = { importLauncher.launch(arrayOf("*/*")) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(stringResource(R.string.key_import))
      }
      OutlinedButton(
        onClick = { dialog = KeyDialog.USE_DEVICE_KEY },
        enabled = !busy && current?.source == SigningKeyManager.Source.STORED,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(stringResource(R.string.key_use_device))
      }
      message?.let {
        Text(
          it,
          color =
            if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
      }
    }
  }

  when (dialog) {
    KeyDialog.CREATE ->
      ConfirmDialog(
        title = stringResource(R.string.key_create_title),
        message = stringResource(R.string.key_create_message),
        confirmText = stringResource(R.string.key_create),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
          dialog = null
          run {
            manager.createExportable()
            context.getString(R.string.key_created)
          }
        },
        onDismiss = { dialog = null },
      )
    KeyDialog.USE_DEVICE_KEY ->
      ConfirmDialog(
        title = stringResource(R.string.key_use_device_title),
        message = stringResource(R.string.key_use_device_message),
        confirmText = stringResource(R.string.key_use_device),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
          dialog = null
          run {
            manager.useDeviceKey()
            context.getString(R.string.key_back_to_device)
          }
        },
        onDismiss = { dialog = null },
      )
    KeyDialog.EXPORT_PASSWORD ->
      PasswordDialog(
        title = stringResource(R.string.key_export_title),
        message = stringResource(R.string.key_export_message),
        confirm = true,
        confirmButton = stringResource(R.string.key_export),
        onSubmit = { password ->
          dialog = null
          exportPassword = password
          exportLauncher.launch("aospgsi-signing-key.p12")
        },
        onDismiss = { dialog = null },
      )
    KeyDialog.IMPORT_PASSWORD ->
      PasswordDialog(
        title = stringResource(R.string.key_import_title),
        message = stringResource(R.string.key_import_message),
        confirm = false,
        confirmButton = stringResource(R.string.key_import),
        onSubmit = { password ->
          val uri = importUri
          dialog = null
          importUri = null
          if (uri != null) {
            run {
              val input =
                context.contentResolver.openInputStream(uri)
                  ?: throw IOException("Cannot read file.")
              input.use { manager.import(it, password) }
              context.getString(R.string.key_imported)
            }
          }
        },
        onDismiss = {
          dialog = null
          importUri = null
        },
      )
    null -> Unit
  }
}

private fun errorText(context: android.content.Context, error: Throwable): String =
  when ((error as? SigningKeyException)?.reason) {
    SigningKeyException.Reason.WRONG_PASSWORD -> context.getString(R.string.key_err_wrong_password)
    SigningKeyException.Reason.UNREADABLE -> context.getString(R.string.key_err_unreadable)
    SigningKeyException.Reason.NO_KEY -> context.getString(R.string.key_err_no_key)
    SigningKeyException.Reason.KEY_CERTIFICATE_MISMATCH ->
      context.getString(R.string.key_err_mismatch)
    SigningKeyException.Reason.NOT_EXPORTABLE -> context.getString(R.string.key_err_not_exportable)
    null -> context.getString(R.string.key_err_other, error.message ?: error.javaClass.simpleName)
  }
