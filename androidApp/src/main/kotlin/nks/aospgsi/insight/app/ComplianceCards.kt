package nks.aospgsi.insight.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nks.aospgsi.insight.core.monetization.ProjectLinks
import nks.aospgsi.insight.core.verification.ProprietaryGoogle
import nks.aospgsi.insight.core.verification.ProprietaryKind

/**
 * Shown for Google's closed-source packages (Play Store, Play Services, WebView...). The app
 * inspects and installs files the user supplies, but never hosts or sells these.
 */
@Composable
fun ProprietaryCard(packageName: String?) {
  val kind = ProprietaryGoogle.classify(packageName) ?: return
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(stringResource(R.string.card_proprietary), style = MaterialTheme.typography.titleLarge)
      if (kind == ProprietaryKind.OTHER_GOOGLE_APP) {
        Text(stringResource(R.string.proprietary_namespace_note))
      } else {
        Text(stringResource(R.string.proprietary_body))
      }
      if (kind == ProprietaryKind.PLAY_STORE || kind == ProprietaryKind.PLAY_SERVICES) {
        Text(stringResource(R.string.proprietary_conflict_hint))
      }
      Text(stringResource(R.string.proprietary_alternatives))
    }
  }
}

/** Where people can support the manager itself. Hidden until real links are configured. */
@Composable
fun SupportCard() {
  val links = ProjectLinks.valid()
  if (links.isEmpty()) return
  val uriHandler = LocalUriHandler.current
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(stringResource(R.string.card_support), style = MaterialTheme.typography.titleLarge)
      Text(stringResource(R.string.support_body))
      links.forEach { link ->
        OutlinedButton(onClick = { uriHandler.openUri(link.url) }) { Text(link.label) }
      }
    }
  }
}
