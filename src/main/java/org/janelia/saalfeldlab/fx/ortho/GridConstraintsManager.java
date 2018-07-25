package org.janelia.saalfeldlab.fx.ortho;

import java.lang.invoke.MethodHandles;

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.RowConstraints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GridConstraintsManager
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final double DEFAULT_COLUMN_WIDTH1 = 50;

	private static final double DEFAULT_ROW_HEIGHT1 = 50;

	public enum MaximizedRow
	{
		TOP(0),
		BOTTOM(1),
		NONE(-1);

		private final int index;

		private MaximizedRow(final int index)
		{
			this.index = index;
		}

		public int asIndex()
		{
			return this.index;
		}

		public static MaximizedRow fromIndex(final int index)
		{
			switch (index)
			{
				case 0:
					return TOP;
				case 1:
					return BOTTOM;
				case -1:
					return NONE;
				default:
					return null;
			}
		}
	}

	public enum MaximizedColumn
	{
		LEFT(0),
		RIGHT(1),
		NONE(-1);

		private final int index;

		private MaximizedColumn(final int index)
		{
			this.index = index;
		}

		public int asIndex()
		{
			return this.index;
		}

		public static MaximizedColumn fromIndex(final int index)
		{
			switch (index)
			{
				case 0:
					return LEFT;
				case 1:
					return RIGHT;
				case -1:
					return NONE;
				default:
					return null;
			}
		}
	}

	private double previousFirstRowHeight;

	private double previousFirstColumnWidth;

	private boolean isFullScreen = false;

	private final SimpleDoubleProperty firstRowHeight = new SimpleDoubleProperty();

	private final SimpleDoubleProperty firstColumnWidth = new SimpleDoubleProperty();

	private transient final ObservableObjectValue<MaximizedColumn> maximizedColumn = Bindings.createObjectBinding(
			() -> fromFirstColumnWidth(firstColumnWidth.get()),
			firstColumnWidth
	                                                                                                             );

	private transient final ObservableObjectValue<MaximizedRow> maximizedRow = Bindings.createObjectBinding(
			() -> fromFirstRowHeight(firstRowHeight.get()),
			firstRowHeight
	                                                                                                       );

	public GridConstraintsManager()
	{
		resetToDefault();
		storeCurrent();

	}

	private synchronized final void resetToDefault()
	{
		firstColumnWidth.set(DEFAULT_COLUMN_WIDTH1);
		firstRowHeight.set(DEFAULT_ROW_HEIGHT1);

		isFullScreen = false;
	}

	public void resetToLast()
	{
		LOG.debug("Reset to last {} {}", previousFirstColumnWidth, previousFirstRowHeight);
		firstColumnWidth.set(previousFirstColumnWidth);
		firstRowHeight.set(previousFirstRowHeight);

		isFullScreen = false;
	}

	private synchronized void storeCurrent()
	{
		this.previousFirstRowHeight = firstRowHeight.get();
		this.previousFirstColumnWidth = firstColumnWidth.get();
	}

	public synchronized void maximize(final MaximizedRow r, final MaximizedColumn c, final int steps)
	{
		LOG.debug("Maximizing cell ({}, {}). Is already maximized? {}", r, c, isFullScreen);
		if (isFullScreen)
		{
			resetToLast();
			return;
		}

		if (r == null || r.equals(MaximizedRow.NONE) || c == null || c.equals(MaximizedColumn.NONE))
		{
			LOG.debug("Arguments null or NONE: {} {}", r, c);
			return;
		}

		storeCurrent();
		final boolean isLeft     = c.equals(MaximizedColumn.LEFT);
		final boolean isTop      = r.equals(MaximizedRow.TOP);
		final double  columnStep = (isLeft ? 100 - firstColumnWidth.get() : firstColumnWidth.get() - 0) / steps;
		final double  rowStep    = (isTop ? 100 - firstRowHeight.get() : firstRowHeight.get() - 0) / steps;

		for (int i = 0; i < steps; ++i)
		{
			firstColumnWidth.set(firstColumnWidth.get() + columnStep);
			firstRowHeight.set(firstRowHeight.get() + rowStep);
		}

		firstColumnWidth.set(isLeft ? 100 : 0);
		firstRowHeight.set(isTop ? 100 : 0);

		LOG.debug("Maximized first column={} first row={}", firstColumnWidth.getValue(), firstRowHeight.getValue());

		isFullScreen = true;
	}

	public synchronized void maximize(final MaximizedRow row, final int steps)
	{
		LOG.debug("Maximizing row {}. Is already maximized? {}", row, isFullScreen);
		if (isFullScreen)
		{
			resetToLast();
			return;
		}

		if (row == null || row.equals(MaximizedRow.NONE))
		{
			LOG.debug("Argument null or NONE: {}", row);
			return;
		}

		LOG.debug("Maximizing row {}", row);

		storeCurrent();
		final boolean isTop   = row.equals(MaximizedRow.TOP);
		final double  rowStep = (isTop ? 100 - firstRowHeight.get() : firstRowHeight.get() - 0) / steps;

		for (int i = 0; i < steps; ++i)
		{
			firstRowHeight.set(firstRowHeight.get() + rowStep);
		}

		firstRowHeight.set(isTop ? 100 : 0);

		isFullScreen = true;
	}

	public void manageGrid(final GridPane grid)
	{

		//		grid.getColumnConstraints().clear();
		//		grid.getColumnConstraints().add( this.column1 );
		//		grid.getColumnConstraints().add( this.column2 );
		//
		//		grid.getRowConstraints().clear();
		//		grid.getRowConstraints().add( this.row1 );
		//		grid.getRowConstraints().add( this.row2 );
		attachToGrid(grid);

	}

	private void attachToGrid(final GridPane grid)
	{

		final ColumnConstraints column1 = new ColumnConstraints();
		final ColumnConstraints column2 = new ColumnConstraints();
		column1.percentWidthProperty().bind(this.firstColumnWidth);
		column2.percentWidthProperty().bind(this.firstColumnWidth.subtract(100.0).multiply(-1.0));
		grid.getColumnConstraints().setAll(column1, column2);

		final RowConstraints row1 = new RowConstraints();
		final RowConstraints row2 = new RowConstraints();
		row1.percentHeightProperty().bind(this.firstRowHeight);
		row2.percentHeightProperty().bind(this.firstRowHeight.subtract(100).multiply(-1.0));
		grid.getRowConstraints().setAll(row1, row2);

		column1.percentWidthProperty().addListener((obs, oldv, newv) -> updateChildrenVisibilities(grid));
		column2.percentWidthProperty().addListener((obs, oldv, newv) -> updateChildrenVisibilities(grid));

		// TODO row visibility overrides columnVisibility
		row1.percentHeightProperty().addListener((obs, oldv, newv) -> updateChildrenVisibilities(grid));
		row2.percentHeightProperty().addListener((obs, oldv, newv) -> updateChildrenVisibilities(grid));

	}

	private static void updateChildrenVisibilities(
			final GridPane grid)
	{
		final ObservableList<ColumnConstraints> colConstraints = grid.getColumnConstraints();
		final ObservableList<RowConstraints>    rowConstraints = grid.getRowConstraints();
		for (final Node node : grid.getChildren())
		{
			final int r = GridPane.getRowIndex(node);
			final int c = GridPane.getColumnIndex(node);
			node.setVisible(colConstraints.get(c).getPercentWidth() > 0 && rowConstraints.get(r).getPercentHeight() >
					0);
		}
	}

	public DoubleProperty firstRowHeightProperty()
	{
		return this.firstRowHeight;
	}

	public DoubleProperty firstColumnWidthProperty()
	{
		return this.firstColumnWidth;
	}

	public synchronized boolean isFullScreen()
	{
		return isFullScreen;
	}

	public ObservableObjectValue<MaximizedColumn> observeMaximizedColumn()
	{
		return this.maximizedColumn;
	}

	public ObservableObjectValue<MaximizedRow> observeMaximizedRow()
	{
		return this.maximizedRow;
	}

	public MaximizedColumn getMaximizedColumn()
	{
		return this.maximizedColumn.get();
	}

	public MaximizedRow getMaximizedRow()
	{
		return this.maximizedRow.get();
	}

	public void set(final GridConstraintsManager that)
	{
		if (this == that) { return; }
		this.isFullScreen = that.isFullScreen;
		this.firstColumnWidth.set(that.firstColumnWidth.get());
		this.firstRowHeight.set(that.firstRowHeight.get());
		this.previousFirstColumnWidth = that.previousFirstColumnWidth;
		this.previousFirstRowHeight = that.previousFirstRowHeight;
	}

	@Override
	public String toString()
	{
		return new StringBuilder("{")
				.append(this.getClass().getSimpleName())
				.append(": ")
				.append(previousFirstRowHeight)
				.append(", ")
				.append(previousFirstColumnWidth)
				.append(", ")
				.append(firstRowHeight.get())
				.append(", ")
				.append(firstColumnWidth.get())
				.append(", ")
				.append(isFullScreen)
				.append(", ")
				.append(maximizedRow)
				.append(", ")
				.append(maximizedColumn)
				.append("}")
				.toString();
	}

	private static MaximizedColumn fromFirstColumnWidth(final double width)
	{
		return width == 0
		       ? MaximizedColumn.RIGHT
		       : width == 100
		         ? MaximizedColumn.LEFT
		         : MaximizedColumn.NONE;
	}

	private static MaximizedRow fromFirstRowHeight(final double height)
	{
		return height == 0
		       ? MaximizedRow.BOTTOM
		       : height == 100
		         ? MaximizedRow.TOP
		         : MaximizedRow.NONE;
	}

}
