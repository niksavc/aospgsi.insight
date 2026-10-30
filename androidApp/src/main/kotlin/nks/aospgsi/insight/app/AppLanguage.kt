package nks.aospgsi.insight.app

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import androidx.activity.ComponentActivity
import java.util.Locale

/** The languages the app has strings for. An empty tag means "follow the system". */
internal enum class AppLanguageChoice(val tag: String) {
  SYSTEM(""),
  ENGLISH("en"),
  RUSSIAN("ru"),
}

/**
 * Per-app language. Android 13 and later keep it themselves (and show it in the system's per-app
 * language settings); on older versions the choice is stored in [AppSettings] and applied to each
 * activity's context in [AppActivity.attachBaseContext].
 */
internal object AppLanguage {
  /** The choice in effect now. */
  fun current(context: Context): AppLanguageChoice {
    val tag =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        if (locales.isEmpty) "" else locales[0].language
      } else {
        AppSettings.get(context).language
      }
    return AppLanguageChoice.entries.firstOrNull { it.tag == tag } ?: AppLanguageChoice.SYSTEM
  }

  /** [base] with the stored language applied; only needed before Android 13. */
  fun wrap(base: Context): Context {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
    val tag = AppSettings.get(base).language
    if (tag.isEmpty()) return base
    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale)
    val config = android.content.res.Configuration(base.resources.configuration)
    config.setLocale(locale)
    return base.createConfigurationContext(config)
  }

  /** Switches to [choice]. Older versions restart the app so that every screen picks it up. */
  fun apply(activity: Activity, choice: AppLanguageChoice) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      // The system recreates the app's activities itself.
      activity.getSystemService(LocaleManager::class.java).applicationLocales =
        LocaleList.forLanguageTags(choice.tag)
    } else {
      AppSettings.get(activity).updateLanguage(choice.tag)
      activity.startActivity(
        Intent(activity, MainActivity::class.java)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
      )
      activity.finishAffinity()
    }
  }
}

/** Base class of the app's screens: applies the in-app language before Android 13. */
open class AppActivity : ComponentActivity() {
  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(AppLanguage.wrap(newBase))
  }
}
