package nks.aospgsi.insight.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An app's icon that opens the actions sheet (launch, settings, share, stores) when tapped. */
@Composable
fun AppIconWithActions(packageName: String, size: Dp = 40.dp) {
  var open by rememberSaveable { mutableStateOf(false) }
  Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { open = true }) {
    AppIcon(packageName, size)
  }
  if (open) AppActionsSheet(packageName, onDismiss = { open = false })
}

/** An app that can open the app's page by the `market://` scheme: a store or an app manager. */
private class StoreApp(val label: String, val packageName: String, val className: String)

private const val STORE_COLUMNS = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppActionsSheet(packageName: String, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val label = remember(packageName) { appLabel(context, packageName) }
  val hasSplits = remember(packageName) { hasSplitApks(context, packageName) }
  val stores by
    produceState(emptyList<StoreApp>(), packageName) {
      value = withContext(Dispatchers.IO) { findStoreApps(context, packageName) }
    }
  val noLauncher = stringResource(R.string.action_no_launcher)
  val openFailed = stringResource(R.string.action_open_failed)
  val shareFailed = stringResource(R.string.action_share_failed)

  fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      Text(
        stringResource(R.string.actions_title),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ActionButton(
          label = stringResource(R.string.action_launch),
          glyph = Glyph.LAUNCH,
          container = MaterialTheme.colorScheme.primary,
          content = MaterialTheme.colorScheme.onPrimary,
        ) {
          val intent = context.packageManager.getLaunchIntentForPackage(packageName)
          if (intent == null) {
            toast(noLauncher)
          } else {
            try {
              context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
              onDismiss()
            } catch (_: Exception) {
              toast(openFailed)
            }
          }
        }
        ActionButton(
          label = stringResource(R.string.action_settings),
          glyph = Glyph.SETTINGS,
          container = MaterialTheme.colorScheme.secondary,
          content = MaterialTheme.colorScheme.onSecondary,
        ) {
          val page = Uri.parse("package:$packageName")
          try {
            context.startActivity(
              Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, page)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            onDismiss()
          } catch (_: Exception) {
            toast(openFailed)
          }
        }
        ActionButton(
          label = stringResource(R.string.action_share),
          glyph = Glyph.SHARE,
          container = MaterialTheme.colorScheme.tertiary,
          content = MaterialTheme.colorScheme.onTertiary,
        ) {
          scope.launch {
            val chooser =
              withContext(Dispatchers.IO) {
                try {
                  shareIntent(context, packageName, label)
                } catch (_: Exception) {
                  null
                }
              }
            if (chooser == null) {
              toast(shareFailed)
            } else {
              try {
                context.startActivity(chooser)
                onDismiss()
              } catch (_: Exception) {
                toast(shareFailed)
              }
            }
          }
        }
      }
      if (hasSplits) {
        Text(stringResource(R.string.action_share_note), style = MaterialTheme.typography.bodySmall)
      }
      if (stores.isNotEmpty()) {
        Text(stringResource(R.string.actions_stores), style = MaterialTheme.typography.titleSmall)
        stores.chunked(STORE_COLUMNS).forEach { rowItems ->
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowItems.forEach { store ->
              StoreCell(store, Modifier.weight(1f)) {
                try {
                  context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                      .setClassName(store.packageName, store.className)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                  )
                  onDismiss()
                } catch (_: Exception) {
                  toast(openFailed)
                }
              }
            }
            repeat(STORE_COLUMNS - rowItems.size) { Spacer(Modifier.weight(1f)) }
          }
        }
      }
    }
  }
}

private enum class Glyph {
  LAUNCH,
  SETTINGS,
  SHARE,
}

@Composable
private fun ActionButton(
  label: String,
  glyph: Glyph,
  container: Color,
  content: Color,
  onClick: () -> Unit,
) {
  Column(
    modifier =
      Modifier.width(80.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) {
        onClick()
      },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(
      modifier = Modifier.size(56.dp).clip(CircleShape).background(container),
      contentAlignment = Alignment.Center,
    ) {
      Canvas(Modifier.size(26.dp)) { drawGlyph(glyph, content) }
    }
    Text(
      label,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun StoreCell(store: StoreApp, modifier: Modifier, onClick: () -> Unit) {
  Column(
    modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onClick() },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    AppIcon(store.packageName, size = 48.dp)
    Text(
      store.label,
      style = MaterialTheme.typography.labelMedium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
    )
  }
}

/** The three action glyphs, drawn so that no icon font or library is needed. */
private fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
  val w = size.width
  val h = size.height
  val stroke = 2.dp.toPx()
  when (glyph) {
    Glyph.LAUNCH -> {
      val path =
        Path().apply {
          moveTo(w * 0.25f, h * 0.12f)
          lineTo(w * 0.88f, h * 0.5f)
          lineTo(w * 0.25f, h * 0.88f)
          close()
        }
      drawPath(path, color)
    }
    Glyph.SETTINGS -> {
      val center = Offset(w / 2, h / 2)
      drawCircle(color, radius = w * 0.2f, center = center, style = Stroke(stroke))
      for (i in 0 until 8) {
        val angle = Math.PI * 2 * i / 8
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        drawLine(
          color,
          start = Offset(center.x + dx * w * 0.32f, center.y + dy * w * 0.32f),
          end = Offset(center.x + dx * w * 0.47f, center.y + dy * w * 0.47f),
          strokeWidth = stroke * 1.5f,
          cap = StrokeCap.Round,
        )
      }
      drawCircle(color, radius = w * 0.32f, center = center, style = Stroke(stroke))
    }
    Glyph.SHARE -> {
      val top = Offset(w * 0.78f, h * 0.2f)
      val mid = Offset(w * 0.22f, h * 0.5f)
      val bottom = Offset(w * 0.78f, h * 0.8f)
      drawLine(color, mid, top, strokeWidth = stroke)
      drawLine(color, mid, bottom, strokeWidth = stroke)
      drawCircle(color, radius = w * 0.13f, center = top)
      drawCircle(color, radius = w * 0.13f, center = mid)
      drawCircle(color, radius = w * 0.13f, center = bottom)
    }
  }
}

private fun appLabel(context: Context, packageName: String): String =
  try {
    context.packageManager
      .getApplicationInfo(packageName, 0)
      .loadLabel(context.packageManager)
      .toString()
  } catch (_: Exception) {
    packageName
  }

private fun hasSplitApks(context: Context, packageName: String): Boolean =
  try {
    !context.packageManager.getApplicationInfo(packageName, 0).splitSourceDirs.isNullOrEmpty()
  } catch (_: Exception) {
    false
  }

/** Apps that handle `market://details?id=…`, minus this app itself. */
@Suppress("DEPRECATION")
private fun findStoreApps(context: Context, packageName: String): List<StoreApp> {
  val pm = context.packageManager
  val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
  return pm
    .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    .filter { it.activityInfo.packageName != context.packageName && it.activityInfo.exported }
    .distinctBy { it.activityInfo.packageName }
    .map {
      StoreApp(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name)
    }
}

/**
 * Copies the app's base APK into the cache and returns a chooser that shares it as a file. Split
 * APKs are not included.
 */
@Suppress("DEPRECATION")
private fun shareIntent(context: Context, packageName: String, label: String): Intent {
  val pm = context.packageManager
  val info = pm.getPackageInfo(packageName, 0)
  val source = File(info.applicationInfo!!.sourceDir)
  val directory = File(context.cacheDir, "share").apply { mkdirs() }
  directory.listFiles()?.forEach { it.delete() }
  val name =
    "${label}_${info.versionName ?: info.versionCode}".replace(Regex("[^A-Za-z0-9._-]"), "_")
  val target = File(directory, "$name.apk")
  source.copyTo(target, overwrite = true)
  val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
  val send =
    Intent(Intent.ACTION_SEND)
      .setType("application/vnd.android.package-archive")
      .putExtra(Intent.EXTRA_STREAM, uri)
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      .apply { clipData = ClipData.newRawUri(null, uri) }
  return Intent.createChooser(send, label).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
