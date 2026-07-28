package com.jetmarkdown

import com.jetmarkdown.style.PlatformColorResolver
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.StateWrapper
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.JetMarkdownViewManagerDelegate
import com.facebook.react.viewmanagers.JetMarkdownViewManagerInterface

@ReactModule(name = JetMarkdownViewManager.NAME)
class JetMarkdownViewManager : SimpleViewManager<JetMarkdownView>(),
  JetMarkdownViewManagerInterface<JetMarkdownView> {
  private val delegate: ViewManagerDelegate<JetMarkdownView> =
    JetMarkdownViewManagerDelegate(this)

  init {
    JetMarkdownNative.ensureInstalled()
  }

  override fun getDelegate(): ViewManagerDelegate<JetMarkdownView> = delegate

  override fun getName(): String = NAME

  public override fun createViewInstance(context: ThemedReactContext): JetMarkdownView {
    JetMarkdownNative.ensureInstalled()
    PlatformColorResolver.install(context)
    return JetMarkdownView(context)
  }

  @ReactProp(name = "allowFontScaling")
  override fun setAllowFontScaling(view: JetMarkdownView?, value: Boolean) {
    view?.allowFontScaling = value
  }

  @ReactProp(name = "markdown")
  override fun setMarkdown(view: JetMarkdownView?, value: String?) {
    view?.setMarkdown(value)
  }

  @ReactProp(name = "stylesJson")
  override fun setStylesJson(view: JetMarkdownView?, value: String?) {
    view?.setStylesJson(value)
  }

  @ReactProp(name = "images")
  override fun setImages(view: JetMarkdownView?, value: ReadableArray?) {
    view?.setImages(value)
  }

  override fun prepareToRecycleView(
    reactContext: ThemedReactContext,
    view: JetMarkdownView,
  ): JetMarkdownView {
    view.resetForRecycle()
    return view
  }

  override fun updateState(
    view: JetMarkdownView,
    props: com.facebook.react.uimanager.ReactStylesDiffMap,
    stateWrapper: StateWrapper?,
  ): Any? {
    view.stateWrapper = stateWrapper
    return null
  }

  companion object {
    const val NAME = "JetMarkdownView"
  }
}
