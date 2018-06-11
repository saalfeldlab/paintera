package org.janelia.saalfeldlab.fx.ortho;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ObservableList;
import javafx.scene.Node;
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

	private double previousFirstRowHeight;

	private double previousFirstColumnWidth;

	private boolean isFullScreen = false;

	private MaximizedColumn maximizedColumn = MaximizedColumn.NONE;

	private MaximizedRow maximizedRow = MaximizedRow.NONE;

	private final SimpleDoubleProperty firstRowHeight = new SimpleDoubleProperty();

	private final SimpleDoubleProperty firstColumnWidth = new SimpleDoubleProperty();

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
		LOG.debug( "Reset to last {} {}", previousFirstColumnWidth, previousFirstRowHeight );
		firstColumnWidth.set( previousFirstColumnWidth );
		firstRowHeight.set( previousFirstRowHeight );

		isFullScreen = false;
		maximizedRow = MaximizedRow.NONE;
		maximizedColumn = MaximizedColumn.NONE;
	}

	private synchronized void storeCurrent()
	{
		this.previousFirstRowHeight = firstRowHeight.get();
		this.previousFirstColumnWidth = firstColumnWidth.get();
	}

	public synchronized void maximize( final int r, final int c, final int steps )
	{
		LOG.debug( "Maximizing cell ({}, {}). Is already maximized? {}", r, c, isFullScreen );
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

		LOG.debug( "Maximized first column={} first row={}", firstColumnWidth.getValue(), firstRowHeight.getValue() );

		isFullScreen = true;
		maximizedRow = r == 0 ? MaximizedRow.TOP : MaximizedRow.BOTTOM;
		maximizedColumn = c == 0 ? MaximizedColumn.LEFT : MaximizedColumn.RIGHT;

	}

	public synchronized void maximize( final int row, final int steps )
	{
		LOG.debug( "Maximizing row {}. Is already maximized? {}", row, isFullScreen );
		if ( isFullScreen )
		{
			resetToLast();
			return;
		}

		LOG.debug( "Maximizing row {}", row );

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

		column1.percentWidthProperty().addListener( ( obs, oldv, newv ) -> updateChildrenVisibilities( grid ) );
		column2.percentWidthProperty().addListener( ( obs, oldv, newv ) -> updateChildrenVisibilities( grid ) );

		// TODO row visibility overrides columnVisibility
		row1.percentHeightProperty().addListener( ( obs, oldv, newv ) -> updateChildrenVisibilities( grid ) );
		row2.percentHeightProperty().addListener( ( obs, oldv, newv ) -> updateChildrenVisibilities( grid ) );

	}

	private static void updateChildrenVisibilities(
			final GridPane grid )
	{
		final ObservableList< ColumnConstraints > colConstraints = grid.getColumnConstraints();
		final ObservableList< RowConstraints > rowConstraints = grid.getRowConstraints();
		for ( final Node node : grid.getChildren() )
		{
			final int r = GridPane.getRowIndex( node );
			final int c = GridPane.getColumnIndex( node );
			node.setVisible( colConstraints.get( c ).getPercentWidth() > 0 && rowConstraints.get( r ).getPercentHeight() > 0 );
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

	public MaximizedColumn getMaximizedColumn()
	{
		return this.maximizedColumn;
	}

	public MaximizedRow getMaximizedRow()
	{
		return this.maximizedRow;
	}

	public void set( final GridConstraintsManager that )
	{
		if ( this == that ) { return; }
		this.isFullScreen = that.isFullScreen;
		this.maximizedColumn = that.maximizedColumn;
		this.maximizedRow = that.maximizedRow;
		this.firstColumnWidth.set( that.firstColumnWidth.get() );
		this.firstRowHeight.set( that.firstRowHeight.get() );
		this.previousFirstColumnWidth = that.previousFirstColumnWidth;
		this.previousFirstRowHeight = that.previousFirstRowHeight;
	}

	@Override
	public String toString()
	{
		return new StringBuilder( "{" )
				.append( this.getClass().getSimpleName() )
				.append( ": " )
				.append( previousFirstRowHeight )
				.append( ", " )
				.append( previousFirstColumnWidth )
				.append( ", " )
				.append( firstRowHeight.get() )
				.append( ", " )
				.append( firstColumnWidth.get() )
				.append( ", " )
				.append( isFullScreen )
				.append( ", " )
				.append( maximizedRow )
				.append( ", " )
				.append( maximizedColumn )
				.append( "}" )
				.toString();
	}

	private static List< Node > getAllChildrenInColumn( final GridPane grid, final int col )
	{
		final List< Node > relevantNodes = grid
				.getChildren()
				.stream()
				.filter( c -> GridPane.getColumnIndex( c ) != null )
				.filter( c -> GridPane.getColumnIndex( c ) == col )
				.collect( Collectors.toList() );
		LOG.warn( "Getting all nodes in col {}: {}", col, relevantNodes );
		return relevantNodes;
	}

	private static List< Node > getAllChildrenInRow( final GridPane grid, final int row )
	{
		final List< Node > relevantNodes = grid
				.getChildren()
				.stream()
				.filter( c -> GridPane.getRowIndex( c ) != null )
				.filter( c -> GridPane.getRowIndex( c ) == row )
				.collect( Collectors.toList() );
		LOG.warn( "Getting all nodes in row {}: {}", row, relevantNodes );
		return relevantNodes;
	}

}
