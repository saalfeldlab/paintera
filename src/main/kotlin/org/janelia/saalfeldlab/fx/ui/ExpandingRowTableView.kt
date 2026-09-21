package org.janelia.saalfeldlab.fx.ui

import javafx.scene.control.TableView

/**
 * A [TableView] whose preferred height is the sum of its first [maxRows] rows, measured by [rowPrefHeight] in the same
 * layout pass the rows are sized in, so it grows when a row expands and shows a scrollbar only past [maxRows]
 *
 * @param rowPrefHeight the preferred height of the row showing an item at the given width, or null when that row has
 * no node yet; [defaultRowHeight] is used then
 */
open class ExpandingRowTableView<T>(
	private val rowPrefHeight: (item: T, width: Double) -> Double?,
	private val maxRows: Int = 8,
	private val defaultRowHeight: Double = 34.0,
	private val defaultHeaderHeight: Double = 34.0,
	/* a little more than the rows add up to, so rounding never asks for a scrollbar */
	private val slack: Double = 6.0
) : TableView<T>() {

	init {
		minHeight = 0.0
	}

	override fun computePrefHeight(width: Double): Double {
		val header = lookup(".column-header-background")?.layoutBounds?.height?.takeIf { it > 0 } ?: defaultHeaderHeight
		val rows = items.take(maxRows).sumOf { rowPrefHeight(it, width) ?: defaultRowHeight }
		return header + rows + snappedTopInset() + snappedBottomInset() + slack
	}
}
