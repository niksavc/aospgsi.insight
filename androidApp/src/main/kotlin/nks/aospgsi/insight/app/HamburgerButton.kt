package nks.aospgsi.insight.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The three-line menu button. It is drawn instead of using an icon font or glyph, so that it looks
 * the same on every device and needs no icon library.
 */
@Composable
internal fun HamburgerButton(onClick: () -> Unit) {
  val color = MaterialTheme.colorScheme.onSurface
  val description = stringResource(R.string.menu_open)
  IconButton(
    onClick = onClick,
    modifier = Modifier.semantics { contentDescription = description },
  ) {
    Canvas(Modifier.size(20.dp)) {
      val stroke = 2.dp.toPx()
      listOf(0.1f, 0.5f, 0.9f).forEach { fraction ->
        val y = size.height * fraction
        drawLine(
          color = color,
          start = Offset(0f, y),
          end = Offset(size.width, y),
          strokeWidth = stroke,
          cap = StrokeCap.Round,
        )
      }
    }
  }
}
