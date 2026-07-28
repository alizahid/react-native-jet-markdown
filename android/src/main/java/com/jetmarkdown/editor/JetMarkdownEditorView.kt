package com.jetmarkdown.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.StateWrapper
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.Event
import com.jetmarkdown.JetMarkdownNative
import com.jetmarkdown.style.StyleConfig

/**
 * WYSIWYG markdown editor: an EditText that publishes its content height
 * into the shadow-node state (autogrow) and emits editing events. Root text
 * attributes come from the same base/paragraph cascade the viewer uses.
 */
@SuppressLint("AppCompatCustomView")
class JetMarkdownEditorView(context: Context) : EditText(context) {
  var stateWrapper: StateWrapper? = null

  private var stylesJson: String = ""
  private var defaultValueApplied = false

  var allowFontScaling = true
    set(value) {
      if (field != value) {
        field = value
        applyTextStyles()
        refreshDisplaySpans(text)
        invalidate()
      }
    }

  private var multiline = true
  private var autoCorrectEnabled = true
  private var capitalizeMode = "sentences"
  private var pendingAutoFocus = false
  private var lastPublishedHeight = 0f

  // Autogrow cap (px); 0 = unbounded. Past it the editor scrolls
  // internally like a textarea.
  private var maxHeightPx = 0
  private var contentExceedsMax = false
  private var scrollAllowed = true

  // Marks armed for text typed at the collapsed cursor; re-derived from the
  // character before the caret whenever the selection moves outside an edit.
  private var pendingMarks = 0

  // Caret position where marks were explicitly toggled. The IME re-syncs
  // its selection asynchronously after external text mutations; a selection
  // event at the SAME position must not wipe a just-armed mark.
  private var pendingArmedAt = -1
  private var lastStateKey = -1L
  private var editInProgress = false
  private var suppressWatcher = false
  private var changeStart = 0
  private var changeInserted = 0
  private var changeReplaced = 0

  // Per-character mark flags of the region an IME re-commit is about to
  // replace ("a" → "ab" replaces the composing word): SSB collapses spans
  // whose whole run is swapped out, so the prefix's marks are restored from
  // this snapshot afterwards.
  private var replacedMarkFlags: IntArray? = null

  private fun markFlagsAt(spanned: Spanned, offset: Int): Int {
    var flags = 0
    for (span in spanned.getSpans(offset, offset + 1, EditorMarkSpan::class.java)) {
      if (spanned.getSpanStart(span) <= offset && spanned.getSpanEnd(span) > offset) {
        flags = flags or span.mark
      }
    }
    return flags
  }

  // TextView's constructor fires onSelectionChanged before any of this
  // class's fields exist; nothing below may run until init completes.
  private var constructed = false

  // Source of truth for per-line blocks (packed type shl 8 or level, one
  // entry per text line). Spliced in the TextWatcher as lines come and go.
  private val lineBlocks = ArrayList<Int>()
  private var editLine = 0
  private var removedNewlines = 0
  private var insertedNewlines = 0

  // Mention trigger session.
  var mentionTriggers: List<String> = emptyList()
  private var mentionActive = false
  private var mentionTrigger = ""
  private var mentionStart = 0
  private var lastMentionQuery: String? = null
  private var linkColor = DEFAULT_LINK_COLOR

  // Resolved lineHeight per context (px); 0 = natural. Headings and code
  // use their own element style, everything else the base/paragraph
  // cascade.
  private var lineHeightPx = 0
  private val headingLineHeightsPx = IntArray(7)
  private var codeLineHeightPx = 0

  // Drawn manually: Fabric never drives onMeasure, and TextView's native
  // hint rendering depends on measure-time layout construction.
  private var placeholderText: String? = null
  private var placeholderColor: Int = DEFAULT_PLACEHOLDER_COLOR
  private val placeholderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

  private val density = context.resources.displayMetrics.density
  private val defaultHighlightColor = highlightColor

  // Fabric assigns exact frames and never runs a parent measure/layout pass,
  // so TextView's internal Layout is not rebuilt after programmatic setText
  // or hint changes. Re-run measure/layout in place against the current
  // frame (the standard fix for text-bearing custom views under Fabric).
  private var relayoutPending = false

  private val measureAndLayout = Runnable {
    relayoutPending = false
    if (width > 0 && height > 0) {
      measure(
        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
      )
      layout(left, top, right, bottom)
    }
  }

  override fun requestLayout() {
    super.requestLayout()
    if (!relayoutPending) {
      relayoutPending = true
      post(measureAndLayout)
    }
  }

  init {
    background = null
    gravity = Gravity.TOP or Gravity.START
    applyTextStyles()
    applyInputType()

    addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        editInProgress = true
        replacedMarkFlags = null
        if (suppressWatcher || s == null) {
          return
        }
        editLine = countNewlines(s, 0, start)
        removedNewlines = countNewlines(s, start, start + count)
        // Word-sized replacements that grow are IME re-commits; snapshot the
        // replaced characters' marks so the prefix can be restored. The size
        // cap keeps selection replacements (e.g. a clipboard-chip paste over
        // a long selection) out of this path — composing words are short.
        if (count in 1..48 && after >= count && s is Spanned) {
          replacedMarkFlags = IntArray(count) { markFlagsAt(s, start + it) }
        }
      }

      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        if (suppressWatcher || s == null) {
          // A nested programmatic edit (block exit) must not clobber the
          // outer edit's bookkeeping mid-afterTextChanged.
          return
        }
        changeStart = start
        changeInserted = count
        changeReplaced = before
        insertedNewlines = countNewlines(s, start, start + count)
      }

      override fun afterTextChanged(s: Editable?) {
        editInProgress = false
        if (suppressWatcher || s == null) {
          return
        }
        spliceLineBlocks()
        if (s.isEmpty()) {
          // Deleting all content resets the document to one plain line;
          // a block must not haunt an empty editor.
          lineBlocks.clear()
          ensureLineBlocks()
          pendingMarks = 0
        }
        var exitedList = false
        if (insertedNewlines == 1 && changeInserted == 1 && removedNewlines == 0) {
          val block = lineBlocks.getOrElse(editLine) { 0 }
          // The Enter split the line at the caret: editLine holds the text
          // before the caret, editLine + 1 the text after it.
          val beforeEmpty = lineContentRange(editLine).isEmpty()
          val afterEmpty = lineContentRange(editLine + 1).isEmpty()
          if (block != 0 && beforeEmpty && afterEmpty) {
            // Enter on any empty formatted line (list item, quote, code
            // block, heading) exits the block instead of continuing it.
            lineBlocks[editLine] = 0
            lineBlocks.removeAt(editLine + 1)
            suppressWatcher = true
            s.delete(changeStart, changeStart + 1)
            suppressWatcher = false
            exitedList = true
          } else if (EditorBlocks.type(block) == EditorBlocks.HEADING) {
            if (beforeEmpty) {
              // Enter at the start of a heading: the heading stays with its
              // text and a plain empty line opens above it.
              lineBlocks[editLine] = 0
            } else if (afterEmpty) {
              // Enter at the end of a heading does not continue it.
              lineBlocks[editLine + 1] = 0
            }
            // Mid-heading splits keep the heading on both halves.
          }
        }
        // The IME often re-commits a whole composing word to append one
        // character ("a" → "ab"). Stamping pending marks over the full
        // insertion would restyle the re-committed prefix (whose spans SSB
        // already preserved), so only the genuinely new suffix is stamped.
        if (!exitedList && changeInserted > changeReplaced && pendingMarks != 0) {
          val stampStart = changeStart + changeReplaced
          for (mark in EditorMarks.ALL) {
            if (pendingMarks and mark != 0) {
              applyMark(s, mark, stampStart, changeStart + changeInserted)
            }
          }
        }
        // Restore the re-committed prefix's marks from the snapshot (the
        // replacement collapsed any span that covered exactly that run).
        val restored = replacedMarkFlags
        if (restored != null && changeReplaced == restored.size &&
          changeInserted >= changeReplaced
        ) {
          for (i in 0 until changeReplaced) {
            val flags = restored[i]
            if (flags == 0) {
              continue
            }
            for (mark in EditorMarks.ALL) {
              if (flags and mark != 0) {
                applyMark(s, mark, changeStart + i, changeStart + i + 1)
              }
            }
          }
        }
        replacedMarkFlags = null
        // Typing strictly inside an atomic token demotes it to plain text.
        if (changeInserted > 0) {
          for (span in s.getSpans(changeStart, changeStart, LinkDataSpan::class.java)) {
            if (span.atomic && s.getSpanStart(span) < changeStart &&
              s.getSpanEnd(span) > changeStart + changeInserted
            ) {
              s.removeSpan(span)
            }
          }
        }
        if (insertedNewlines == 0 && removedNewlines == 0 && s.isNotEmpty()) {
          // Plain in-line keystroke: derived spans on other lines are still
          // valid; rebuilding them all would reflow the whole document.
          refreshDisplaySpansAround(s, changeStart, changeStart + changeInserted)
        } else {
          refreshDisplaySpans(s)
        }
        // The armed-mark guard is positional; any text edit invalidates it.
        pendingArmedAt = -1
        emitContentChanged()
        if (changeInserted == 1 && changeStart < s.length && isWordBreak(s[changeStart])) {
          detectLinkBefore(changeStart)
        }
        updateMentionSession()
        emitState()
      }
    })

    setOnFocusChangeListener { _, hasFocus ->
      emitEvent(if (hasFocus) "topEditorFocus" else "topEditorBlur") { }
    }

    constructed = true
  }

  // Root text attributes: base (style prop text keys) then paragraph,
  // floored at 16pt black — the same cascade root the viewer uses.
  private fun applyTextStyles() {
    val styles = StyleConfig.from(stylesJson)

    var fontSize = 16f
    var fontFamily: String? = null
    var color = Color.BLACK
    var lineHeight = 0f
    for (key in listOf("base", "paragraph")) {
      val spec = styles.textStyleFor(key) ?: continue
      spec.fontSize?.let { fontSize = it }
      spec.fontFamily?.let { fontFamily = it }
      spec.color?.let { color = it }
      spec.lineHeight?.let { lineHeight = it }
    }
    // Must match the shadow node's fontSizeMultiplier (the system scale).
    val fontScale =
      if (allowFontScaling) resources.configuration.fontScale else 1.0f
    fontSize *= fontScale
    lineHeight *= fontScale
    lineHeightPx = (lineHeight * density).toInt()
    for (level in 1..6) {
      headingLineHeightsPx[level] =
        ((styles.textStyleFor("h$level")?.lineHeight ?: 0f) * density * fontScale).toInt()
    }
    codeLineHeightPx =
      styles.textStyleFor("codeBlock")?.lineHeight
        ?.let { (it * density * fontScale).toInt() }
        ?: lineHeightPx

    setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * density)
    setTextColor(color)
    // ReactFontManager understands RN/Expo custom fonts; Typeface.create
    // silently falls back to the system default for them.
    typeface = com.jetmarkdown.style.Fonts.resolve(context, fontFamily, 400, italic = false)
    styles.textStyleFor("link")?.color?.let { linkColor = it }

    setPadding(
      (styles.paddingLeft * density).toInt(),
      (styles.paddingTop * density).toInt(),
      (styles.paddingRight * density).toInt(),
      (styles.paddingBottom * density).toInt(),
    )
    styles.backgroundColor?.let { setBackgroundColor(it) }

    publishHeight()
  }

  // Autocorrect/autocapitalize/suggestions are suppressed while the caret
  // is in a code context (`let` must not become `Let`).
  private var codeContextActive = false

  private fun lineIsCode(line: Int): Boolean =
    EditorBlocks.type(lineBlocks.getOrElse(line) { 0 }) == EditorBlocks.CODE

  private fun caretInCodeContext(): Boolean {
    if (pendingMarks and EditorMarks.INLINE_CODE != 0) {
      return true
    }
    return lineIsCode(lineIndexAt(selectionStart.coerceAtLeast(0)))
  }

  private fun updateInputTypeForContext() {
    val inCode = caretInCodeContext()
    if (inCode != codeContextActive) {
      codeContextActive = inCode
      applyInputType()
    }
  }

  // Setting inputType resets the typeface, so callers reapply styles after.
  private fun applyInputType() {
    var type = InputType.TYPE_CLASS_TEXT
    if (multiline) {
      type = type or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    }
    if (autoCorrectEnabled && !codeContextActive) {
      type = type or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
    }
    if (codeContextActive) {
      type = type or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }
    type = type or when (if (codeContextActive) "none" else capitalizeMode) {
      "words" -> InputType.TYPE_TEXT_FLAG_CAP_WORDS
      "characters" -> InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
      "none" -> 0
      else -> InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    }
    val selection = selectionStart
    val selectionUpper = selectionEnd
    inputType = type
    isSingleLine = !multiline
    if (selection in 0..text.length && selectionUpper in selection..text.length) {
      setSelection(selection, selectionUpper)
    }
    applyTextStyles()
  }

  fun setStylesJson(value: String?) {
    val json = value ?: ""
    if (json != stylesJson) {
      stylesJson = json
      applyTextStyles()
      // Existing derived spans carry the OLD line heights / link color; a
      // theme change must restyle content that is already there.
      refreshDisplaySpans(text)
      invalidate()
    }
  }

  fun setDefaultValue(value: String?) {
    if (!defaultValueApplied) {
      defaultValueApplied = true
      if (!value.isNullOrEmpty()) {
        setMarkdownValue(value)
      }
    }
  }

  /** Parsed as markdown into text + inline-mark spans + line blocks. */
  fun setMarkdownValue(markdown: String) {
    val decoded = JetMarkdownNative.editorFromMarkdown(markdown)
    suppressWatcher = true
    setText(decoded.text)
    suppressWatcher = false
    val editable = text
    applyRuns(editable, decoded.runs)
    for ((index, url) in decoded.linkUrls.withIndex()) {
      val start = decoded.linkRanges[index * 2].coerceIn(0, editable.length)
      val end = decoded.linkRanges[index * 2 + 1].coerceIn(start, editable.length)
      if (end > start) {
        editable.setSpan(
          LinkDataSpan(url, atomic = false),
          start,
          end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
      }
    }
    lineBlocks.clear()
    var index = 0
    while (index + 2 <= decoded.lineBlocks.size) {
      lineBlocks.add(
        EditorBlocks.pack(decoded.lineBlocks[index], decoded.lineBlocks[index + 1]),
      )
      index += 2
    }
    ensureLineBlocks()
    refreshDisplaySpans(editable)
    setSelection(text.length)
    emitContentChanged()
    emitState()
  }

  private fun applyRuns(editable: Editable, runs: IntArray) {
    var index = 0
    while (index + 3 <= runs.size) {
      val start = runs[index].coerceIn(0, editable.length)
      val end = runs[index + 1].coerceIn(start, editable.length)
      val flags = runs[index + 2]
      if (end > start) {
        for (mark in EditorMarks.ALL) {
          if (flags and mark != 0) {
            editable.setSpan(
              EditorMarkSpan(mark),
              start,
              end,
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
          }
        }
      }
      index += 3
    }
  }

  private fun countNewlines(s: CharSequence, from: Int, to: Int): Int {
    var count = 0
    for (i in from.coerceAtLeast(0) until to.coerceAtMost(s.length)) {
      if (s[i] == '\n') {
        count++
      }
    }
    return count
  }

  private fun lineCount(): Int = countNewlines(text, 0, text.length) + 1

  private fun lineIndexAt(offset: Int): Int = countNewlines(text, 0, offset)

  private fun lineStartOffset(index: Int): Int {
    var start = 0
    var line = 0
    while (line < index) {
      val newline = text.indexOf('\n', start)
      if (newline == -1) {
        return text.length
      }
      start = newline + 1
      line++
    }
    return start
  }

  private fun lineContentRange(index: Int): IntRange {
    val start = lineStartOffset(index)
    val newline = text.indexOf('\n', start)
    val end = if (newline == -1) text.length else newline
    return start until end
  }

  private fun ensureLineBlocks() {
    val count = lineCount()
    while (lineBlocks.size < count) {
      lineBlocks.add(0)
    }
    while (lineBlocks.size > count) {
      lineBlocks.removeAt(lineBlocks.size - 1)
    }
  }

  // Keeps lineBlocks aligned as the edit adds/removes lines: new lines
  // inherit the edited line's block so lists and quotes continue on Enter.
  private fun spliceLineBlocks() {
    if (lineBlocks.isEmpty()) {
      ensureLineBlocks()
      return
    }
    val anchor = editLine.coerceIn(0, lineBlocks.size - 1)
    val inherited = lineBlocks[anchor]
    repeat(removedNewlines) {
      if (anchor + 1 < lineBlocks.size) {
        lineBlocks.removeAt(anchor + 1)
      }
    }
    repeat(insertedNewlines) {
      lineBlocks.add((anchor + 1).coerceAtMost(lineBlocks.size), inherited)
    }
    ensureLineBlocks()
  }

  /** Toggles a block over every line the selection touches. */
  fun toggleBlock(type: Int, level: Int) {
    ensureLineBlocks()
    val target = EditorBlocks.pack(type, level)
    val startLine = lineIndexAt(selectionStart.coerceAtLeast(0))
    val endLine = lineIndexAt(selectionEnd.coerceAtLeast(selectionStart.coerceAtLeast(0)))
    val all = (startLine..endLine).all { lineBlocks.getOrElse(it) { 0 } == target }
    val next = if (all) 0 else target
    for (index in startLine..endLine) {
      if (index < lineBlocks.size) {
        lineBlocks[index] = next
      }
    }
    if (EditorBlocks.type(next) == EditorBlocks.CODE) {
      // A code fence carries raw text only: inline marks and links on the
      // converted lines would be dropped by the serializer, so shed them
      // now rather than displaying formatting that cannot survive.
      val editable = text
      val rangeStart = lineStartOffset(startLine)
      val rangeEnd = lineContentRange(endLine).last + 1
      for (mark in EditorMarks.ALL) {
        removeMark(editable, mark, rangeStart, rangeEnd)
      }
      for (span in editable.getSpans(rangeStart, rangeEnd, LinkDataSpan::class.java)) {
        editable.removeSpan(span)
      }
      pendingMarks = 0
    }
    refreshDisplaySpans(text)
    updateInputTypeForContext()
    invalidate()
    emitContentChanged()
    emitState()
  }

  // Backspace at the start of a formatted line clears the block first
  // (intercepted at the InputConnection level; TextWatcher cannot veto).
  override fun onCreateInputConnection(outAttrs: android.view.inputmethod.EditorInfo):
    android.view.inputmethod.InputConnection? {
    val base = super.onCreateInputConnection(outAttrs) ?: return null
    return object : android.view.inputmethod.InputConnectionWrapper(base, true) {
      override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        if (beforeLength == 1 && afterLength == 0 && interceptBackspace()) {
          return true
        }
        return super.deleteSurroundingText(beforeLength, afterLength)
      }

      override fun sendKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN &&
          event.keyCode == android.view.KeyEvent.KEYCODE_DEL &&
          interceptBackspace()
        ) {
          return true
        }
        return super.sendKeyEvent(event)
      }
    }
  }

  private fun interceptBackspace(): Boolean {
    val start = selectionStart
    if (start != selectionEnd || start < 0) {
      return false
    }
    val editable = text
    if (start > 0) {
      // Backspacing into an atomic token removes the whole token.
      for (span in editable.getSpans(start - 1, start, LinkDataSpan::class.java)) {
        if (!span.atomic) {
          continue
        }
        val spanStart = editable.getSpanStart(span)
        val spanEnd = editable.getSpanEnd(span)
        if (start - 1 in spanStart until spanEnd) {
          editable.removeSpan(span)
          editable.delete(spanStart, spanEnd)
          return true
        }
      }
    }
    // Backspace at a line start (including the document start) clears the
    // line's block before deleting anything.
    if (start > 0 && text[start - 1] != '\n') {
      return false
    }
    val line = lineIndexAt(start)
    if (lineBlocks.getOrElse(line) { 0 } == 0) {
      return false
    }
    lineBlocks[line] = 0
    refreshDisplaySpans(text)
    updateInputTypeForContext()
    invalidate()
    emitContentChanged()
    emitState()
    return true
  }

  // Paste never inserts directly: the clipboard is reported to JS, which
  // owns the default insertion (via insertMarkdown) unless prevented.
  override fun onTextContextMenuItem(id: Int): Boolean {
    if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
      reportPaste()
      return true
    }
    return super.onTextContextMenuItem(id)
  }

  private fun reportPaste() {
    val clipboard =
      context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    val clip = clipboard?.primaryClip ?: return
    var textContent = ""
    val images = Arguments.createArray()
    for (index in 0 until clip.itemCount) {
      val item = clip.getItemAt(index)
      val uri = item.uri
      if (uri != null) {
        val options = android.graphics.BitmapFactory.Options().apply {
          inJustDecodeBounds = true
        }
        runCatching {
          context.contentResolver.openInputStream(uri)?.use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream, null, options)
          }
        }
        if (options.outWidth > 0) {
          images.pushMap(
            Arguments.createMap().apply {
              putString("url", uri.toString())
              putDouble("width", options.outWidth.toDouble())
              putDouble("height", options.outHeight.toDouble())
            },
          )
          continue
        }
      }
      if (textContent.isEmpty()) {
        textContent = item.coerceToText(context)?.toString() ?: ""
      }
    }
    emitEvent("topEditorPaste") {
      putString("text", textContent)
      putArray("images", images)
    }
  }

  /** Parses markdown and inserts it at the cursor / over the selection. */
  fun insertMarkdownAt(markdown: String) {
    if (markdown.isEmpty()) {
      return
    }
    val decoded = JetMarkdownNative.editorFromMarkdown(markdown)
    val start = selectionStart.coerceAtLeast(0)
    val end = selectionEnd.coerceAtLeast(start)
    val startLine = lineIndexAt(start)
    val editable = text
    val removedLines = countNewlines(editable, start, end)
    val decodedLineCount = decoded.lineBlocks.size / 2
    suppressWatcher = true
    editable.replace(start, end, decoded.text)
    suppressWatcher = false
    // The replace ran under suppressWatcher, so the watcher's lineBlocks
    // splice never happened. Splice here or every line AFTER the paste
    // point keeps a stale block entry (a heading below the caret would be
    // silently stripped by the block-copy loop overwriting shifted
    // indices).
    repeat(removedLines) {
      if (startLine + 1 < lineBlocks.size) {
        lineBlocks.removeAt(startLine + 1)
      }
    }
    repeat((decodedLineCount - 1).coerceAtLeast(0)) {
      lineBlocks.add((startLine + 1).coerceAtMost(lineBlocks.size), 0)
    }
    ensureLineBlocks()

    var runIndex = 0
    while (runIndex + 3 <= decoded.runs.size) {
      val runStart = (decoded.runs[runIndex] + start).coerceIn(0, editable.length)
      val runEnd = (decoded.runs[runIndex + 1] + start).coerceIn(runStart, editable.length)
      val flags = decoded.runs[runIndex + 2]
      if (runEnd > runStart) {
        for (mark in EditorMarks.ALL) {
          if (flags and mark != 0) {
            editable.setSpan(
              EditorMarkSpan(mark),
              runStart,
              runEnd,
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
          }
        }
      }
      runIndex += 3
    }
    for ((index, url) in decoded.linkUrls.withIndex()) {
      val linkStart = (decoded.linkRanges[index * 2] + start).coerceIn(0, editable.length)
      val linkEnd =
        (decoded.linkRanges[index * 2 + 1] + start).coerceIn(linkStart, editable.length)
      if (linkEnd > linkStart) {
        editable.setSpan(
          LinkDataSpan(url, atomic = false),
          linkStart,
          linkEnd,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
      }
    }
    for (k in 0 until decodedLineCount) {
      val lineIndex = startLine + k
      if (lineIndex >= lineBlocks.size) {
        break
      }
      val block = EditorBlocks.pack(decoded.lineBlocks[k * 2], decoded.lineBlocks[k * 2 + 1])
      // The first pasted line joins the current line; its existing block
      // wins unless it is a plain paragraph.
      if (k > 0 || lineBlocks[lineIndex] == 0) {
        lineBlocks[lineIndex] = block
      }
    }

    refreshDisplaySpans(editable)
    setSelection((start + decoded.text.length).coerceAtMost(editable.length))
    emitContentChanged()
    emitState()
  }

  // Hardware keyboard formatting shortcuts (Ctrl+B / Ctrl+I).
  override fun onKeyShortcut(keyCode: Int, event: android.view.KeyEvent): Boolean {
    when (keyCode) {
      android.view.KeyEvent.KEYCODE_B -> {
        toggleMark(EditorMarks.BOLD)
        return true
      }
      android.view.KeyEvent.KEYCODE_I -> {
        toggleMark(EditorMarks.ITALIC)
        return true
      }
      else -> return super.onKeyShortcut(keyCode, event)
    }
  }

  /** Links the selection, or inserts a linked label at the caret. */
  fun insertLink(url: String, label: String) {
    if (url.isEmpty()) {
      return
    }
    val start = selectionStart.coerceAtLeast(0)
    val end = selectionEnd.coerceAtLeast(start)
    val editable = text
    if (start == end) {
      val content = label.ifEmpty { url }
      suppressWatcher = true
      editable.insert(start, content)
      suppressWatcher = false
      ensureLineBlocks()
      editable.setSpan(
        LinkDataSpan(url, atomic = false),
        start,
        start + content.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
      )
      setSelection(start + content.length)
    } else {
      editable.setSpan(
        LinkDataSpan(url, atomic = false),
        start,
        end,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
      )
      setSelection(end)
    }
    refreshDisplaySpans(editable)
    emitContentChanged()
  }

  /** Removes the link covering the selection or the caret. */
  fun removeLink() {
    val start = selectionStart.coerceAtLeast(0)
    val end = selectionEnd.coerceAtLeast(start)
    val editable = text
    val probeStart = if (start == end) (start - 1).coerceAtLeast(0) else start
    var removed = false
    for (span in editable.getSpans(probeStart, end, LinkDataSpan::class.java)) {
      editable.removeSpan(span)
      removed = true
    }
    if (removed) {
      refreshDisplaySpans(editable)
      emitContentChanged()
    }
  }

  /** Inserts an atomic mention token, replacing any active query. */
  fun insertMention(trigger: String, label: String, url: String) {
    if (label.isEmpty() || url.isEmpty()) {
      return
    }
    val editable = text
    val caret = selectionStart.coerceAtLeast(0)
    val start = if (mentionActive) mentionStart else caret
    val end = caret.coerceAtLeast(start)
    val token = trigger + label
    suppressWatcher = true
    editable.replace(start, end, "$token ")
    suppressWatcher = false
    ensureLineBlocks()
    editable.setSpan(
      LinkDataSpan(url, atomic = true),
      start,
      start + token.length,
      Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
    endMentionSession()
    setSelection(start + token.length + 1)
    refreshDisplaySpans(editable)
    emitContentChanged()
  }

  private fun endMentionSession() {
    if (!mentionActive) {
      return
    }
    mentionActive = false
    lastMentionQuery = null
    emitEvent("topEditorMentionEnd") { putString("trigger", mentionTrigger) }
  }

  private fun isWordBreak(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n'

  // Starts, updates, or ends the mention session from the text between the
  // trigger and the caret.
  private fun updateMentionSession() {
    if (mentionTriggers.isEmpty()) {
      return
    }
    val editable = text
    val start = selectionStart
    val end = selectionEnd
    if (start != end || start < 0) {
      endMentionSession()
      return
    }

    if (mentionActive) {
      var valid = mentionStart < editable.length && start > mentionStart
      if (valid && mentionTrigger != editable[mentionStart].toString()) {
        valid = false
      }
      var query = ""
      if (valid) {
        query = editable.substring(mentionStart + 1, start)
        if (query.any { isWordBreak(it) }) {
          valid = false
        }
      }
      if (!valid) {
        endMentionSession()
        return
      }
      if (query == lastMentionQuery) {
        return
      }
      lastMentionQuery = query
      emitEvent("topEditorMentionChange") {
        putString("query", query)
        putString("trigger", mentionTrigger)
      }
      return
    }

    if (start == 0) {
      return
    }
    val last = editable[start - 1].toString()
    if (last !in mentionTriggers) {
      return
    }
    if (start >= 2 && !isWordBreak(editable[start - 2])) {
      return
    }
    mentionActive = true
    mentionTrigger = last
    mentionStart = start - 1
    emitEvent("topEditorMentionStart") { putString("trigger", last) }
  }

  // After a word break lands, reports a bare URL the completed word forms.
  private fun detectLinkBefore(position: Int) {
    val editable = text
    if (position > editable.length) {
      return
    }
    var wordStart = position
    while (wordStart > 0 && !isWordBreak(editable[wordStart - 1])) {
      wordStart--
    }
    if (wordStart >= position) {
      return
    }
    val word = editable.substring(wordStart, position)
    if (!word.startsWith("http://") && !word.startsWith("https://")) {
      return
    }
    if (word == "http://" || word == "https://") {
      return
    }
    if (editable.getSpans(wordStart, position, LinkDataSpan::class.java).isNotEmpty()) {
      return
    }
    // Linkify in place: a bare URL re-parses as an autolink in any markdown
    // renderer, so the editor must show it as a link too (WYSIWYG). The app
    // can still restyle or remove it from the onLinkDetected callback.
    if (!lineIsCode(lineIndexAt(wordStart))) {
      editable.setSpan(
        LinkDataSpan(word, atomic = false),
        wordStart,
        position,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
      )
      refreshDisplaySpansAround(editable, wordStart, position)
      emitContentChanged()
    }
    emitEvent("topEditorLinkDetected") { putString("url", word) }
  }

  /** Toggles a mark over the selection, or arms it for typed text. */
  fun toggleMark(mark: Int) {
    val start = selectionStart.coerceAtLeast(0)
    val end = selectionEnd.coerceAtLeast(start)
    // A code fence carries raw text only — marks applied there would render
    // in the editor but silently vanish from the markdown, so refuse them.
    for (line in lineIndexAt(start)..lineIndexAt(end)) {
      if (EditorBlocks.type(lineBlocks.getOrElse(line) { 0 }) == EditorBlocks.CODE) {
        return
      }
    }
    // Superscript and subscript are mutually exclusive: a glyph cannot sit
    // above and below the baseline, and combined they serialize to nested
    // ^~…~^ that does not round-trip.
    val exclusive = when (mark) {
      EditorMarks.SUPERSCRIPT -> EditorMarks.SUBSCRIPT
      EditorMarks.SUBSCRIPT -> EditorMarks.SUPERSCRIPT
      else -> 0
    }
    if (start == end) {
      pendingMarks = pendingMarks xor mark
      if (pendingMarks and mark != 0) {
        pendingMarks = pendingMarks and exclusive.inv()
      }
      pendingArmedAt = start
      updateInputTypeForContext()
      emitState()
      return
    }
    val editable = text
    if (commonMarksInRange(editable, start, end) and mark != 0) {
      removeMark(editable, mark, start, end)
    } else {
      if (exclusive != 0) {
        removeMark(editable, exclusive, start, end)
      }
      applyMark(editable, mark, start, end)
    }
    refreshDisplaySpans(editable)
    setSelection(start, end)
    emitContentChanged()
    emitState()
  }

  /** Adds a mark over [start, end), merging with touching same-mark spans. */
  private fun applyMark(editable: Editable, mark: Int, start: Int, end: Int) {
    var newStart = start
    var newEnd = end
    for (span in editable.getSpans(start, end, EditorMarkSpan::class.java)) {
      if (span.mark != mark) {
        continue
      }
      newStart = minOf(newStart, editable.getSpanStart(span))
      newEnd = maxOf(newEnd, editable.getSpanEnd(span))
      editable.removeSpan(span)
    }
    editable.setSpan(
      EditorMarkSpan(mark),
      newStart,
      newEnd,
      Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
  }

  private fun removeMark(editable: Editable, mark: Int, start: Int, end: Int) {
    for (span in editable.getSpans(start, end, EditorMarkSpan::class.java)) {
      if (span.mark != mark) {
        continue
      }
      val spanStart = editable.getSpanStart(span)
      val spanEnd = editable.getSpanEnd(span)
      editable.removeSpan(span)
      if (spanStart < start) {
        editable.setSpan(
          EditorMarkSpan(mark),
          spanStart,
          start,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
      }
      if (spanEnd > end) {
        editable.setSpan(
          EditorMarkSpan(mark),
          end,
          spanEnd,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
      }
    }
  }

  /** Marks present across the ENTIRE range (the AND). */
  private fun commonMarksInRange(editable: Editable, start: Int, end: Int): Int {
    var common = 0
    for (mark in EditorMarks.ALL) {
      val intervals = editable.getSpans(start, end, EditorMarkSpan::class.java)
        .filter { it.mark == mark }
        .map { editable.getSpanStart(it) to editable.getSpanEnd(it) }
        .sortedBy { it.first }
      var covered = start
      for ((spanStart, spanEnd) in intervals) {
        if (spanStart > covered) {
          break
        }
        covered = maxOf(covered, spanEnd)
      }
      if (covered >= end) {
        common = common or mark
      }
    }
    return common
  }

  private fun marksAt(editable: Editable, position: Int): Int {
    if (position < 0 || position >= editable.length) {
      return 0
    }
    var flags = 0
    for (span in editable.getSpans(position, position + 1, EditorMarkSpan::class.java)) {
      if (editable.getSpanStart(span) <= position && editable.getSpanEnd(span) > position) {
        flags = flags or span.mark
      }
    }
    return flags
  }

  /**
   * Rebuilds the derived visual spans inside [regionStart, regionEnd):
   * inline spans from the data mark spans (boundary points partition the
   * text into constant-flag intervals), and per-line block spans from
   * lineBlocks. The default region is the whole document.
   */
  private fun refreshDisplaySpans(
    editable: Editable,
    regionStart: Int = 0,
    regionEnd: Int = editable.length,
  ) {
    for (span in editable.getSpans(regionStart, regionEnd, EditorDerivedSpan::class.java)) {
      if (editable.getSpanStart(span) < regionEnd && editable.getSpanEnd(span) > regionStart) {
        editable.removeSpan(span)
      }
    }

    for (span in editable.getSpans(regionStart, regionEnd, LinkDataSpan::class.java)) {
      val start = editable.getSpanStart(span)
      val end = editable.getSpanEnd(span)
      if (start >= regionEnd || end <= regionStart) {
        continue
      }
      if (end > start) {
        editable.setSpan(
          LinkDisplaySpan(linkColor),
          start,
          end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
      }
    }

    val marks = editable.getSpans(regionStart, regionEnd, EditorMarkSpan::class.java)
      .filter { editable.getSpanStart(it) < regionEnd && editable.getSpanEnd(it) > regionStart }
    if (marks.isNotEmpty()) {
      // Sweep over sorted edges instead of rescanning every mark span per
      // interval (that scan was O(spans²) per keystroke).
      val starts = marks.map { editable.getSpanStart(it).coerceIn(0, editable.length) to it.mark }
        .sortedBy { it.first }
      val ends = marks.map { editable.getSpanEnd(it).coerceIn(0, editable.length) to it.mark }
        .sortedBy { it.first }
      val cuts = sortedSetOf(regionStart, regionEnd)
      for ((position, _) in starts) {
        cuts.add(position)
      }
      for ((position, _) in ends) {
        cuts.add(position)
      }
      val bitCounts = IntArray(32)
      var startIdx = 0
      var endIdx = 0
      var active = 0
      val points = cuts.toIntArray()
      for (i in 0 until points.size - 1) {
        val start = points[i]
        val end = points[i + 1]
        if (end <= start) {
          continue
        }
        while (endIdx < ends.size && ends[endIdx].first <= start) {
          val mark = ends[endIdx].second
          for (bit in 0 until 32) {
            if (mark and (1 shl bit) != 0 && --bitCounts[bit] == 0) {
              active = active and (1 shl bit).inv()
            }
          }
          endIdx++
        }
        while (startIdx < starts.size && starts[startIdx].first <= start) {
          val mark = starts[startIdx].second
          for (bit in 0 until 32) {
            if (mark and (1 shl bit) != 0 && bitCounts[bit]++ == 0) {
              active = active or (1 shl bit)
            }
          }
          startIdx++
        }
        if (active != 0) {
          editable.setSpan(
            EditorDisplaySpan(active),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
          )
        }
      }
    }

    ensureLineBlocks()
    var lineStart = lineStartOffset(lineIndexAt(regionStart))
    var index = lineIndexAt(regionStart)
    // Ordered markers number from the start of their contiguous group,
    // which may sit before the region; recover the count from lineBlocks.
    var orderedNumber = 0
    var probe = index - 1
    while (probe >= 0 && EditorBlocks.type(lineBlocks.getOrElse(probe) { 0 }) == EditorBlocks.ORDERED) {
      orderedNumber++
      probe--
    }
    while (true) {
      val newline = editable.indexOf('\n', lineStart)
      val lineEnd = if (newline == -1) editable.length else newline
      val block = lineBlocks.getOrElse(index) { 0 }
      val type = EditorBlocks.type(block)
      orderedNumber = if (type == EditorBlocks.ORDERED) orderedNumber + 1 else 0
      if (lineEnd > lineStart) {
        val span: Any? = when (type) {
          EditorBlocks.HEADING -> HeadingDisplaySpan(EditorBlocks.level(block))
          EditorBlocks.QUOTE -> QuoteDisplaySpan(density)
          EditorBlocks.CODE -> CodeLineDisplaySpan()
          EditorBlocks.BULLET -> ListMarkerDisplaySpan("•", density)
          EditorBlocks.ORDERED -> ListMarkerDisplaySpan("$orderedNumber.", density)
          else -> null
        }
        if (span != null) {
          editable.setSpan(span, lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
      }
      if (newline != -1) {
        // The heading's newline must not extend the heading's font run, or
        // the trailing empty line's caret inherits heading metrics.
        if (type == EditorBlocks.HEADING) {
          editable.setSpan(
            NewlineResetSpan(),
            newline,
            newline + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
          )
        }
      }
      // Line height from the styles cascade: headings/code use their
      // element style, everything else base/paragraph (0 = natural).
      val lineHeight = when (type) {
        EditorBlocks.HEADING -> headingLineHeightsPx[EditorBlocks.level(block).coerceIn(1, 6)]
        EditorBlocks.CODE -> codeLineHeightPx
        else -> lineHeightPx
      }
      val spanEnd = if (newline == -1) lineEnd else newline + 1
      if (lineHeight > 0 && spanEnd > lineStart) {
        editable.setSpan(
          EditorLineHeightSpan(lineHeight),
          lineStart,
          spanEnd,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
      }
      if (newline == -1 || newline + 1 >= regionEnd) {
        break
      }
      lineStart = newline + 1
      index++
    }
  }

  /**
   * Scoped refresh for plain in-line keystrokes: expands the edit to whole
   * lines plus any span it touches, so derived spans elsewhere (and their
   * DynamicLayout reflows) are left alone. Structural edits (newlines,
   * empty doc) take the full-document path — ordered-list renumbering can
   * ripple arbitrarily far.
   */
  private fun refreshDisplaySpansAround(editable: Editable, editStart: Int, editEnd: Int) {
    var start = editStart.coerceIn(0, editable.length)
    var end = editEnd.coerceIn(start, editable.length)
    while (true) {
      var expandedStart = start
      var expandedEnd = end
      // getSpans also returns spans merely TOUCHING the probe; absorbing
      // those would chain across adjacent line-height spans to the whole
      // document, so only strict overlaps expand the region.
      for (span in editable.getSpans(start, end, EditorDerivedSpan::class.java)) {
        if (editable.getSpanStart(span) < end && editable.getSpanEnd(span) > start) {
          expandedStart = minOf(expandedStart, editable.getSpanStart(span))
          expandedEnd = maxOf(expandedEnd, editable.getSpanEnd(span))
        }
      }
      for (span in editable.getSpans(start, end, EditorMarkSpan::class.java)) {
        if (editable.getSpanStart(span) < end && editable.getSpanEnd(span) > start) {
          expandedStart = minOf(expandedStart, editable.getSpanStart(span))
          expandedEnd = maxOf(expandedEnd, editable.getSpanEnd(span))
        }
      }
      for (span in editable.getSpans(start, end, LinkDataSpan::class.java)) {
        if (editable.getSpanStart(span) < end && editable.getSpanEnd(span) > start) {
          expandedStart = minOf(expandedStart, editable.getSpanStart(span))
          expandedEnd = maxOf(expandedEnd, editable.getSpanEnd(span))
        }
      }
      while (expandedStart > 0 && editable[expandedStart - 1] != '\n') {
        expandedStart--
      }
      while (expandedEnd < editable.length && editable[expandedEnd] != '\n') {
        expandedEnd++
      }
      if (expandedEnd < editable.length) {
        // Include the newline: line-height spans cover it.
        expandedEnd++
      }
      if (expandedStart == start && expandedEnd == end) {
        break
      }
      start = expandedStart.coerceIn(0, editable.length)
      end = expandedEnd.coerceIn(start, editable.length)
    }
    refreshDisplaySpans(editable, start, end)
  }

  private fun serializedMarkdown(): String {
    val editable = text
    val spans = editable.getSpans(0, editable.length, EditorMarkSpan::class.java)
    val runs = IntArray(spans.size * 3)
    for ((index, span) in spans.withIndex()) {
      runs[index * 3] = editable.getSpanStart(span)
      runs[index * 3 + 1] = editable.getSpanEnd(span)
      runs[index * 3 + 2] = span.mark
    }
    ensureLineBlocks()
    val blocks = IntArray(lineBlocks.size * 2)
    for ((index, block) in lineBlocks.withIndex()) {
      blocks[index * 2] = EditorBlocks.type(block)
      blocks[index * 2 + 1] = EditorBlocks.level(block)
    }
    val links = editable.getSpans(0, editable.length, LinkDataSpan::class.java)
      .sortedBy { editable.getSpanStart(it) }
    val linkRanges = IntArray(links.size * 2)
    val linkUrls = ArrayList<String>(links.size)
    for ((index, span) in links.withIndex()) {
      linkRanges[index * 2] = editable.getSpanStart(span)
      linkRanges[index * 2 + 1] = editable.getSpanEnd(span)
      linkUrls.add(span.url)
    }
    return JetMarkdownNative.markdownFromEditor(
      editable.toString(),
      runs,
      blocks,
      linkRanges,
      linkUrls,
    )
  }

  private var markdownEmitPending = false

  private fun emitContentChanged() {
    publishHeight()
    emitEvent("topEditorChangeText") { putString("text", text.toString()) }
    // Serializing the whole document (spans + JNI round trip) per keystroke
    // is the expensive half; coalesce bursts to one emission per frame.
    if (!markdownEmitPending) {
      markdownEmitPending = true
      post {
        markdownEmitPending = false
        if (stateWrapper != null) {
          emitEvent("topEditorChangeMarkdown") { putString("markdown", serializedMarkdown()) }
        }
      }
    }
  }

  private fun emitState() {
    val start = selectionStart.coerceAtLeast(0)
    val end = selectionEnd.coerceAtLeast(start)
    val flags = if (start == end) pendingMarks else commonMarksInRange(text, start, end)
    val block = lineBlocks.getOrElse(lineIndexAt(start)) { 0 }
    val stateKey = (block.toLong() shl 32) or flags.toLong()
    if (stateKey == lastStateKey) {
      return
    }
    lastStateKey = stateKey
    val type = EditorBlocks.type(block)
    emitEvent("topEditorChangeState") {
      putInt(
        "headingLevel",
        if (type == EditorBlocks.HEADING) EditorBlocks.level(block) else 0,
      )
      putBoolean("isBlockQuote", type == EditorBlocks.QUOTE)
      putBoolean("isBold", flags and EditorMarks.BOLD != 0)
      putBoolean("isCodeBlock", type == EditorBlocks.CODE)
      putBoolean("isInlineCode", flags and EditorMarks.INLINE_CODE != 0)
      putBoolean("isItalic", flags and EditorMarks.ITALIC != 0)
      putBoolean("isOrderedList", type == EditorBlocks.ORDERED)
      putBoolean("isSpoiler", flags and EditorMarks.SPOILER != 0)
      putBoolean("isStrikethrough", flags and EditorMarks.STRIKETHROUGH != 0)
      putBoolean("isSubscript", flags and EditorMarks.SUBSCRIPT != 0)
      putBoolean("isSuperscript", flags and EditorMarks.SUPERSCRIPT != 0)
      putBoolean("isUnorderedList", type == EditorBlocks.BULLET)
    }
  }

  fun setMultiline(value: Boolean) {
    if (multiline != value) {
      multiline = value
      applyInputType()
    }
  }

  fun setAutoCorrectEnabled(value: Boolean) {
    if (autoCorrectEnabled != value) {
      autoCorrectEnabled = value
      applyInputType()
    }
  }

  fun setCapitalizeMode(value: String?) {
    val mode = value ?: "sentences"
    if (capitalizeMode != mode) {
      capitalizeMode = mode
      applyInputType()
    }
  }

  fun setPlaceholderText(value: String?) {
    if (placeholderText != value) {
      placeholderText = value
      // Fabric never renders TextView's own hint (drawn manually in
      // onDraw), but TalkBack still announces it.
      hint = value
      invalidate()
    }
  }

  fun setPlaceholderColor(value: Int) {
    val next = if (value != 0) value else DEFAULT_PLACEHOLDER_COLOR
    if (placeholderColor != next) {
      placeholderColor = next
      invalidate()
    }
  }

  private val decorationPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val decorationTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
  private val placeholderMetrics = Paint.FontMetrics()

  override fun onDraw(canvas: Canvas) {
    drawBlockDecorations(canvas)
    super.onDraw(canvas)
    val hint = placeholderText
    if (!hint.isNullOrEmpty() && text.isEmpty()) {
      placeholderPaint.textSize = textSize
      placeholderPaint.typeface = typeface
      placeholderPaint.color = placeholderColor
      placeholderPaint.getFontMetrics(placeholderMetrics)
      canvas.drawText(
        hint,
        compoundPaddingLeft.toFloat(),
        compoundPaddingTop - placeholderMetrics.top,
        placeholderPaint,
      )
    }
  }

  /**
   * Block visuals spans cannot provide: the full-width code stripe (drawn
   * per contiguous code GROUP, empty lines included) and markers/bars for
   * empty block lines (spans need at least one character).
   */
  private fun drawBlockDecorations(canvas: Canvas) {
    val textLayout = layout ?: return
    // Plain-paragraph documents (the common case) draw nothing; skip the
    // per-line text scan the cursor-blink invalidations would otherwise pay.
    if (lineBlocks.none { it != 0 }) {
      return
    }
    val offsetY = compoundPaddingTop.toFloat()
    val contentLeft = compoundPaddingLeft.toFloat()
    val stripeLeft = (contentLeft - 6 * density).coerceAtLeast(0f)
    val stripeRight =
      (width - compoundPaddingRight + 6 * density).coerceAtMost(width.toFloat())
    val markerColor = (currentTextColor and 0x00FFFFFF) or -0x67000000

    var lineStart = 0
    var index = 0
    var orderedNumber = 0
    var groupTop = -1f
    var groupBottom = 0f

    fun flushCodeGroup() {
      if (groupTop >= 0f) {
        decorationPaint.color = 0x14808080
        canvas.drawRoundRect(
          stripeLeft,
          groupTop - 2 * density,
          stripeRight,
          groupBottom + 2 * density,
          6 * density,
          6 * density,
          decorationPaint,
        )
        groupTop = -1f
      }
    }

    while (true) {
      val newline = text.indexOf('\n', lineStart)
      val lineEnd = if (newline == -1) text.length else newline
      val block = lineBlocks.getOrElse(index) { 0 }
      val type = EditorBlocks.type(block)
      orderedNumber = if (type == EditorBlocks.ORDERED) orderedNumber + 1 else 0

      val layoutLine = textLayout.getLineForOffset(lineStart)
      val top = textLayout.getLineTop(layoutLine) + offsetY
      val bottom =
        textLayout.getLineBottom(textLayout.getLineForOffset(lineEnd)) + offsetY

      if (type == EditorBlocks.CODE) {
        if (groupTop < 0f) {
          groupTop = top
        }
        groupBottom = bottom
      } else {
        flushCodeGroup()
      }

      if (lineEnd == lineStart && block != 0) {
        when (type) {
          EditorBlocks.QUOTE -> {
            decorationPaint.color = markerColor
            val barLeft = contentLeft + 4 * density
            canvas.drawRect(barLeft, top, barLeft + 3 * density, bottom, decorationPaint)
          }
          EditorBlocks.BULLET, EditorBlocks.ORDERED -> {
            val marker = if (type == EditorBlocks.BULLET) "•" else "$orderedNumber."
            decorationTextPaint.textSize = textSize
            decorationTextPaint.typeface = typeface
            decorationTextPaint.color = markerColor
            val markerWidth = decorationTextPaint.measureText(marker)
            canvas.drawText(
              marker,
              contentLeft + (24 - 6) * density - markerWidth,
              textLayout.getLineBaseline(layoutLine) + offsetY,
              decorationTextPaint,
            )
          }
          else -> Unit
        }
      }

      if (newline == -1) {
        break
      }
      lineStart = newline + 1
      index++
    }
    flushCodeGroup()
  }

  fun setCursorColorInt(value: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      if (value != 0) {
        textCursorDrawable?.setTint(value)
      } else {
        textCursorDrawable?.setTintList(null)
      }
    }
  }

  fun setSelectionColorInt(value: Int) {
    highlightColor = if (value != 0) value else defaultHighlightColor
  }

  fun setAutoFocus(value: Boolean) {
    pendingAutoFocus = value
    if (value && isAttachedToWindow) {
      focusAndShowKeyboard()
    }
  }

  fun setEditorSelection(start: Int, end: Int) {
    val length = text.length
    val clampedStart = start.coerceIn(0, length)
    val clampedEnd = end.coerceIn(clampedStart, length)
    setSelection(clampedStart, clampedEnd)
  }

  fun focusAndShowKeyboard() {
    requestFocus()
    val manager =
      context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    manager?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
  }

  fun blurAndHideKeyboard() {
    clearFocus()
    val manager =
      context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    manager?.hideSoftInputFromWindow(windowToken, 0)
  }

  fun resetForRecycle() {
    // The next tenant's props arrive after this; every prop-backed field
    // must return to its default or state leaks between list items. The
    // clear runs watcher-silent: emitting change events for a view being
    // recycled would reach the OLD component's JS handlers.
    stateWrapper = null
    suppressWatcher = true
    setText("")
    suppressWatcher = false
    stylesJson = ""
    defaultValueApplied = false
    pendingAutoFocus = false
    lastPublishedHeight = 0f
    maxHeightPx = 0
    contentExceedsMax = false
    scrollAllowed = true
    isVerticalScrollBarEnabled = false
    pendingMarks = 0
    pendingArmedAt = -1
    lastStateKey = -1L
    lineBlocks.clear()
    ensureLineBlocks()
    mentionActive = false
    lastMentionQuery = null
    mentionTriggers = emptyList()
    placeholderText = null
    hint = null
    placeholderColor = DEFAULT_PLACEHOLDER_COLOR
    linkColor = DEFAULT_LINK_COLOR
    isEnabled = true
    highlightColor = defaultHighlightColor
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      textCursorDrawable?.setTintList(null)
    }
    val inputDirty = codeContextActive || !multiline || !autoCorrectEnabled ||
      capitalizeMode != "sentences"
    codeContextActive = false
    multiline = true
    autoCorrectEnabled = true
    capitalizeMode = "sentences"
    if (inputDirty) {
      applyInputType()
    }
    applyTextStyles()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (pendingAutoFocus) {
      pendingAutoFocus = false
      post { focusAndShowKeyboard() }
    }
  }

  override fun onSelectionChanged(selStart: Int, selEnd: Int) {
    super.onSelectionChanged(selStart, selEnd)
    if (!constructed) {
      return
    }
    // Sticky typing state: inherit the marks of the character before the
    // caret. Skipped mid-edit — afterTextChanged has not applied the pending
    // marks to the inserted text yet, so reading here would clear them.
    // Marks end at the paragraph break: a newline never carries them
    // forward.
    if (!editInProgress && selStart == selEnd && text != null &&
      selStart != pendingArmedAt
    ) {
      pendingArmedAt = -1
      pendingMarks = if (selStart > 0 && text[selStart - 1] == '\n') {
        0
      } else {
        marksAt(text, selStart - 1)
      }
      updateInputTypeForContext()
    }
    if (!editInProgress) {
      updateMentionSession()
    }
    emitState()
    emitEvent("topEditorChangeSelection") {
      putInt("start", selStart)
      putInt("end", selEnd)
    }
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    publishHeight()
  }

  fun setMaxContentHeight(dp: Double) {
    val px = (dp * density).toInt()
    if (px != maxHeightPx) {
      maxHeightPx = px
      publishHeight()
    }
  }

  override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
    // While capped, drags scroll the editor's own content — the parent
    // scroll view must not steal them.
    if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN &&
      contentExceedsMax && scrollAllowed
    ) {
      parent?.requestDisallowInterceptTouchEvent(true)
    }
    return super.onTouchEvent(event)
  }

  fun setScrollAllowed(value: Boolean) {
    if (scrollAllowed != value) {
      scrollAllowed = value
      isVerticalScrollBarEnabled = contentExceedsMax && scrollAllowed
    }
  }

  private fun publishHeight() {
    val wrapper = stateWrapper ?: return
    val currentWidth = width
    if (currentWidth <= 0) {
      return
    }
    measure(
      MeasureSpec.makeMeasureSpec(currentWidth, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
    )
    var heightPx = measuredHeight
    contentExceedsMax = maxHeightPx in 1 until heightPx
    if (contentExceedsMax) {
      heightPx = maxHeightPx
    }
    isVerticalScrollBarEnabled = contentExceedsMax && scrollAllowed
    val heightDp = heightPx / density
    if (kotlin.math.abs(heightDp - lastPublishedHeight) < 0.5f) {
      return
    }
    lastPublishedHeight = heightDp
    wrapper.updateState(Arguments.createMap().apply { putDouble("height", heightDp.toDouble()) })
  }

  private companion object {
    const val DEFAULT_PLACEHOLDER_COLOR = 0x4D000000
    const val DEFAULT_LINK_COLOR = -0xbd5a0b // #4285F5-ish; styles override
  }

  private inline fun emitEvent(name: String, crossinline builder: WritableMap.() -> Unit) {
    val reactContext = context as? ReactContext ?: return
    val dispatcher = UIManagerHelper.getEventDispatcherForReactTag(reactContext, id) ?: return
    val surfaceId = UIManagerHelper.getSurfaceId(reactContext)
    dispatcher.dispatchEvent(object : Event<Nothing>(surfaceId, id) {
      override fun getEventName(): String = name

      override fun getEventData(): WritableMap = Arguments.createMap().apply(builder)
    })
  }
}
