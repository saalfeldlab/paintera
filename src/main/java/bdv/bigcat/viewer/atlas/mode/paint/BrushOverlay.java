package bdv.bigcat.viewer.atlas.mode.paint;

import bdv.bigcat.viewer.bdvfx.OverlayRendererGeneric;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Cursor;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import net.imglib2.realtransform.AffineTransform3D;

public class BrushOverlay implements OverlayRendererGeneric< GraphicsContext >
{
	private final double strokeWidth = 1.5;

	private final ViewerPanelFX viewer;

	private double x, y, width, height;

	private final SimpleDoubleProperty radius = new SimpleDoubleProperty();

	protected boolean visible = false;

	protected boolean wasVisible = false;

	final AffineTransform3D viewerTransform = new AffineTransform3D();

	public BrushOverlay( final ViewerPanelFX viewer, final GlobalTransformManager manager )
	{
		this.viewer = viewer;
		this.viewer.getDisplay().addOverlayRenderer( this );
		this.viewer.addEventFilter( MouseEvent.MOUSE_MOVED, this::setPosition );
		this.viewer.addEventFilter( MouseEvent.MOUSE_DRAGGED, this::setPosition );
		manager.addListener( tf -> viewerTransform.set( tf ) );
		this.radius.addListener( ( obs, oldv, newv ) -> this.viewer.getDisplay().drawOverlays() );

	}

	public void setVisible( final boolean visible )
	{
		if ( visible != this.visible )
		{
			if ( this.visible )
				this.wasVisible = true;
			this.visible = visible;
			this.viewer.getDisplay().drawOverlays();
		}
	}

	public void setPosition( final MouseEvent event )
	{
		setPosition( event.getX(), event.getY() );
	}

	public void setPosition( final double x, final double y )
	{
		this.x = x;
		this.y = y;
		this.viewer.getDisplay().drawOverlays();
	}

	@Override
	public void drawOverlays( final GraphicsContext g )
	{

		if ( visible && this.viewer.isMouseInside() )
		{

//			final double scale = Affine3DHelpers.extractScale( viewerTransform, 0 );
			final double scale = 1.0;
			final double scaledRadius = scale * radius.get();

			if ( x + scaledRadius > 0 &&
					x - scaledRadius < width &&
					y + scaledRadius > 0 &&
					y - scaledRadius < height )
			{
				g.setStroke( Color.WHITE );
				g.setLineWidth( this.strokeWidth );
				g.strokeOval( x - scaledRadius, y - scaledRadius, 2 * scaledRadius + 1, 2 * scaledRadius + 1 );
				this.viewer.getScene().setCursor( Cursor.NONE );
				return;
			}
		}
		if ( wasVisible )
		{
			this.viewer.getScene().setCursor( Cursor.DEFAULT );
			wasVisible = false;
		}
	}

	@Override
	public void setCanvasSize( final int width, final int height )
	{
		this.width = width;
		this.height = height;
	}

	public SimpleDoubleProperty radiusProperty()
	{
		return this.radius;
	}

}
