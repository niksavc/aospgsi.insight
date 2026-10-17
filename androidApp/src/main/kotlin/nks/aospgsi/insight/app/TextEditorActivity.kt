package nks.aospgsi.insight.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nks.aospgsi.insight.app.ui.ConfirmDialog
import nks.aospgsi.insight.core.texteditor.LineEnding
import nks.aospgsi.insight.core.texteditor.TextCodec
import nks.aospgsi.insight.core.texteditor.TextLoad
import nks.aospgsi.insight.core.texteditor.TextSearch

/**
 * A plain UTF-8 text editor for files the user opens through the system picker, or that another app
 * hands over with "Open with". It keeps a file's line ending style and byte order mark.
 */
class TextEditorActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val uri = intent?.data
    setContent { AppTheme { TextEditorScreen(initialUri = uri, onClose = { finish() }) } }
  }
}

/** Undo and redo over whole-text snapshots; edits typed in quick succession share one step. */
private class EditHistory(initial: TextFieldValue) {
  private val states = ArrayList<TextFieldValue>().apply { add(initial) }
  private var index = 0
  private var lastAt = 0L

  val canUndo: Boolean
    get() = index > 0

  val canRedo: Boolean
    get() = index < states.lastIndex

  fun record(value: TextFieldValue, now: Long) {
    if (value.text == states[index].text) {
      states[index] = value // only the cursor moved
      return
    }
    while (states.lastIndex > index) states.removeAt(states.lastIndex)
    if (index > 0 && now - lastAt < COALESCE_MS) {
      states[index] = value
    } else {
      states.add(value)
      index++
    }
    lastAt = now
    trim()
  }

  fun undo(): TextFieldValue? = if (canUndo) states[--index].also { lastAt = 0L } else null

  fun redo(): TextFieldValue? = if (canRedo) states[++index].also { lastAt = 0L } else null

  /** Old snapshots go first, so that a big file cannot fill memory with copies of itself. */
  private fun trim() {
    var chars = states.sumOf { it.text.length.toLong() }
    while (states.size > 2 && (states.size > MAX_STEPS || chars > MAX_CHARS)) {
      chars -= states.removeAt(0).text.length
      index--
    }
  }

  private companion object {
    const val COALESCE_MS = 800L
    const val MAX_STEPS = 100
    const val MAX_CHARS = 24_000_000L
  }
}

private class EditorFile(
  val uri: Uri?,
  val name: String,
  val lineEnding: LineEnding,
  val hasBom: Boolean,
  val mixedLineEndings: Boolean,
)

/** What the user asked for that would throw away unsaved changes. */
private enum class Pending {
  CLOSE,
  OPEN,
  NEW,
}

private fun displayName(context: Context, uri: Uri): String =
  try {
    context.contentResolver
      .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { if (it.moveToFirst()) it.getString(0) else null }
  } catch (_: RuntimeException) {
    null
  } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file.txt"

/** Reads at most one byte over the limit, so that a huge file is refused without being loaded. */
private fun readCapped(context: Context, uri: Uri): ByteArray {
  val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open.")
  input.use {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    val limit = TextCodec.MAX_BYTES + 1
    while (out.size() < limit) {
      val n = it.read(buffer, 0, minOf(buffer.size, limit - out.size()))
      if (n < 0) break
      out.write(buffer, 0, n)
    }
    return out.toByteArray()
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextEditorScreen(initialUri: Uri?, onClose: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var file by remember { mutableStateOf<EditorFile?>(null) }
  var value by remember { mutableStateOf(TextFieldValue("")) }
  var history by remember { mutableStateOf(EditHistory(TextFieldValue(""))) }
  var dirty by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf<String?>(null) }
  var failed by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var wrap by remember { mutableStateOf(false) }
  var menuOpen by remember { mutableStateOf(false) }
  var pending by remember { mutableStateOf<Pending?>(null) }

  var findOpen by remember { mutableStateOf(false) }
  var query by remember { mutableStateOf("") }
  var replacement by remember { mutableStateOf("") }
  var ignoreCase by remember { mutableStateOf(true) }

  fun report(text: String, error: Boolean = false) {
    message = text
    failed = error
  }

  fun install(opened: EditorFile, text: String) {
    file = opened
    value = TextFieldValue(text)
    history = EditHistory(value)
    dirty = false
    message = null
  }

  fun load(uri: Uri) {
    busy = true
    scope.launch {
      val outcome =
        withContext(Dispatchers.IO) {
          try {
            val name = displayName(context, uri)
            Result.success(name to TextCodec.decode(readCapped(context, uri)))
          } catch (e: Exception) {
            Result.failure(e)
          }
        }
      busy = false
      outcome
        .onFailure {
          report(
            context.getString(R.string.editor_err_read, it.message ?: it.javaClass.simpleName),
            error = true,
          )
        }
        .onSuccess { (name, result) ->
          when (result) {
            is TextLoad.Ok ->
              install(
                EditorFile(
                  uri,
                  name,
                  result.document.lineEnding,
                  result.document.hasBom,
                  result.document.mixedLineEndings,
                ),
                result.document.text,
              )
            TextLoad.Binary -> report(context.getString(R.string.editor_err_binary), error = true)
            TextLoad.NotUtf8 ->
              report(context.getString(R.string.editor_err_not_utf8), error = true)
            is TextLoad.TooLarge ->
              report(
                context.getString(R.string.editor_err_too_large, result.limitBytes / (1024 * 1024)),
                error = true,
              )
          }
        }
    }
  }

  fun write(target: Uri, name: String) {
    val current = file
    val bytes =
      TextCodec.encode(
        value.text,
        current?.lineEnding ?: LineEnding.LF,
        current?.hasBom ?: false,
      )
    busy = true
    scope.launch {
      val outcome =
        withContext(Dispatchers.IO) {
          try {
            val out =
              context.contentResolver.openOutputStream(target, "wt")
                ?: throw IOException("Cannot open for writing.")
            out.use { it.write(bytes) }
            Result.success(Unit)
          } catch (e: Exception) {
            Result.failure(e)
          }
        }
      busy = false
      outcome
        .onFailure {
          report(
            context.getString(R.string.editor_err_write, it.message ?: it.javaClass.simpleName),
            error = true,
          )
        }
        .onSuccess {
          file =
            EditorFile(
              target,
              name,
              current?.lineEnding ?: LineEnding.LF,
              current?.hasBom ?: false,
              mixedLineEndings = false,
            )
          dirty = false
          report(context.getString(R.string.editor_saved))
        }
    }
  }

  val openLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) load(uri)
    }
  val saveAsLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
      if (uri != null) write(uri, displayName(context, uri))
    }

  LaunchedEffect(initialUri) { if (initialUri != null) load(initialUri) }

  fun save() {
    val current = file
    if (current?.uri != null) write(current.uri, current.name)
    else saveAsLauncher.launch(current?.name ?: "untitled.txt")
  }

  fun applyEdit(next: TextFieldValue) {
    if (next.text != value.text) {
      dirty = true
      message = null
    }
    history.record(next, System.currentTimeMillis())
    value = next
  }

  fun perform(action: Pending) {
    when (action) {
      Pending.CLOSE -> onClose()
      Pending.OPEN -> openLauncher.launch(arrayOf("*/*"))
      Pending.NEW -> install(EditorFile(null, "untitled.txt", LineEnding.LF, false, false), "")
    }
  }

  fun request(action: Pending) {
    if (dirty) pending = action else perform(action)
  }

  fun select(at: Int) {
    value = value.copy(selection = TextRange(at, at + query.length))
  }

  fun findNext() {
    TextSearch.next(value.text, query, value.selection.max, ignoreCase)?.let { select(it) }
      ?: report(context.getString(R.string.editor_no_match))
  }

  fun findPrevious() {
    TextSearch.previous(value.text, query, value.selection.min, ignoreCase)?.let { select(it) }
      ?: report(context.getString(R.string.editor_no_match))
  }

  fun replaceCurrent() {
    val selected = value.text.substring(value.selection.min, value.selection.max)
    if (query.isNotEmpty() && selected.equals(query, ignoreCase = ignoreCase)) {
      val text = value.text.replaceRange(value.selection.min, value.selection.max, replacement)
      val cursor = value.selection.min + replacement.length
      applyEdit(TextFieldValue(text, TextRange(cursor)))
    }
    findNext()
  }

  fun replaceEverywhere() {
    val (text, count) = TextSearch.replaceAll(value.text, query, replacement, ignoreCase)
    if (count > 0) applyEdit(TextFieldValue(text, TextRange(0)))
    report(context.getString(R.string.editor_replaced, count))
  }

  BackHandler(enabled = dirty) { request(Pending.CLOSE) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text((file?.name ?: stringResource(R.string.editor_title)) + if (dirty) " *" else "")
        },
        navigationIcon = {
          TextButton(onClick = { request(Pending.CLOSE) }) { Text(stringResource(R.string.back)) }
        },
        actions = {
          TextButton(onClick = { request(Pending.OPEN) }, enabled = !busy) {
            Text(stringResource(R.string.editor_open))
          }
          if (file != null) {
            TextButton(onClick = { save() }, enabled = !busy && (dirty || file?.uri == null)) {
              Text(stringResource(R.string.editor_save))
            }
            Box {
              TextButton(onClick = { menuOpen = true }) { Text("⋮") }
              DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.editor_save_as)) },
                  onClick = {
                    menuOpen = false
                    saveAsLauncher.launch(file?.name ?: "untitled.txt")
                  },
                )
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.editor_new)) },
                  onClick = {
                    menuOpen = false
                    request(Pending.NEW)
                  },
                )
              }
            }
          }
        },
      )
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
      val current = file
      if (current == null) {
        EmptyEditor(
          busy = busy,
          message = message,
          failed = failed,
          onOpen = { openLauncher.launch(arrayOf("*/*")) },
          onNew = { perform(Pending.NEW) },
        )
        return@Column
      }

      if (findOpen) {
        FindBar(
          query = query,
          onQuery = { query = it },
          replacement = replacement,
          onReplacement = { replacement = it },
          ignoreCase = ignoreCase,
          onIgnoreCase = { ignoreCase = it },
          matches =
            remember(value.text, query, ignoreCase) {
              TextSearch.findAll(value.text, query, ignoreCase).size
            },
          onNext = ::findNext,
          onPrevious = ::findPrevious,
          onReplace = ::replaceCurrent,
          onReplaceAll = ::replaceEverywhere,
        )
        HorizontalDivider()
      }

      Box(Modifier.weight(1f).fillMaxWidth()) {
        EditorBody(value = value, wrap = wrap, onChange = ::applyEdit)
      }

      HorizontalDivider()
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
          onClick = {
            history.undo()?.let {
              value = it
              dirty = true
            }
          },
          enabled = history.canUndo,
        ) {
          Text(stringResource(R.string.editor_undo))
        }
        TextButton(
          onClick = {
            history.redo()?.let {
              value = it
              dirty = true
            }
          },
          enabled = history.canRedo,
        ) {
          Text(stringResource(R.string.editor_redo))
        }
        TextButton(onClick = { findOpen = !findOpen }) {
          Text(stringResource(R.string.editor_find))
        }
        TextButton(onClick = { wrap = !wrap }) {
          Text(stringResource(if (wrap) R.string.editor_wrap_on else R.string.editor_wrap_off))
        }
      }
      val (line, column) = TextSearch.position(value.text, value.selection.end)
      val ending =
        when (current.lineEnding) {
          LineEnding.LF -> "LF"
          LineEnding.CRLF -> "CRLF"
          LineEnding.CR -> "CR"
        }
      Text(
        buildString {
          append(stringResource(R.string.editor_status, line, column, ending))
          if (current.hasBom) append(" · BOM")
        },
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
      if (current.mixedLineEndings) {
        Text(
          stringResource(R.string.editor_mixed_endings, ending),
          style = MaterialTheme.typography.labelSmall,
          modifier = Modifier.padding(horizontal = 16.dp),
        )
      }
      message?.let {
        Text(
          it,
          style = MaterialTheme.typography.labelMedium,
          color =
            if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
      }
    }
  }

  pending?.let { action ->
    ConfirmDialog(
      title = stringResource(R.string.editor_discard_title),
      message = stringResource(R.string.editor_discard_message),
      confirmText = stringResource(R.string.editor_discard),
      dismissText = stringResource(R.string.cancel),
      onConfirm = {
        pending = null
        dirty = false
        perform(action)
      },
      onDismiss = { pending = null },
    )
  }
}

@Composable
private fun EmptyEditor(
  busy: Boolean,
  message: String?,
  failed: Boolean,
  onOpen: () -> Unit,
  onNew: () -> Unit,
) {
  Column(
    Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(stringResource(R.string.editor_empty), style = MaterialTheme.typography.bodyLarge)
    Button(onClick = onOpen, enabled = !busy) { Text(stringResource(R.string.editor_open_file)) }
    OutlinedButton(onClick = onNew, enabled = !busy) { Text(stringResource(R.string.editor_new)) }
    message?.let {
      Text(
        it,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
      )
    }
  }
}

private val EditorTextStyle =
  TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)

/**
 * The text field with a line number gutter. Without wrapping each logical line is one visual line,
 * so the gutter lines up; with wrapping it is hidden.
 */
@Composable
private fun EditorBody(value: TextFieldValue, wrap: Boolean, onChange: (TextFieldValue) -> Unit) {
  val lineCount = remember(value.text) { value.text.count { it == '\n' } + 1 }
  val gutter = remember(lineCount) { (1..lineCount).joinToString("\n") }
  val color = MaterialTheme.colorScheme.onSurface
  Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 4.dp)) {
    if (!wrap) {
      Text(
        gutter,
        style = EditorTextStyle,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.End,
        modifier =
          Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 6.dp),
      )
    }
    BasicTextField(
      value = value,
      onValueChange = onChange,
      textStyle = EditorTextStyle.copy(color = color),
      cursorBrush = SolidColor(color),
      softWrap = wrap,
      modifier =
        Modifier.weight(1f)
          .then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState()))
          .padding(horizontal = 8.dp),
    )
  }
}

@Composable
private fun FindBar(
  query: String,
  onQuery: (String) -> Unit,
  replacement: String,
  onReplacement: (String) -> Unit,
  ignoreCase: Boolean,
  onIgnoreCase: (Boolean) -> Unit,
  matches: Int,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onReplace: () -> Unit,
  onReplaceAll: () -> Unit,
) {
  Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.weight(1f),
        singleLine = true,
        label = { Text(stringResource(R.string.editor_find_label)) },
        supportingText = {
          if (query.isNotEmpty()) Text(stringResource(R.string.editor_matches, matches))
        },
      )
      TextButton(onClick = onPrevious, enabled = matches > 0) { Text("↑") }
      TextButton(onClick = onNext, enabled = matches > 0) { Text("↓") }
    }
    OutlinedTextField(
      value = replacement,
      onValueChange = onReplacement,
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      label = { Text(stringResource(R.string.editor_replace_label)) },
    )
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedButton(onClick = onReplace, enabled = matches > 0) {
        Text(stringResource(R.string.editor_replace))
      }
      OutlinedButton(onClick = onReplaceAll, enabled = matches > 0) {
        Text(stringResource(R.string.editor_replace_all))
      }
      TextButton(onClick = { onIgnoreCase(!ignoreCase) }) {
        Text(
          stringResource(
            if (ignoreCase) R.string.editor_match_case_off else R.string.editor_match_case_on,
          ),
        )
      }
    }
  }
}
