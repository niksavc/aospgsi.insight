package nks.aospgsi.insight.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

class AboutActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { AppTheme { AboutScreen(onBack = { finish() }) } }
  }
}

@Composable
private fun AboutScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val version = remember {
    try {
      context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
      "?"
    }
  }
  ScreenFrame(stringResource(R.string.about_title), onBack) { padding ->
    ScrollingPage(padding) {
      InfoCard("AOSP GSI Insight") {
        Text(stringResource(R.string.about_tagline))
        Text(
          stringResource(R.string.about_version, version),
          style = MaterialTheme.typography.bodySmall,
        )
        Text(context.packageName, style = MaterialTheme.typography.bodySmall)
      }
      InfoCard(stringResource(R.string.about_legal_title)) {
        Text(stringResource(R.string.about_legal_body))
      }
      InfoCard(stringResource(R.string.about_licenses_title)) {
        OPEN_SOURCE.forEach { (name, license) ->
          Text("• $name — $license", style = MaterialTheme.typography.bodySmall)
        }
        Text(
          stringResource(R.string.about_licenses_note),
          style = MaterialTheme.typography.bodySmall,
        )
      }
      SupportCard()
    }
  }
}

/** Components shipped inside the app and their licenses. */
private val OPEN_SOURCE =
  listOf(
    "Apktool" to "Apache-2.0",
    "apksig (Android Open Source Project)" to "Apache-2.0",
    "aapt2 (AOSP platform-tools)" to "Apache-2.0",
    "Jetpack Compose / AndroidX" to "Apache-2.0",
    "Kotlin and kotlinx libraries" to "Apache-2.0",
  )
