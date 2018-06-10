package org.janelia.saalfeldlab.fx.ortho;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.RowConstraints;

public class GridConstraintsManager
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final double DEFAULT_COLUMN_WIDTH1 = 50;

	private static final double DEFAULT_ROW_HEIGHT1 = 50;

	public enum MaximizedRow
	{
		TOP( 0 ),
		BOTTOM( 1 ),
		NONE( -1 );

		private final int index;

		private MaximizedRow( final int index )
		{
			this.index = index;
		}

		public int asIndex()
		{
			return this.index;
		}
	}

	public enum MaximizedColumn
	{
		LEFT( 0 ),
		RIGHT( 1 ),
		NONE( -1 );

		private final int index;

		private MaximizedColumn( final int index )
		{
			this.index = index;
		}

		public int asIndex()
		{
			return this.index;
		}
	}

	private double columnWidth1;

	private double rowHeight1;

	private boolean isFullScreen = false;

	private MaximizedColumn maximizedColumn = MaximizedColumn.NONE;

	private MaximizedRow maximizedRow = MaximizedRow.NONE;

	private final DoubleProperty firstRowHeight = new SimpleDoubleProperty();

	private final DoubleProperty firstColumnWidth = new SimpleDoubleProperty();

	public GridConstraintsManager()
	{
		resetToDefault();
		storeCurrent();
	}

	private synchronized final void resetToDefault()
	{
		firstColumnWidth.set( DEFAULT_COLUMN_WIDTH1 );
		firstRowHeight.set( DEFAULT_ROW_HEIGHT1 );

		isFullScreen = false;
		maximizedColumn = MaximizedColumn.NONE;
		maximizedRow = MaximizedRow.NONE;
	}

	public void resetToLast()
	{
		firstColumnWidth.set( columnWidth1 );
		firstRowHeight.set( rowHeight1 );

		isFullScreen = false;
		maximizedRow = MaximizedRow.NONE;
		maximizedColumn = MaximizedColumn.NONE;
	}

	private synchronized void storeCurrent()
	{
		this.columnWidth1 = firstColumnWidth.get();
		this.rowHeight1 = firstRowHeight.get();
	}

	public synchronized void maximize( final int r, final int c, final int steps )
	{
		if ( isFullScreen )
		{
			resetToLast();
			return;
		}

		storeCurrent();
		final double columnStep = ( c == 0 ? 100 - firstColumnWidth.get() : firstColumnWidth.get() - 0 ) / steps;
		final double rowStep = ( r == 0 ? 100 - firstRowHeight.get() : firstRowHeight.get() - 0 ) / steps;

		for ( int i = 0; i < steps; ++i )
		{
			firstColumnWidth.set( firstColumnWidth.get() + columnStep );
			firstRowHeight.set( firstRowHeight.get() + rowStep );
		}

		firstColumnWidth.set( c == 0 ? 100 : 0 );
		firstRowHeight.set( r == 0 ? 100 : 0 );

		isFullScreen = true;
		maximizedRow = r == 0 ? MaximizedRow.TOP : MaximizedRow.BOTTOM;
		maximizedColumn = c == 0 ? MaximizedColumn.LEFT : MaximizedColumn.RIGHT;

	}

	public synchronized void maximize( final int row, final int steps )
	{
		if ( isFullScreen )
		{
			resetToLast();
			return;
		}

		storeCurrent();
		final double rowStep = ( row == 0 ? 100 - firstRowHeight.get() : firstRowHeight.get() - 0 ) / steps;

		for ( int i = 0; i < steps; ++i )
		{
			firstRowHeight.set( firstRowHeight.get() + rowStep );
		}

		firstRowHeight.set( row == 0 ? 100 : 0 );

		isFullScreen = true;
		maximizedRow = row == 0 ? MaximizedRow.TOP : MaximizedRow.BOTTOM;
		maximizedColumn = MaximizedColumn.NONE;
	}

	public void manageGrid( final GridPane grid )
	{

//		grid.getColumnConstraints().clear();
//		grid.getColumnConstraints().add( this.column1 );
//		grid.getColumnConstraints().add( this.column2 );
//
//		grid.getRowConstraints().clear();
//		grid.getRowConstraints().add( this.row1 );
//		grid.getRowConstraints().add( this.row2 );
		attachToGrid( grid );

	}

	private void attachToGrid( final GridPane grid )
	{

		final ColumnConstraints column1 = new ColumnConstraints();
		final ColumnConstraints column2 = new ColumnConstraints();
		column1.percentWidthProperty().bind( this.firstColumnWidth );
		column2.percentWidthProperty().bind( this.firstColumnWidth.subtract( 100.0 ).multiply( -1.0 ) );
		grid.getColumnConstraints().setAll( column1, column2 );

		final RowConstraints row1 = new RowConstraints();
		final RowConstraints row2 = new RowConstraints();
		row1.percentHeightProperty().bind( this.firstRowHeight );
		row2.percentHeightProperty().bind( this.firstRowHeight.subtract( 100 ).multiply( -1.0 ) );
		grid.getRowConstraints().setAll( row1, row2 );

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

	public MaximizedColumn getMaximizedColumn()
	{
		return this.maximizedColumn;
	}

	public MaximizedRow getMaximizedRow()
	{
		return this.maximizedRow;
	}

}
