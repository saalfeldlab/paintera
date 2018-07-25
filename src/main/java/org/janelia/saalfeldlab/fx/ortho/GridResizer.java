package org.janelia.saalfeldlab.fx.ortho;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import org.janelia.saalfeldlab.fx.event.InstallAndRemove;
import org.janelia.saalfeldlab.fx.event.KeyTracker;

public class GridResizer implements InstallAndRemove<Node>
{
	private final GridConstraintsManager manager;

	private final double tolerance;

	private final Pane grid;

	private boolean mouseWithinResizableRangeX = false;

	private boolean mouseWithinResizableRangeY = false;

	private boolean dragging = false;

	private boolean isOnMargin;

	private final KeyTracker keyTracker;

	public GridResizer(final GridConstraintsManager manager, final double tolerance, final Pane grid, final KeyTracker
			keyTracker)
	{
		super();
		this.manager = manager;
		this.tolerance = tolerance;
		this.grid = grid;
		this.keyTracker = keyTracker;
	}

	public EventHandler<MouseEvent> onMouseMovedHandler()
	{
		return new MouseChanged();
	}

	public EventHandler<MouseEvent> onMousePressedHandler()
	{
		return new MousePressed();
	}

	public EventHandler<MouseEvent> onMouseDraggedHandler()
	{
		return new MouseDragged();
	}

	public EventHandler<MouseEvent> onMouseDoubleClickedHandler()
	{
		return new MouseDoubleClicked();
	}

	public EventHandler<MouseEvent> onMouseReleased()
	{
		return new MouseReleased();
	}

	public boolean isDraggingPanel()
	{
		return dragging;
	}

	private class MouseChanged implements EventHandler<MouseEvent>
	{
		@Override
		public void handle(final MouseEvent event)
		{
			if (!keyTracker.noKeysActive()) { return; }
			synchronized (manager)
			{
				synchronized (grid)
				{
					final double  x                          = event.getX();
					final double  y                          = event.getY();
					final double  gridBorderX                = manager.firstColumnWidthProperty().get() / 100 * grid
							.widthProperty().get();
					final double  gridBorderY                = manager.firstRowHeightProperty().get() / 100 * grid
							.heightProperty().get();
					final boolean mouseWithinResizableRangeX = Math.abs(x - gridBorderX) < tolerance;
					final boolean mouseWithinResizableRangeY = Math.abs(y - gridBorderY) < tolerance;

					final Scene scene = grid.sceneProperty().get();

					if (mouseWithinResizableRangeX && mouseWithinResizableRangeY)
					{
						if (Double.compare(x - gridBorderX, 0.0) < 0 && Double.compare(y - gridBorderY, 0.0) < 0)
						{
							scene.setCursor(Cursor.SE_RESIZE);
						}
						else if (Double.compare(x - gridBorderX, 0.0) > 0 && Double.compare(y - gridBorderY, 0.0) < 0)
						{
							scene.setCursor(Cursor.SW_RESIZE);
						}
						else if (Double.compare(x - gridBorderX, 0.0) < 0 && Double.compare(y - gridBorderY, 0.0) > 0)
						{
							scene.setCursor(Cursor.NE_RESIZE);
						}
						else
						{
							scene.setCursor(Cursor.NW_RESIZE);
						}
						isOnMargin = true;
					}

					else if (mouseWithinResizableRangeX)
					{
						scene.setCursor(Cursor.H_RESIZE);
						isOnMargin = true;
					}
					else if (mouseWithinResizableRangeY)
					{
						scene.setCursor(Cursor.V_RESIZE);
						isOnMargin = true;
					}
					else if (isOnMargin)
					{
						scene.setCursor(Cursor.DEFAULT);
						isOnMargin = false;
					}
				}
			}
		}
	}

	private class MousePressed implements EventHandler<MouseEvent>
	{

		@Override
		public void handle(final MouseEvent event)
		{
			final double x           = event.getX();
			final double y           = event.getY();
			final double gridBorderX = manager.firstColumnWidthProperty().get() / 100 * grid.widthProperty().get();
			final double gridBorderY = manager.firstRowHeightProperty().get() / 100 * grid.heightProperty().get();

			mouseWithinResizableRangeX = Math.abs(x - gridBorderX) < tolerance;
			mouseWithinResizableRangeY = Math.abs(y - gridBorderY) < tolerance;

			dragging = mouseWithinResizableRangeX || mouseWithinResizableRangeY;
			if (dragging)
			{
				event.consume();
			}
		}
	}

	private class MouseReleased implements EventHandler<MouseEvent>
	{

		@Override
		public void handle(final MouseEvent event)
		{
			dragging = false;
			grid.sceneProperty().get().setCursor(Cursor.DEFAULT);
		}

	}

	private class MouseDragged implements EventHandler<MouseEvent>
	{

		@Override
		public void handle(final MouseEvent event)
		{
			if (dragging)
			{
				final double width  = grid.widthProperty().get();
				final double height = grid.heightProperty().get();
				final double stopX  = event.getX();
				final double stopY  = event.getY();

				if (mouseWithinResizableRangeX)
				{
					final double percentWidth = Math.min(Math.max(stopX * 100.0 / width, 20), 80);
					manager.firstColumnWidthProperty().set(percentWidth);
				}

				if (mouseWithinResizableRangeY)
				{
					final double percentHeight = Math.min(Math.max(stopY * 100.0 / height, 20), 80);
					manager.firstRowHeightProperty().set(percentHeight);
				}

				event.consume();
			}

		}

	}

	private class MouseDoubleClicked implements EventHandler<MouseEvent>
	{
		@Override
		public void handle(final MouseEvent event)
		{
			if (event.getClickCount() == 2)
			{
				final double  x                          = event.getX();
				final double  y                          = event.getY();
				final double  gridBorderX                = manager.firstColumnWidthProperty().get() / 100 * grid
						.widthProperty().get();
				final double  gridBorderY                = manager.firstRowHeightProperty().get() / 100 * grid
						.heightProperty().get();
				final boolean mouseWithinResizableRangeX = Math.abs(x - gridBorderX) < tolerance;
				final boolean mouseWithinResizableRangeY = Math.abs(y - gridBorderY) < tolerance;

				if (mouseWithinResizableRangeX || mouseWithinResizableRangeY)
				{
					final int time = 300;
					event.consume();
					final Timeline timeline = new Timeline();

					if (mouseWithinResizableRangeX && mouseWithinResizableRangeY)
					{
						timeline.getKeyFrames().addAll(
								new KeyFrame(
										Duration.ZERO,
										new KeyValue(
												manager.firstColumnWidthProperty(),
												manager.firstColumnWidthProperty().get()
										),
										new KeyValue(
												manager.firstRowHeightProperty(),
												manager.firstRowHeightProperty().get()
										)
								),
								new KeyFrame(
										new Duration(time),
										new KeyValue(manager.firstColumnWidthProperty(), 50),
										new KeyValue(manager.firstRowHeightProperty(), 50)
								)
						                              );
					}
					else if (mouseWithinResizableRangeX)
					{
						timeline.getKeyFrames().addAll(
								new KeyFrame(
										Duration.ZERO,
										new KeyValue(
												manager.firstColumnWidthProperty(),
												manager.firstColumnWidthProperty().get()
										)
								),
								new KeyFrame(
										new Duration(time),
										new KeyValue(manager.firstColumnWidthProperty(), 50)
								)
						                              );
					}
					else if (mouseWithinResizableRangeY)
					{
						timeline.getKeyFrames().addAll(
								new KeyFrame(
										Duration.ZERO,
										new KeyValue(
												manager.firstRowHeightProperty(),
												manager.firstRowHeightProperty().get()
										)
								),
								new KeyFrame(
										new Duration(time),
										new KeyValue(manager.firstRowHeightProperty(), 50)
								)
						                              );
					}
					timeline.play();
				}
			}
		}
	}

	@Override
	public void installInto(final Node node)
	{
		node.addEventFilter(MouseEvent.MOUSE_MOVED, onMouseMovedHandler());
		node.addEventFilter(MouseEvent.MOUSE_CLICKED, onMouseDoubleClickedHandler());
		node.addEventFilter(MouseEvent.MOUSE_DRAGGED, onMouseDraggedHandler());
		node.addEventFilter(MouseEvent.MOUSE_PRESSED, onMousePressedHandler());
		node.addEventFilter(MouseEvent.MOUSE_RELEASED, onMouseReleased());
	}

	@Override
	public void removeFrom(final Node node)
	{
		node.removeEventFilter(MouseEvent.MOUSE_MOVED, onMouseMovedHandler());
		node.removeEventFilter(MouseEvent.MOUSE_CLICKED, onMouseDoubleClickedHandler());
		node.removeEventFilter(MouseEvent.MOUSE_DRAGGED, onMouseDraggedHandler());
		node.removeEventFilter(MouseEvent.MOUSE_PRESSED, onMousePressedHandler());
		node.removeEventFilter(MouseEvent.MOUSE_RELEASED, onMouseReleased());
	}
}
