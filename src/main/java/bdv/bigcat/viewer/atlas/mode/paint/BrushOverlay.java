package bdv.bigcat.viewer.atlas.mode.paint;

import java.lang.invoke.MethodHandles;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.bdvfx.OverlayRendererGeneric;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.scene.Cursor;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import net.imglib2.realtransform.AffineTransform3D;

public class BrushOverlay implements OverlayRendererGeneric< GraphicsContext >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final double strokeWidth = 1.5;

	private final ViewerPanelFX viewer;

	private double x, y, width, height;

	private final SimpleDoubleProperty physicalRadius = new SimpleDoubleProperty();

	private final SimpleDoubleProperty viewerRadius = new SimpleDoubleProperty();

	protected boolean visible = false;

	protected boolean wasVisible = false;

	final AffineTransform3D viewerTransform = new AffineTransform3D();

	public BrushOverlay( final ViewerPanelFX viewer, final GlobalTransformManager manager )
	{
		this.viewer = viewer;
		this.viewer.getDisplay().addOverlayRenderer( this );
		this.viewer.addEventFilter( MouseEvent.MOUSE_MOVED, this::setPosition );
		this.viewer.addEventFilter( MouseEvent.MOUSE_DRAGGED, this::setPosition );

		this.viewerRadius.addListener( ( obs, oldv, newv ) -> this.viewer.getDisplay().drawOverlays() );
		this.viewerRadius.addListener( ( obs, oldv, newv ) -> LOG.debug( "Updating paint brush overlay radius: physical radius={}, viewer radius={}, viewer transform={}", physicalRadius, viewerRadius, viewerTransform ) );

		this.physicalRadius.addListener( ( obs, oldv, newv ) -> this.updateViewerRadius( this.viewerTransform.copy() ) );
		viewer.addTransformListener( tf -> viewerTransform.set( tf ) );
		viewer.addTransformListener( this::updateViewerRadius );
		viewer.getState().getViewerTransform( viewerTransform );
		this.updateViewerRadius( viewerTransform );

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

			final double scaledRadius = this.viewerRadius.get();

			if ( x + scaledRadius > 0 &&
					x - scaledRadius < width &&
					y + scaledRadius > 0 &&
					y - scaledRadius < height )
			{
				g.setStroke( Color.WHITE );
				g.setLineWidth( this.strokeWidth );
				g.strokeOval( x - scaledRadius, y - scaledRadius, 2 * scaledRadius + 1, 2 * scaledRadius + 1 );
//				this.viewer.getScene().setCursor( Cursor.NONE );
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

	public DoubleProperty physicalRadiusProperty()
	{
		return this.physicalRadius;
	}

	public ObservableDoubleValue viewerRadiusProperty()
	{
		return this.viewerRadius;
	}

	private void updateViewerRadius( final AffineTransform3D transform )
	{
		this.viewerRadius.set( viewerRadius( transform, this.physicalRadius.get() ) );
	}

	public static double viewerRadius(
			final AffineTransform3D transform,
			final double physicalRadius )
	{
		final double sum11 = IntStream.range( 0, 3 ).mapToDouble( i -> transform.inverse().get( i, 0 ) ).map( d -> d * d ).sum();
		final double scaleRadius = physicalRadius / Math.sqrt( sum11 );
		return scaleRadius;
	}

}
