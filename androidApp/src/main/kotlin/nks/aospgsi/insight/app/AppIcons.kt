package nks.aospgsi.insight.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** In-memory cache of launcher icons, drawn once at [SIZE_PX] and reused by every list row. */
private object IconCache {
  private const val SIZE_PX = 128
  private val cache = LruCache<String, ImageBitmap>(400)

  fun get(packageName: String): ImageBitmap? = cache.get(packageName)

  fun load(context: Context, packageName: String): ImageBitmap? =
    try {
      val drawable = context.packageManager.getApplicationIcon(packageName)
      val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
      drawable.setBounds(0, 0, SIZE_PX, SIZE_PX)
      drawable.draw(Canvas(bitmap))
      bitmap.asImageBitmap().also { cache.put(packageName, it) }
    } catch (_: Exception) {
      null
    }
}

/** The app's launcher icon, loaded off the main thread the first time it is shown. */
@Composable
fun AppIcon(packageName: String, size: Dp = 40.dp) {
  val context = LocalContext.current
  val icon by
    produceState(IconCache.get(packageName), packageName) {
      if (value == null) {
        value = withContext(Dispatchers.IO) { IconCache.load(context, packageName) }
      }
    }
  Box(Modifier.size(size)) {
    icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
  }
}
