package nks.aospgsi.insight.app

import android.os.Bundle
import androidx.activity.compose.setContent

class MainActivity : AppActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { AospGsiAndroidApp() }
  }
}
