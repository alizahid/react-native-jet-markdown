package com.jetmarkdown.editor

import com.facebook.react.bridge.ReadableArray
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.StateWrapper
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.JetMarkdownEditorManagerDelegate
import com.facebook.react.viewmanagers.JetMarkdownEditorManagerInterface
import com.jetmarkdown.JetMarkdownNative
import com.jetmarkdown.style.PlatformColorResolver

@ReactModule(name = JetMarkdownEditorManager.NAME)
class JetMarkdownEditorManager : SimpleViewManager<JetMarkdownEditorView>(),
  JetMarkdownEditorManagerInterface<JetMarkdownEditorView> {
  private val delegate: ViewManagerDelegate<JetMarkdownEditorView> =
    JetMarkdownEditorManagerDelegate(this)

  init {
    JetMarkdownNative.ensureInstalled()
  }

  override fun getDelegate(): ViewManagerDelegate<JetMarkdownEditorView> = delegate

  override fun getName(): String = NAME

  public override fun createViewInstance(context: ThemedReactContext): JetMarkdownEditorView {
    JetMarkdownNative.ensureInstalled()
    PlatformColorResolver.install(context)
    return JetMarkdownEditorView(context)
  }

  @ReactProp(name = "allowFontScaling")
  override fun setAllowFontScaling(view: JetMarkdownEditorView?, value: Boolean) {
    view?.allowFontScaling = value
  }

  @ReactProp(name = "autoCapitalize")
  override fun setAutoCapitalize(view: JetMarkdownEditorView?, value: String?) {
    view?.setCapitalizeMode(value)
  }

  @ReactProp(name = "autoCorrect")
  override fun setAutoCorrect(view: JetMarkdownEditorView?, value: Boolean) {
    view?.setAutoCorrectEnabled(value)
  }

  @ReactProp(name = "autoFocus")
  override fun setAutoFocus(view: JetMarkdownEditorView?, value: Boolean) {
    view?.setAutoFocus(value)
  }

  @ReactProp(name = "cursorColor", customType = "Color")
  override fun setCursorColor(view: JetMarkdownEditorView?, value: Int?) {
    view?.setCursorColorInt(value ?: 0)
  }

  @ReactProp(name = "defaultValue")
  override fun setDefaultValue(view: JetMarkdownEditorView?, value: String?) {
    view?.setDefaultValue(value)
  }

  @ReactProp(name = "editable")
  override fun setEditable(view: JetMarkdownEditorView?, value: Boolean) {
    view?.isEnabled = value
  }

  @ReactProp(name = "maxHeight")
  override fun setMaxHeight(view: JetMarkdownEditorView?, value: Double) {
    view?.setMaxContentHeight(value)
  }

  @ReactProp(name = "mentionTriggers")
  override fun setMentionTriggers(view: JetMarkdownEditorView?, value: ReadableArray?) {
    val triggers = ArrayList<String>()
    if (value != null) {
      for (index in 0 until value.size()) {
        val trigger = value.getString(index)
        if (!trigger.isNullOrEmpty()) {
          triggers.add(trigger.substring(0, 1))
        }
      }
    }
    view?.mentionTriggers = triggers
  }

  @ReactProp(name = "multiline")
  override fun setMultiline(view: JetMarkdownEditorView?, value: Boolean) {
    view?.setMultiline(value)
  }

  @ReactProp(name = "placeholder")
  override fun setPlaceholder(view: JetMarkdownEditorView?, value: String?) {
    view?.setPlaceholderText(value)
  }

  @ReactProp(name = "placeholderTextColor", customType = "Color")
  override fun setPlaceholderTextColor(view: JetMarkdownEditorView?, value: Int?) {
    view?.setPlaceholderColor(value ?: 0)
  }

  @ReactProp(name = "scrollEnabled")
  override fun setScrollEnabled(view: JetMarkdownEditorView?, value: Boolean) {
    view?.setScrollAllowed(value)
  }

  @ReactProp(name = "selectionColor", customType = "Color")
  override fun setSelectionColor(view: JetMarkdownEditorView?, value: Int?) {
    view?.setSelectionColorInt(value ?: 0)
  }

  @ReactProp(name = "stylesJson")
  override fun setStylesJson(view: JetMarkdownEditorView?, value: String?) {
    view?.setStylesJson(value)
  }

  override fun blur(view: JetMarkdownEditorView?) {
    view?.blurAndHideKeyboard()
  }

  override fun focus(view: JetMarkdownEditorView?) {
    view?.focusAndShowKeyboard()
  }

  override fun setSelection(view: JetMarkdownEditorView?, start: Int, end: Int) {
    view?.setEditorSelection(start, end)
  }

  override fun setValue(view: JetMarkdownEditorView?, value: String?) {
    view?.setMarkdownValue(value ?: "")
  }

  override fun insertLink(view: JetMarkdownEditorView?, url: String?, label: String?) {
    view?.insertLink(url ?: "", label ?: "")
  }

  override fun insertMarkdown(view: JetMarkdownEditorView?, value: String?) {
    view?.insertMarkdownAt(value ?: "")
  }

  override fun insertMention(
    view: JetMarkdownEditorView?,
    trigger: String?,
    label: String?,
    url: String?,
  ) {
    view?.insertMention(trigger ?: "", label ?: "", url ?: "")
  }

  override fun removeLink(view: JetMarkdownEditorView?) {
    view?.removeLink()
  }

  override fun toggleBlockQuote(view: JetMarkdownEditorView?) {
    view?.toggleBlock(EditorBlocks.QUOTE, 0)
  }

  override fun toggleBold(view: JetMarkdownEditorView?) {
    view?.toggleMark(EditorMarks.BOLD)
  }

  override fun toggleCodeBlock(view: JetMarkdownEditorView?) {
    view?.toggleBlock(EditorBlocks.CODE, 0)
  }

  override fun toggleHeading(view: JetMarkdownEditorView?, level: Int) {
    view?.toggleBlock(EditorBlocks.HEADING, level.coerceIn(1, 6))
  }

  override fun toggleOrderedList(view: JetMarkdownEditorView?) {
    view?.toggleBlock(EditorBlocks.ORDERED, 0)
  }

  override fun toggleUnorderedList(view: JetMarkdownEditorView?) {
    view?.toggleBlock(EditorBlocks.BULLET, 0)
  }

  override fun toggleCode(view: JetMarkdownEditorView?) {
    view?.toggleMark(EditorMarks.INLINE_CODE)
  }

  override fun toggleItalic(view: JetMarkdownEditorView?) {
    view?.toggleMark(EditorMarks.ITALIC)
  }

  override fun toggleSpoiler(view: JetMarkdownEditorView?) {
    view?.toggleMark(EditorMarks.SPOILER)
  }

  override fun toggleStrikethrough(view: JetMarkdownEditorView?) {
    view?.toggleMark(EditorMarks.STRIKETHROUGH)
  }

  override fun toggleSubscript(view: JetMarkdownEditorView?) {
    view?.toggleMark(EditorMarks.SUBSCRIPT)
  }

  override fun toggleSuperscript(view: JetMarkdownEditorView?) {
    view?.toggleMark(EditorMarks.SUPERSCRIPT)
  }

  override fun prepareToRecycleView(
    reactContext: ThemedReactContext,
    view: JetMarkdownEditorView,
  ): JetMarkdownEditorView {
    view.resetForRecycle()
    return view
  }

  override fun updateState(
    view: JetMarkdownEditorView,
    props: com.facebook.react.uimanager.ReactStylesDiffMap,
    stateWrapper: StateWrapper?,
  ): Any? {
    view.stateWrapper = stateWrapper
    return null
  }

  companion object {
    const val NAME = "JetMarkdownEditor"
  }
}
