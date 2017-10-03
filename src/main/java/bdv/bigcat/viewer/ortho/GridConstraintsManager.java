package bdv.bigcat.viewer.ortho;

import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.RowConstraints;

public class GridConstraintsManager
{

	private final double defaultColumnWidth1 = 50;

	private final double defaultColumnWidth2 = 50;

	private final double defaultRowHeight1 = 50;

	private final double defaultRowHeight2 = 50;

	final ColumnConstraints column1 = new ColumnConstraints();

	final ColumnConstraints column2 = new ColumnConstraints();

	final RowConstraints row1 = new RowConstraints();

	final RowConstraints row2 = new RowConstraints();

	private double columnWidth1;

	private double columnWidth2;

	private double rowHeight1;

	private double rowHeight2;

	private boolean isFullScreen = false;

	public GridConstraintsManager()
	{
		resetToDefault();
		storeCurrent();
	}

	private synchronized final void resetToDefault()
	{
		column1.setPercentWidth( defaultColumnWidth1 );
		column2.setPercentWidth( defaultColumnWidth2 );
		row1.setPercentHeight( defaultRowHeight1 );
		row2.setPercentHeight( defaultRowHeight2 );

		isFullScreen = false;
	}

	synchronized final void resetToLast()
	{
		column1.setPercentWidth( columnWidth1 );
		column2.setPercentWidth( columnWidth2 );
		row1.setPercentHeight( rowHeight1 );
		row2.setPercentHeight( rowHeight2 );

		isFullScreen = false;
	}

	private synchronized void storeCurrent()
	{
		this.columnWidth1 = column1.getPercentWidth();
		this.columnWidth2 = column2.getPercentWidth();
		this.rowHeight1 = row1.getPercentHeight();
		this.rowHeight2 = row2.getPercentHeight();
	}

	public synchronized void maximize( final int r, final int c, final int steps )
	{
		storeCurrent();
		final ColumnConstraints increaseColumn = c == 0 ? column1 : column2;
		final ColumnConstraints decreaseColumn = c == 0 ? column2 : column1;
		final RowConstraints increaseRow = r == 0 ? row1 : row2;
		final RowConstraints decreaseRow = r == 0 ? row2 : row1;
		final double increaseColumnStep = ( 100 - increaseColumn.getPercentWidth() ) / steps;
		final double decreaseColumnStep = ( decreaseColumn.getPercentWidth() - 0 ) / steps;
		final double increaseRowStep = ( 100 - increaseRow.getPercentHeight() ) / steps;
		final double decreaseRowStep = ( decreaseRow.getPercentHeight() - 0 ) / steps;

		for ( int i = 0; i < steps; ++i )
		{
			increaseColumn.setPercentWidth( increaseColumn.getPercentWidth() + increaseColumnStep );
			decreaseColumn.setPercentWidth( decreaseColumn.getPercentWidth() - decreaseColumnStep );
			increaseRow.setPercentHeight( increaseRow.getPercentHeight() + increaseRowStep );
			decreaseRow.setPercentHeight( decreaseRow.getPercentHeight() - decreaseRowStep );
		}

		increaseColumn.setPercentWidth( 100 );
		decreaseColumn.setPercentWidth( 0 );
		increaseRow.setPercentHeight( 100 );
		decreaseRow.setPercentHeight( 0 );

		isFullScreen = true;

	}

	public void manageGrid( final GridPane grid )
	{

		grid.getColumnConstraints().clear();
		grid.getColumnConstraints().add( this.column1 );
		grid.getColumnConstraints().add( this.column2 );

		grid.getRowConstraints().clear();
		grid.getRowConstraints().add( this.row1 );
		grid.getRowConstraints().add( this.row2 );

	}

	public synchronized boolean isFullScreen()
	{
		return isFullScreen;
	}

}
