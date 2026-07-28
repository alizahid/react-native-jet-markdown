package com.jetmarkdown.render

import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import com.jetmarkdown.parser.MdNode
import com.jetmarkdown.parser.MdNodeType
import com.jetmarkdown.render.spans.ChipSpan
import com.jetmarkdown.render.spans.LinkSpan
import com.jetmarkdown.render.spans.MarkdownLineHeightSpan
import com.jetmarkdown.render.spans.RunSpan
import com.jetmarkdown.render.spans.SpoilerSpan
import com.jetmarkdown.style.Fonts
import com.jetmarkdown.style.LayoutStyleSpec
import com.jetmarkdown.style.PlatformColorResolver
import com.jetmarkdown.style.StyleConfig
import com.jetmarkdown.style.TextStyleSpec
import kotlin.math.ceil
import org.json.JSONObject

/**
 * AST -> renderable block tree. Inline runs carry one RunSpan each with the
 * fully-resolved attributes (mirrors the iOS attribute-stack renderer).
 */
object SpannableRenderer {

  // Unstyled output is fully plain: no backgrounds, borders, or paddings
  // unless the styles prop (e.g. defaultStyles on the JS side) provides
  // them.
  private val PLAIN_LAYOUT = LayoutStyleSpec(
    backgroundColor = null,
    paddingLeft = 0f, paddingRight = 0f, paddingTop = 0f, paddingBottom = 0f,
    borderRadius = 0f,
    borderLeftColor = null, borderLeftWidth = 0f,
    borderRightColor = null, borderRightWidth = 0f,
    borderTopColor = null, borderTopWidth = 0f,
    borderBottomColor = null, borderBottomWidth = 0f,
  )

  /** Fully-resolved text attributes at one point of the inline walk. */
  private data class ResolvedAttrs(
    val fontSizePx: Float,
    val lineHeightPx: Int = 0, // 0 = natural
    val weight: Int = 400,
    val italic: Boolean = false,
    val family: String? = null,
    val color: Int = Color.BLACK,
    val variants: List<String>? = null,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val baselineShiftPx: Int = 0,
    val backgroundColor: Int? = null,
    // Chip geometry for drawn run backgrounds (inlineCode/link/mention).
    // A link/mention node attaches ONE ChipSpan over its whole range so a
    // mixed-styling link doesn't fragment into seamed per-run chips.
    val suppressRunChip: Boolean = false,
    val chipRadiusPx: Float = 0f,
    val chipPadLeftPx: Float = 0f,
    val chipPadRightPx: Float = 0f,
    val linkUrl: String? = null,
    val spoilerId: Int? = null,
  )

  private class Context(
    val styles: StyleConfig,
    val density: Float,
    val fontScale: Float,
  ) {
    private var spoilerCounter = 0

    fun nextSpoilerId(): Int = spoilerCounter++

    fun apply(attrs: ResolvedAttrs, spec: TextStyleSpec?): ResolvedAttrs {
      if (spec == null) {
        return attrs
      }
      var next = attrs
      spec.fontSize?.let { next = next.copy(fontSizePx = it * density * fontScale) }
      spec.lineHeight?.let { next = next.copy(lineHeightPx = (it * density * fontScale).toInt()) }
      spec.fontWeight?.let { next = next.copy(weight = it) }
      spec.fontFamily?.let { next = next.copy(family = it) }
      spec.color?.let { next = next.copy(color = it) }
      spec.fontVariant?.let { next = next.copy(variants = it) }
      spec.textDecorationLine?.let {
        next = next.copy(
          underline = it.contains("underline"),
          strikethrough = it.contains("line-through"),
        )
      }
      spec.backgroundColor?.let { next = next.copy(backgroundColor = it) }
      return next
    }

    fun apply(attrs: ResolvedAttrs, key: String): ResolvedAttrs =
      apply(attrs, styles.textStyleFor(key))

    fun layoutStyle(key: String, defaults: LayoutStyleSpec): LayoutStyleSpec {
      val spec = LayoutStyleSpec.from(styles.rawSection(key), defaults)
      return spec.scaled(density)
    }
  }

  fun render(root: MdNode, styles: StyleConfig, density: Float, fontScale: Float): List<Block> {
    val context = Context(styles, density, fontScale)
    return renderBlocks(root.children, context, inherited = null)
  }

  // Coalesces stray inline children (tight list items) into paragraphs and
  // renders each block child.
  private fun renderBlocks(
    children: List<MdNode>,
    context: Context,
    inherited: TextStyleSpec?,
  ): List<Block> {
    val out = ArrayList<Block>()
    var inlineRun = ArrayList<MdNode>()

    fun flushInline() {
      if (inlineRun.isNotEmpty()) {
        val synthetic = MdNode(MdNodeType.PARAGRAPH, "", "", 0, false, 1, inlineRun)
        out.add(textBlock(synthetic, context, inherited))
        inlineRun = ArrayList()
      }
    }

    for (child in children) {
      if (isInline(child.type)) {
        inlineRun.add(child)
      } else {
        flushInline()
        renderBlock(child, context, inherited, out)
      }
    }
    flushInline()
    return out
  }

  private fun isInline(type: MdNodeType): Boolean = when (type) {
    MdNodeType.TEXT, MdNodeType.SOFT_BREAK, MdNodeType.HARD_BREAK, MdNodeType.BOLD,
    MdNodeType.ITALIC, MdNodeType.STRIKETHROUGH, MdNodeType.LINK, MdNodeType.INLINE_CODE,
    MdNodeType.SPOILER, MdNodeType.SUPERSCRIPT, MdNodeType.SUBSCRIPT, MdNodeType.IMAGE,
    -> true
    else -> false
  }

  private fun renderBlock(
    node: MdNode,
    context: Context,
    inherited: TextStyleSpec?,
    out: MutableList<Block>,
  ) {
    when (node.type) {
      MdNodeType.PARAGRAPH, MdNodeType.HEADING -> {
        val image = singleImageChild(node)
        if (image != null) {
          out.add(imageBlock(image, context))
        } else {
          out.add(textBlock(node, context, inherited))
        }
      }

      MdNodeType.BLOCK_QUOTE -> {
        val layout = context.layoutStyle("blockQuote", PLAIN_LAYOUT)
        val quoteText = merge(inherited, textStyleWithoutBackground(context.styles, "blockQuote"))
        out.add(Block.Quote(renderBlocks(node.children, context, quoteText), layout))
      }

      MdNodeType.CODE_BLOCK -> {
        val layout = context.layoutStyle("codeBlock", PLAIN_LAYOUT)
        // Base cascades into code; the monospace family is semantic and
        // only styles.codeBlock overrides it.
        var attrs = context.apply(
          ResolvedAttrs(fontSizePx = 16f * context.density * context.fontScale),
          "base",
        ).copy(family = "monospace")
        attrs = context.apply(attrs, inherited)
        attrs = context.apply(attrs, textStyleWithoutBackground(context.styles, "codeBlock"))
        val text = SpannableStringBuilder()
        appendRun(text, node.text.trimEnd('\n'), attrs)
        out.add(Block.Code(text, basePaint(attrs), layout))
      }

      MdNodeType.LIST -> out.add(listBlock(node, context, inherited))

      MdNodeType.TABLE -> out.add(tableBlock(node, context, inherited))

      MdNodeType.THEMATIC_BREAK -> {
        val section = context.styles.rawSection("divider")
        // Neutral functional floor — a divider is content, so it stays
        // visible even unstyled; defaultStyles provides the subtle hairline.
        val color = section?.let { TextStyleSpec.from(it)?.color } ?: Color.BLACK
        val height = section?.optDpOr("height", 1f) ?: 1f
        out.add(Block.Divider(color, height * context.density))
      }

      else -> {
        if (node.children.isNotEmpty()) {
          out.addAll(renderBlocks(node.children, context, inherited))
        } else if (node.text.isNotEmpty()) {
          val synthetic = MdNode(
            MdNodeType.PARAGRAPH, "", "", 0, false, 1,
            listOf(MdNode(MdNodeType.TEXT, node.text, "", 0, false, 1, emptyList())),
          )
          out.add(textBlock(synthetic, context, inherited))
        }
      }
    }
  }

  // A paragraph whose only meaningful child is one image renders as an
  // image block (markdown images are inline; block display matches usage).
  private fun singleImageChild(node: MdNode): MdNode? {
    if (node.type != MdNodeType.PARAGRAPH) {
      return null
    }
    var image: MdNode? = null
    for (child in node.children) {
      when (child.type) {
        MdNodeType.IMAGE -> {
          if (image != null) {
            return null
          }
          image = child
        }
        MdNodeType.TEXT -> if (child.text.isNotBlank()) return null
        MdNodeType.SOFT_BREAK, MdNodeType.HARD_BREAK -> Unit
        else -> return null
      }
    }
    return image
  }

  private fun imageBlock(node: MdNode, context: Context): Block.Image {
    val section = context.styles.rawSection("image")
    val density = context.density
    return Block.Image(
      url = node.url,
      backgroundColor = section?.let { TextStyleSpec.from(it)?.backgroundColor },
      borderRadiusPx = (section?.optDpOr("borderRadius", 0f) ?: 0f) * density,
      heightPx = (section?.optDpOr("height", 0f) ?: 0f) * density,
      maxHeightPx = (section?.optDpOr("maxHeight", 0f) ?: 0f) * density,
      placeholderPx = 200f * density,
    )
  }

  private fun listBlock(node: MdNode, context: Context, inherited: TextStyleSpec?): Block.ListBlock {
    val styles = context.styles
    val listSection = styles.rawSection("list")
    val markerSection = styles.rawSection("listMarker")

    val marginLeft = (listSection?.optDpOr("marginLeft", 0f) ?: 0f) * context.density
    val markerMarginLeft = (markerSection?.optDpOr("marginLeft", 0f) ?: 0f) * context.density
    val markerColor = markerSection?.let { TextStyleSpec.from(it)?.color }

    val itemText = merge(inherited, styles.textStyleFor("listItem"))

    var markerAttrs = ResolvedAttrs(fontSizePx = 16f * context.density * context.fontScale)
    markerAttrs = context.apply(markerAttrs, "base")
    markerAttrs = context.apply(markerAttrs, "paragraph")
    markerAttrs = context.apply(markerAttrs, itemText)
    if (markerColor != null) {
      markerAttrs = markerAttrs.copy(color = markerColor)
    }

    val rows = ArrayList<Block.ListRow>()
    var index = node.startIndex
    var naturalMarkerWidth = 0f
    for (item in node.children) {
      if (item.type != MdNodeType.LIST_ITEM) {
        continue
      }
      val markerText = if (node.ordered) "$index." else "•"
      val marker = SpannableStringBuilder()
      appendRun(marker, markerText, markerAttrs)
      val markerPaint = basePaint(markerAttrs)
      naturalMarkerWidth =
        maxOf(naturalMarkerWidth, ceil(Layout.getDesiredWidth(marker, markerPaint)))
      rows.add(
        Block.ListRow(
          marker = marker,
          markerPaint = markerPaint,
          content = renderBlocks(item.children, context, itemText),
        )
      )
      index++
    }
    // Unstyled marker column is content-driven (widest marker); defaultStyles
    // provides the classic fixed width.
    val styledWidth = markerSection?.optDpOr("width", -1f) ?: -1f
    val markerWidth = if (styledWidth >= 0f) styledWidth * context.density else naturalMarkerWidth
    return Block.ListBlock(rows, marginLeft, markerWidth, markerMarginLeft)
  }

  private fun tableBlock(node: MdNode, context: Context, inherited: TextStyleSpec?): Block.Table {
    val styles = context.styles
    val density = context.density
    val tableSection = styles.rawSection("table")
    val cellSection = styles.rawSection("tableCell")

    val headerCellSection = styles.rawSection("tableHeaderCell")

    val cellText = merge(inherited, styles.textStyleFor("tableCell"))
    var cellAttrs = ResolvedAttrs(fontSizePx = 16f * density * context.fontScale)
    cellAttrs = context.apply(cellAttrs, "base")
    cellAttrs = context.apply(cellAttrs, "paragraph")
    cellAttrs = context.apply(cellAttrs, cellText)

    val cellPadding = cellSection.optPadding(density, defaultAll = 0f)
    // Header cells fall back to the body cell padding key-by-key.
    val headerCellPadding = optPaddingWithFallback(headerCellSection, cellSection, density)

    // Header/body rows layer on top of the shared tableRow base.
    val rowBase = LayoutStyleSpec.from(styles.rawSection("tableRow"), PLAIN_LAYOUT)
    val headerRowStyle =
      LayoutStyleSpec.from(styles.rawSection("tableHeaderRow"), rowBase).scaled(density)
    val bodyRowStyle =
      LayoutStyleSpec.from(styles.rawSection("tableBodyRow"), rowBase).scaled(density)

    val rows = ArrayList<Block.TableRowData>()
    for (rowNode in node.children) {
      if (rowNode.type != MdNodeType.TABLE_ROW) {
        continue
      }
      val isHeader = rowNode.level == 1
      val cells = rowNode.children
        .filter { it.type == MdNodeType.TABLE_CELL }
        .map { cell ->
          val builder = SpannableStringBuilder()
          val attrs = if (isHeader) {
            context.apply(
              context.apply(cellAttrs.copy(weight = 700), "tableCell"),
              "tableHeaderCell",
            )
          } else {
            cellAttrs
          }
          walk(builder, cell, attrs, context)
          builder
        }
      rows.add(Block.TableRowData(isHeader, cells))
    }

    return Block.Table(
      rows = rows,
      cellPaint = basePaint(cellAttrs),
      style = context.layoutStyle("table", LayoutStyleSpec.EMPTY),
      headerRowStyle = headerRowStyle,
      bodyRowStyle = bodyRowStyle,
      cellPaddingLeftPx = cellPadding[0],
      cellPaddingRightPx = cellPadding[1],
      cellPaddingTopPx = cellPadding[2],
      cellPaddingBottomPx = cellPadding[3],
      headerCellPaddingLeftPx = headerCellPadding[0],
      headerCellPaddingRightPx = headerCellPadding[1],
      headerCellPaddingTopPx = headerCellPadding[2],
      headerCellPaddingBottomPx = headerCellPadding[3],
      // Unstyled columns take their natural widths; defaultStyles provides
      // the classic [44, 320] clamps.
      minColumnWidthPx = (tableSection?.optDpOr("minColumnWidth", 0f) ?: 0f) * density,
      maxColumnWidthPx = (tableSection?.optDpOr("maxColumnWidth", 0f) ?: 0f) * density,
    )
  }

  /** Like optPadding but each key falls back to a second section. */
  private fun optPaddingWithFallback(
    primary: JSONObject?,
    fallback: JSONObject?,
    density: Float,
  ): FloatArray {
    fun side(key: String): Float {
      val fromPrimary = primary?.optDpOr(key, Float.NaN) ?: Float.NaN
      if (!fromPrimary.isNaN()) {
        return fromPrimary * density
      }
      return (fallback?.optDpOr(key, 0f) ?: 0f) * density
    }
    return floatArrayOf(
      side("paddingLeft"),
      side("paddingRight"),
      side("paddingTop"),
      side("paddingBottom"),
    )
  }

  /** [left, right, top, bottom] px with shorthand expansion done JS-side. */
  private fun JSONObject?.optPadding(density: Float, defaultAll: Float): FloatArray {
    return floatArrayOf(
      (this?.optDpOr("paddingLeft", defaultAll) ?: defaultAll) * density,
      (this?.optDpOr("paddingRight", defaultAll) ?: defaultAll) * density,
      (this?.optDpOr("paddingTop", defaultAll) ?: defaultAll) * density,
      (this?.optDpOr("paddingBottom", defaultAll) ?: defaultAll) * density,
    )
  }

  private fun merge(base: TextStyleSpec?, over: TextStyleSpec?): TextStyleSpec? {
    if (base == null) {
      return over
    }
    if (over == null) {
      return base
    }
    return TextStyleSpec(
      fontSize = over.fontSize ?: base.fontSize,
      fontWeight = over.fontWeight ?: base.fontWeight,
      fontFamily = over.fontFamily ?: base.fontFamily,
      lineHeight = over.lineHeight ?: base.lineHeight,
      color = over.color ?: base.color,
      fontVariant = over.fontVariant ?: base.fontVariant,
      textDecorationColor = over.textDecorationColor ?: base.textDecorationColor,
      textDecorationLine = over.textDecorationLine ?: base.textDecorationLine,
      textDecorationStyle = over.textDecorationStyle ?: base.textDecorationStyle,
      backgroundColor = over.backgroundColor ?: base.backgroundColor,
    )
  }

  private fun textBlock(node: MdNode, context: Context, inherited: TextStyleSpec?): Block.Text {
    val builder = SpannableStringBuilder()
    val attrs = baseAttrs(node, context, inherited)
    walk(builder, node, attrs, context)
    val spoilerSection = context.styles.rawSection("spoiler")
    return Block.Text(
      builder,
      basePaint(attrs),
      // Neutral functional floor — the cover must hide text even unstyled;
      // defaultStyles provides the styled cover.
      spoilerColor = spoilerSection?.let { com.jetmarkdown.style.TextStyleSpec.from(it)?.backgroundColor }
        ?: Color.BLACK,
      spoilerRadiusPx = (spoilerSection?.optDpOr("borderRadius", 0f) ?: 0f) * context.density,
    )
  }

  // codeBlock/blockQuote merge text + layout keys in one section; their
  // backgroundColor is the box fill, not an inline-run background, so it
  // must not reach the text-attribute path.
  private fun textStyleWithoutBackground(styles: StyleConfig, key: String): TextStyleSpec? {
    val section = styles.rawSection(key) ?: return null
    if (!section.has("backgroundColor")) {
      return TextStyleSpec.from(section)
    }
    val copy = JSONObject(section.toString())
    copy.remove("backgroundColor")
    return TextStyleSpec.from(copy)
  }

  private fun basePaint(attrs: ResolvedAttrs): TextPaint = TextPaint().apply {
    isAntiAlias = true
    textSize = attrs.fontSizePx
  }

  private fun baseAttrs(node: MdNode, context: Context, inherited: TextStyleSpec?): ResolvedAttrs {
    val scale = context.density * context.fontScale
    var attrs: ResolvedAttrs
    // Unstyled output is fully plain: heading sizes/weights come from the
    // hN sections (defaultStyles on the JS side), not builtins.
    if (node.type == MdNodeType.HEADING) {
      attrs = ResolvedAttrs(fontSizePx = 16f * scale)
      attrs = context.apply(attrs, "base")
      attrs = context.apply(attrs, inherited)
      // Headings shed the inherited lineHeight like they shed paragraph
      // styles: a body lineHeight would cap the taller heading's line box
      // and clip its ascenders. hN.lineHeight still applies.
      attrs = attrs.copy(lineHeightPx = 0)
      attrs = context.apply(attrs, "h${node.level}")
    } else {
      attrs = ResolvedAttrs(fontSizePx = 16f * scale)
      attrs = context.apply(attrs, "base")
      attrs = context.apply(attrs, "paragraph")
      attrs = context.apply(attrs, inherited)
    }
    return attrs
  }

  private fun walk(
    builder: SpannableStringBuilder,
    parent: MdNode,
    attrs: ResolvedAttrs,
    context: Context,
  ) {
    for (node in parent.children) {
      when (node.type) {
        MdNodeType.TEXT -> appendRun(builder, node.text, attrs)
        MdNodeType.SOFT_BREAK -> appendRun(builder, " ", attrs)
        MdNodeType.HARD_BREAK -> appendRun(builder, "\n", attrs)
        MdNodeType.BOLD ->
          walk(builder, node, context.apply(attrs.copy(weight = 700), "bold"), context)
        MdNodeType.ITALIC ->
          walk(builder, node, context.apply(attrs.copy(italic = true), "italic"), context)
        MdNodeType.STRIKETHROUGH ->
          walk(
            builder,
            node,
            context.apply(attrs.copy(strikethrough = true), "strikethrough"),
            context,
          )
        MdNodeType.LINK -> {
          var next = attrs.copy(linkUrl = node.url)
          val variant = context.styles.mentionVariants.firstOrNull {
            it.pattern.matcher(node.url).find()
          }
          val sectionKey = if (variant != null) "mention" else "link"
          next = if (variant != null) {
            context.apply(context.apply(next, "mention"), variant.style)
          } else {
            context.apply(next, "link")
          }
          val section = context.styles.rawSection(sectionKey)
          if (section != null) {
            next = next.copy(
              chipRadiusPx =
                (section.optDpOr("borderRadius", 0f)) * context.density,
            )
          }
          val nodeChipColor = next.backgroundColor
          if (nodeChipColor != null) {
            val chipStart = builder.length
            walk(builder, node, next.copy(suppressRunChip = true), context)
            if (builder.length > chipStart) {
              builder.setSpan(
                ChipSpan(
                  color = nodeChipColor,
                  radiusPx = next.chipRadiusPx,
                  padLeftPx = next.chipPadLeftPx,
                  padRightPx = next.chipPadRightPx,
                  typeface = buildTypeface(next),
                  textSizePx = next.fontSizePx,
                ),
                chipStart,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
              )
            }
          } else {
            walk(builder, node, next, context)
          }
        }
        MdNodeType.INLINE_CODE -> {
          var next = attrs.copy(family = "monospace")
          next = context.apply(next, "inlineCode")
          val section = context.styles.rawSection("inlineCode")
          if (section != null) {
            next = next.copy(
              chipRadiusPx =
                (section.optDpOr("borderRadius", 0f)) * context.density,
              chipPadLeftPx =
                (section.optDpOr("paddingLeft", 0f)) * context.density,
              chipPadRightPx =
                (section.optDpOr("paddingRight", 0f)) * context.density,
            )
          }
          appendRun(builder, node.text, next)
        }
        MdNodeType.SUPERSCRIPT -> {
          var next = attrs.copy(
            fontSizePx = attrs.fontSizePx * 0.7f,
            baselineShiftPx = (-attrs.fontSizePx * 0.35f).toInt(),
          )
          next = context.apply(next, "superscript")
          walk(builder, node, next, context)
        }
        MdNodeType.SUBSCRIPT -> {
          var next = attrs.copy(
            fontSizePx = attrs.fontSizePx * 0.7f,
            baselineShiftPx = (attrs.fontSizePx * 0.18f).toInt(),
          )
          next = context.apply(next, "subscript")
          walk(builder, node, next, context)
        }
        MdNodeType.SPOILER ->
          walk(builder, node, attrs.copy(spoilerId = context.nextSpoilerId()), context)
        MdNodeType.IMAGE -> appendRun(builder, node.text, attrs)
        else -> walk(builder, node, attrs, context)
      }
    }
  }

  private fun appendRun(builder: SpannableStringBuilder, text: String, attrs: ResolvedAttrs) {
    if (text.isEmpty()) {
      return
    }
    val start = builder.length
    builder.append(text)
    val typeface = buildTypeface(attrs)
    builder.setSpan(
      RunSpan(
        typeface = typeface,
        textSizePx = attrs.fontSizePx,
        color = attrs.color,
        baselineShiftPx = attrs.baselineShiftPx,
        fontFeatureSettings = attrs.variants?.let(::featureSettings),
        underline = attrs.underline,
        strikethrough = attrs.strikethrough,
      ),
      start,
      builder.length,
      Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
    if (attrs.backgroundColor != null && !attrs.suppressRunChip) {
      builder.setSpan(
        ChipSpan(
          color = attrs.backgroundColor,
          radiusPx = attrs.chipRadiusPx,
          padLeftPx = attrs.chipPadLeftPx,
          padRightPx = attrs.chipPadRightPx,
          typeface = typeface,
          textSizePx = attrs.fontSizePx,
        ),
        start,
        builder.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
      )
    }
    if (attrs.lineHeightPx > 0) {
      builder.setSpan(
        MarkdownLineHeightSpan(attrs.lineHeightPx),
        start,
        builder.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
      )
    }
    if (attrs.linkUrl != null) {
      builder.setSpan(LinkSpan(attrs.linkUrl), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    if (attrs.spoilerId != null) {
      builder.setSpan(
        SpoilerSpan(attrs.spoilerId, typeface, attrs.fontSizePx),
        start,
        builder.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
      )
    }
  }

  private fun buildTypeface(attrs: ResolvedAttrs): Typeface =
    Fonts.resolve(PlatformColorResolver.current(), attrs.family, attrs.weight, attrs.italic)

  private fun featureSettings(variants: List<String>): String? {
    val features = variants.mapNotNull {
      when (it) {
        "tabular-nums" -> "'tnum'"
        "proportional-nums" -> "'pnum'"
        "oldstyle-nums" -> "'onum'"
        "lining-nums" -> "'lnum'"
        "small-caps" -> "'smcp'"
        else -> null
      }
    }
    return if (features.isEmpty()) null else features.joinToString(", ")
  }

  private fun JSONObject.optDpOr(key: String, fallback: Float): Float {
    val value = optDouble(key)
    return if (value.isNaN()) fallback else value.toFloat()
  }
}
