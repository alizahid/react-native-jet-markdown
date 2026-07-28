package com.jetmarkdown.render

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.jetmarkdown.style.LayoutStyleSpec

/** One renderable block; blocks nest (quote children, list row content). */
sealed class Block {
  class Text(
    val text: CharSequence,
    val paint: TextPaint,
    val spoilerColor: Int = 0xFF3F3F46.toInt(),
    val spoilerRadiusPx: Float = 0f,
  ) : Block()

  /** Code renders unwrapped inside a horizontal scroller. */
  class Code(val text: CharSequence, val paint: TextPaint, val style: LayoutStyleSpec) : Block()

  class Quote(val children: List<Block>, val style: LayoutStyleSpec) : Block()

  class ListRow(val marker: CharSequence, val markerPaint: TextPaint, val content: List<Block>)

  class ListBlock(
    val rows: List<ListRow>,
    val marginLeftPx: Float,
    val markerWidthPx: Float,
    val markerMarginLeftPx: Float,
  ) : Block()

  class Divider(val color: Int, val thicknessPx: Float) : Block()

  class Image(
    val url: String,
    val backgroundColor: Int?,
    val borderRadiusPx: Float,
    val heightPx: Float,
    val maxHeightPx: Float,
    val placeholderPx: Float,
  ) : Block()

  class TableRowData(val isHeader: Boolean, val cells: List<CharSequence>)

  class Table(
    val rows: List<TableRowData>,
    val cellPaint: TextPaint,
    val style: LayoutStyleSpec,
    val headerRowStyle: LayoutStyleSpec,
    val bodyRowStyle: LayoutStyleSpec,
    val cellPaddingLeftPx: Float,
    val cellPaddingRightPx: Float,
    val cellPaddingTopPx: Float,
    val cellPaddingBottomPx: Float,
    val headerCellPaddingLeftPx: Float,
    val headerCellPaddingRightPx: Float,
    val headerCellPaddingTopPx: Float,
    val headerCellPaddingBottomPx: Float,
    val minColumnWidthPx: Float,
    val maxColumnWidthPx: Float,
  ) : Block() {
    fun rowStyle(isHeader: Boolean): LayoutStyleSpec =
      if (isHeader) headerRowStyle else bodyRowStyle

    fun padLeft(isHeader: Boolean): Float =
      if (isHeader) headerCellPaddingLeftPx else cellPaddingLeftPx

    fun padRight(isHeader: Boolean): Float =
      if (isHeader) headerCellPaddingRightPx else cellPaddingRightPx

    fun padTop(isHeader: Boolean): Float =
      if (isHeader) headerCellPaddingTopPx else cellPaddingTopPx

    fun padBottom(isHeader: Boolean): Float =
      if (isHeader) headerCellPaddingBottomPx else cellPaddingBottomPx
  }
}

/** Layout results for one block at one width. */
class MeasuredBlock(
  val block: Block,
  val heightPx: Float,
  val textLayout: StaticLayout?,
  /** Code/Table: unwrapped content width for the scroller. */
  val contentWidthPx: Float,
  val children: List<MeasuredBlock>,
  /** List rows: marker layouts parallel to children groups. */
  val markerLayouts: List<StaticLayout>,
  /** List rows: measured content per row. */
  val rowContents: List<List<MeasuredBlock>>,
  /** Tables: resolved column widths. */
  val columnWidths: FloatArray = FloatArray(0),
  /** Tables: per-row heights. */
  val rowHeights: FloatArray = FloatArray(0),
  /** Tables: cell layouts, row-major. */
  val cellLayouts: List<List<StaticLayout>> = emptyList(),
)

/**
 * Parsed + rendered markdown, shared between the Fabric measurer (layout
 * thread) and the mounted view (main thread). Per-width results are cached.
 */
class RenderedContent(
  val blocks: List<Block>,
  private val gapPx: Float,
  private val topPaddingPx: Float,
  private val bottomPaddingPx: Float,
  private val density: Float,
) {
  class WidthLayout(val measured: List<MeasuredBlock>, val totalHeightPx: Float)

  private data class LayoutKey(val widthPx: Int, val sizesHash: Int)

  private val layoutCache = HashMap<LayoutKey, WidthLayout>()

  /** imageSizes: url -> intrinsic dp size, merged prop + discovered. */
  @Synchronized
  fun layoutFor(widthPx: Int, imageSizes: Map<String, FloatArray> = emptyMap()): WidthLayout {
    val key = LayoutKey(widthPx, imageSizesHash(imageSizes))
    layoutCache[key]?.let { return it }

    val measured = blocks.map { measure(it, widthPx.toFloat(), imageSizes) }
    var height = topPaddingPx + bottomPaddingPx
    measured.forEachIndexed { index, block ->
      height += block.heightPx
      if (index < measured.size - 1) {
        height += gapPx
      }
    }

    val result = WidthLayout(measured, height)
    if (layoutCache.size > 4) {
      layoutCache.clear()
    }
    layoutCache[key] = result
    return result
  }

  private fun imageSizesHash(sizes: Map<String, FloatArray>): Int {
    var hash = sizes.size
    for ((url, size) in sizes) {
      hash = hash * 31 + url.hashCode()
      hash = hash * 31 + size[0].toInt()
      hash = hash * 31 + size[1].toInt()
    }
    return hash
  }

  fun stackHeight(children: List<MeasuredBlock>): Float {
    var height = 0f
    children.forEachIndexed { index, child ->
      height += child.heightPx
      if (index < children.size - 1) {
        height += gapPx
      }
    }
    return height
  }

  val gap: Float get() = gapPx

  private fun measure(
    block: Block,
    widthPx: Float,
    imageSizes: Map<String, FloatArray>,
  ): MeasuredBlock {
    return when (block) {
      is Block.Text -> {
        val layout = staticLayout(block.text, block.paint, widthPx.toInt(), wrap = true)
        MeasuredBlock(block, layout.height.toFloat(), layout, widthPx, emptyList(), emptyList(), emptyList())
      }

      is Block.Code -> {
        val desired = Layout.getDesiredWidth(block.text, block.paint)
        val layout = staticLayout(block.text, block.paint, kotlin.math.ceil(desired.toDouble()).toInt(), wrap = false)
        val height = layout.height + block.style.paddingTop + block.style.paddingBottom
        MeasuredBlock(block, height, layout, desired, emptyList(), emptyList(), emptyList())
      }

      is Block.Quote -> {
        val innerWidth = widthPx - block.style.paddingLeft - block.style.paddingRight -
          block.style.borderLeftWidth - block.style.borderRightWidth
        val children = block.children.map { measure(it, innerWidth.coerceAtLeast(1f), imageSizes) }
        val height = stackHeight(children) + block.style.paddingTop + block.style.paddingBottom +
          block.style.borderTopWidth + block.style.borderBottomWidth
        MeasuredBlock(block, height, null, widthPx, children, emptyList(), emptyList())
      }

      is Block.ListBlock -> {
        val contentX = block.marginLeftPx + block.markerMarginLeftPx + block.markerWidthPx
        val contentWidth = (widthPx - contentX).coerceAtLeast(1f)
        val markerLayouts = ArrayList<StaticLayout>(block.rows.size)
        val rowContents = ArrayList<List<MeasuredBlock>>(block.rows.size)
        var height = 0f
        block.rows.forEachIndexed { index, row ->
          val marker = staticLayout(row.marker, row.markerPaint, block.markerWidthPx.toInt().coerceAtLeast(1), wrap = true)
          val content = row.content.map { measure(it, contentWidth, imageSizes) }
          markerLayouts.add(marker)
          rowContents.add(content)
          height += maxOf(marker.height.toFloat(), stackHeight(content))
          if (index < block.rows.size - 1) {
            height += gapPx / 2f
          }
        }
        MeasuredBlock(block, height, null, contentWidth, emptyList(), markerLayouts, rowContents)
      }

      is Block.Divider ->
        MeasuredBlock(block, block.thicknessPx, null, widthPx, emptyList(), emptyList(), emptyList())

      is Block.Table -> measureTable(block, widthPx)

      is Block.Image -> {
        val known = imageSizes[block.url]
        var displayH: Float
        var displayW: Float
        if (known != null && known[0] > 0 && known[1] > 0) {
          val intrinsicW = known[0] * density
          val intrinsicH = known[1] * density
          val scale = (widthPx / intrinsicW).coerceAtMost(1f)
          displayH = intrinsicH * scale
          if (block.heightPx > 0) {
            displayH = block.heightPx
          }
          if (block.maxHeightPx > 0) {
            displayH = displayH.coerceAtMost(block.maxHeightPx)
          }
          displayW = (intrinsicW * displayH / intrinsicH).coerceAtMost(widthPx)
        } else {
          // Full-width placeholder until the intrinsic size is known.
          displayH = if (block.heightPx > 0) block.heightPx else block.placeholderPx
          if (block.maxHeightPx > 0) {
            displayH = displayH.coerceAtMost(block.maxHeightPx)
          }
          displayW = widthPx
        }
        MeasuredBlock(block, displayH, null, displayW, emptyList(), emptyList(), emptyList())
      }
    }
  }

  // Intelligent column widths: natural (unwrapped) width per column,
  // clamped to [min, max]; surplus distributed proportionally when the
  // table fits, horizontal scroll when it does not.
  private fun measureTable(block: Block.Table, widthPx: Float): MeasuredBlock {
    val columnCount = block.rows.maxOfOrNull { it.cells.size } ?: 0
    if (columnCount == 0) {
      return MeasuredBlock(block, 0f, null, widthPx, emptyList(), emptyList(), emptyList())
    }

    val natural = FloatArray(columnCount)
    for (row in block.rows) {
      val cellPadH = block.padLeft(row.isHeader) + block.padRight(row.isHeader)
      row.cells.forEachIndexed { column, cell ->
        // Ceil so the later int truncation of the cell width can't force
        // a wrap that the natural measurement said would fit.
        val desired =
          kotlin.math.ceil(Layout.getDesiredWidth(cell, block.cellPaint).toDouble()).toFloat() +
            cellPadH + 1f
        if (desired > natural[column]) {
          natural[column] = desired
        }
      }
    }

    val columnWidths = FloatArray(columnCount) {
      natural[it].coerceIn(
        block.minColumnWidthPx,
        if (block.maxColumnWidthPx > 0) block.maxColumnWidthPx else Float.MAX_VALUE,
      )
    }

    val availableWidth = widthPx - block.style.paddingLeft - block.style.paddingRight -
      block.style.borderLeftWidth - block.style.borderRightWidth
    val total = columnWidths.sum()
    if (total < availableWidth) {
      val naturalTotal = natural.sum().coerceAtLeast(1f)
      val surplus = availableWidth - total
      for (i in 0 until columnCount) {
        columnWidths[i] += surplus * (natural[i] / naturalTotal)
      }
    }
    val contentWidth = columnWidths.sum()

    val rowHeights = FloatArray(block.rows.size)
    val cellLayouts = block.rows.mapIndexed { rowIndex, row ->
      val rowStyle = block.rowStyle(row.isHeader)
      val cellPadH = block.padLeft(row.isHeader) + block.padRight(row.isHeader)
      val cellPadV = block.padTop(row.isHeader) + block.padBottom(row.isHeader)
      val rowExtra = rowStyle.borderBottomWidth + rowStyle.borderTopWidth
      val layouts = row.cells.mapIndexed { column, cell ->
        staticLayout(
          cell,
          block.cellPaint,
          (columnWidths[column] - cellPadH).toInt().coerceAtLeast(1),
          wrap = true,
        )
      }
      rowHeights[rowIndex] =
        (layouts.maxOfOrNull { it.height.toFloat() } ?: 0f) + cellPadV + rowExtra
      layouts
    }

    val height = rowHeights.sum() + block.style.paddingTop + block.style.paddingBottom +
      block.style.borderTopWidth + block.style.borderBottomWidth
    return MeasuredBlock(
      block, height, null, contentWidth, emptyList(), emptyList(), emptyList(),
      columnWidths, rowHeights, cellLayouts,
    )
  }

  private fun staticLayout(text: CharSequence, paint: TextPaint, widthPx: Int, wrap: Boolean): StaticLayout {
    return StaticLayout.Builder
      .obtain(text, 0, text.length, paint, widthPx.coerceAtLeast(1))
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setIncludePad(false)
      .build()
  }
}
