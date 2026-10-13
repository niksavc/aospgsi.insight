package nks.aospgsi.insight.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** A secondary screen: top bar with a back button, content below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenFrame(
  title: String,
  onBack: () -> Unit,
  content: @Composable (PaddingValues) -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(title) },
        navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
      )
    },
    content = content,
  )
}

/** Scrolling column with page padding, for screens made of cards. */
@Composable
fun ScrollingPage(padding: PaddingValues, content: @Composable () -> Unit) {
  Column(
    modifier =
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    content()
  }
}

@Composable
fun InfoCard(title: String, content: @Composable () -> Unit) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(title, style = MaterialTheme.typography.titleLarge)
      content()
    }
  }
}
