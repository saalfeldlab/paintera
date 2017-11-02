package bdv.bigcat.viewer.ortho;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.util.Duration;

public class GridResizer
{

	private final GridConstraintsManager manager;

	private final double tolerance;

	private final GridPane grid;

	private boolean mouseWithinResizableRangeX = false;

	private boolean mouseWithinResizableRangeY = false;

	private double x;

	private double y;

	private double dragX;

	private double dragY;

	private double dragStartX;

	private double dragStartY;

	private boolean dragging = false;

	public GridResizer( final GridConstraintsManager manager, final double tolerance, final GridPane grid )
	{
		super();
		this.manager = manager;
		this.tolerance = tolerance;
		this.grid = grid;
//		this.grid.setOnMouseMoved( new MouseChanged() );
//		this.grid.setOnMousePressed( new MousePressed() );
//		this.grid.setOnMouseDragged( new MouseDragged() );
//		this.grid.setOnMouseClicked( new MouseDoubleClicked() );
	}

	public EventHandler< MouseEvent > onMouseMovedHandler()
	{
		return new MouseChanged();
	}

	public EventHandler< MouseEvent > onMousePresedHandler()
	{
		return new MousePressed();
	}

	public EventHandler< MouseEvent > onMouseDraggedHandler()
	{
		return new MouseDragged();
	}

	public EventHandler< MouseEvent > onMouseDoubleClickedHandler()
	{
		return new MouseDoubleClicked();
	}

	public EventHandler< MouseEvent > onMouseReleased()
	{
		return new MouseReleased();
	}

	private class MouseChanged implements EventHandler< MouseEvent >
	{

		@Override
		public void handle( final MouseEvent event )
		{
			synchronized ( manager )
			{
				synchronized ( grid )
				{
					final double x = event.getX();
					final double y = event.getY();
					final double gridBorderX = manager.column1.getPercentWidth() / 100 * grid.widthProperty().get();
					final double gridBorderY = manager.row1.getPercentHeight() / 100 * grid.heightProperty().get();
//					System.out.println( "REGISTERING EVENT AT " + x + " " + y + " " + proportionX + " " + proportionY + " " + tolerance );
					final boolean mouseWithinResizableRangeX = Math.abs( x - gridBorderX ) < tolerance;
					final boolean mouseWithinResizableRangeY = Math.abs( y - gridBorderY ) < tolerance;

					final Scene scene = grid.sceneProperty().get();

					if ( mouseWithinResizableRangeX && mouseWithinResizableRangeY )
						scene.setCursor( Cursor.OPEN_HAND );// Cursor.NW_RESIZE
															// );
					else if ( mouseWithinResizableRangeX )
						scene.setCursor( Cursor.OPEN_HAND );// Cursor.H_RESIZE
															// );
					else if ( mouseWithinResizableRangeY )
						scene.setCursor( Cursor.OPEN_HAND );// Cursor.V_RESIZE
															// );
					else
						scene.setCursor( Cursor.DEFAULT );

				}
			}
		}
	}

	private class MousePressed implements EventHandler< MouseEvent >
	{

		@Override
		public void handle( final MouseEvent event )
		{
			final double x = event.getX();
			final double y = event.getY();
			final double gridBorderX = manager.column1.getPercentWidth() / 100 * grid.widthProperty().get();
			final double gridBorderY = manager.row1.getPercentHeight() / 100 * grid.heightProperty().get();
//					System.out.println( "REGISTERING EVENT AT " + x + " " + y + " " + proportionX + " " + proportionY + " " + tolerance );
			mouseWithinResizableRangeX = Math.abs( x - gridBorderX ) < tolerance;
			mouseWithinResizableRangeY = Math.abs( y - gridBorderY ) < tolerance;
			dragging = mouseWithinResizableRangeX || mouseWithinResizableRangeY;
			if ( dragging )
			{
//				System.out.println( "INITIATE DRAG!" );
				grid.sceneProperty().get().setCursor( Cursor.CLOSED_HAND );
				dragStartX = x;
				dragStartY = y;
				event.consume();
			}

		}

	}

	private class MouseReleased implements EventHandler< MouseEvent >
	{

		@Override
		public void handle( final MouseEvent event )
		{
			dragging = false;
			grid.sceneProperty().get().setCursor( Cursor.DEFAULT );
		}

	}

	private class MouseDragged implements EventHandler< MouseEvent >
	{

		@Override
		public void handle( final MouseEvent event )
		{
//			System.out.println( "DRAAAAG YOOO! " + mouseWithinResizableRangeX + " " + mouseWithinResizableRangeY );
			if ( dragging )
			{
				final double width = grid.widthProperty().get();
				final double height = grid.heightProperty().get();
				final double stopX = event.getX();
				final double stopY = event.getY();

				if ( mouseWithinResizableRangeX )
				{
					final double percentWidth = Math.min( Math.max( stopX * 100.0 / width, 20 ), 80 );
//					System.out.println( "PERCENTAGE WIDTH! " + percentWidth + " " + width + " " + stopX );
					manager.column1.setPercentWidth( percentWidth );
					manager.column2.setPercentWidth( 100 - percentWidth );
				}

				if ( mouseWithinResizableRangeY )
				{
					final double percentHeight = Math.min( Math.max( stopY * 100.0 / height, 20 ), 80 );
					manager.row1.setPercentHeight( percentHeight );
					manager.row2.setPercentHeight( 100 - percentHeight );
				}

				event.consume();
			}

		}

	}

	private class MouseDoubleClicked implements EventHandler< MouseEvent >
	{

		@Override
		public void handle( final MouseEvent event )
		{
			if ( event.getClickCount() == 2 )
			{
				final double x = event.getX();
				final double y = event.getY();
				final double gridBorderX = manager.column1.getPercentWidth() / 100 * grid.widthProperty().get();
				final double gridBorderY = manager.row1.getPercentHeight() / 100 * grid.heightProperty().get();
//					System.out.println( "REGISTERING EVENT AT " + x + " " + y + " " + proportionX + " " + proportionY + " " + tolerance );
				final boolean mouseWithinResizableRangeX = Math.abs( x - gridBorderX ) < tolerance;
				final boolean mouseWithinResizableRangeY = Math.abs( y - gridBorderY ) < tolerance;

				if ( mouseWithinResizableRangeX || mouseWithinResizableRangeY )
				{
					final int time = 300;
					event.consume();
					final Timeline timeline = new Timeline();

					if ( mouseWithinResizableRangeX && mouseWithinResizableRangeY )
						timeline.getKeyFrames().addAll(
								new KeyFrame( Duration.ZERO,
										new KeyValue( manager.column1.percentWidthProperty(), manager.column1.getPercentWidth() ),
										new KeyValue( manager.column2.percentWidthProperty(), manager.column2.getPercentWidth() ),
										new KeyValue( manager.row1.percentHeightProperty(), manager.row1.getPercentHeight() ),
										new KeyValue( manager.row2.percentHeightProperty(), manager.row2.getPercentHeight() ) ),
								new KeyFrame( new Duration( time ),
										new KeyValue( manager.column1.percentWidthProperty(), 50 ),
										new KeyValue( manager.column2.percentWidthProperty(), 50 ),
										new KeyValue( manager.row1.percentHeightProperty(), 50 ),
										new KeyValue( manager.row2.percentHeightProperty(), 50 ) ) );
					else if ( mouseWithinResizableRangeX )
						timeline.getKeyFrames().addAll(
								new KeyFrame( Duration.ZERO,
										new KeyValue( manager.column1.percentWidthProperty(), manager.column1.getPercentWidth() ),
										new KeyValue( manager.column2.percentWidthProperty(), manager.column2.getPercentWidth() ) ),
								new KeyFrame( new Duration( time ),
										new KeyValue( manager.column1.percentWidthProperty(), 50 ),
										new KeyValue( manager.column2.percentWidthProperty(), 50 ) ) );
					else if ( mouseWithinResizableRangeY )
						timeline.getKeyFrames().addAll(
								new KeyFrame( Duration.ZERO,
										new KeyValue( manager.row1.percentHeightProperty(), manager.row1.getPercentHeight() ),
										new KeyValue( manager.row2.percentHeightProperty(), manager.row2.getPercentHeight() ) ),
								new KeyFrame( new Duration( time ),
										new KeyValue( manager.row1.percentHeightProperty(), 50 ),
										new KeyValue( manager.row2.percentHeightProperty(), 50 ) ) );
					timeline.play();
				}
			}
		}

	}

}
