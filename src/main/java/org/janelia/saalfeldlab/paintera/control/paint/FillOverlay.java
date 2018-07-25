package org.janelia.saalfeldlab.paintera.control.paint;

import java.lang.invoke.MethodHandles;

import bdv.fx.viewer.OverlayRendererGeneric;
import bdv.fx.viewer.ViewerPanelFX;
import javafx.scene.Cursor;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FillOverlay implements OverlayRendererGeneric<GraphicsContext>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final ViewerPanelFX viewer;

	private double x, y;

	protected boolean visible = false;

	protected boolean wasVisible = false;

	public FillOverlay(final ViewerPanelFX viewer)
	{
		this.viewer = viewer;
		this.viewer.getDisplay().addOverlayRenderer(this);
		this.viewer.addEventFilter(MouseEvent.MOUSE_MOVED, this::setPosition);
		this.viewer.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::setPosition);

	}

	public void setVisible(final boolean visible)
	{
		if (visible != this.visible)
		{
			if (this.visible)
			{
				this.wasVisible = true;
			}
			this.visible = visible;
			this.viewer.getDisplay().drawOverlays();
		}
	}

	public void setPosition(final MouseEvent event)
	{
		setPosition(event.getX(), event.getY());
	}

	public void setPosition(final double x, final double y)
	{
		this.x = x;
		this.y = y;
		this.viewer.getDisplay().drawOverlays();
	}

	@Override
	public void drawOverlays(final GraphicsContext g)
	{

		if (visible && this.viewer.isMouseInside())
		{

			{
				this.viewer.getScene().setCursor(Cursor.CROSSHAIR);
				g.setFill(Color.WHITE);
				g.setFont(Font.font(g.getFont().getFamily(), 15.0));
				g.fillText("Fill 3D", x + 5, y - 5);
				//				this.viewer.getScene().setCursor( Cursor.NONE );
				return;
			}
		}
		if (wasVisible)
		{
			this.viewer.getScene().setCursor(Cursor.DEFAULT);
			wasVisible = false;
		}
	}

	@Override
	public void setCanvasSize(final int width, final int height)
	{
	}

}
