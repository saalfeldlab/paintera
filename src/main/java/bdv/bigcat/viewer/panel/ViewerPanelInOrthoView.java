package bdv.bigcat.viewer.panel;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.atlas.control.navigation.AffineTransformWithListeners;
import bdv.bigcat.viewer.atlas.control.navigation.DisplayTransformUpdateOnResize;
import bdv.bigcat.viewer.atlas.control.navigation.TransformConcatenator;
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceAndConverter;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import net.imglib2.realtransform.AffineTransform3D;

public class ViewerPanelInOrthoView implements ListChangeListener< SourceAndConverter< ? > >
{

	public static Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public enum ViewerAxis
	{
		X, Y, Z
	};

	private final ViewerPanelFX viewer;

	private final ViewerState state;

	private final AffineTransformWithListeners globalToViewerTransformWithListeners;

	private final AffineTransformWithListeners displayTransformWithListeners;

	private final TransformConcatenator concatenator;

	private final DisplayTransformUpdateOnResize displayTransformUpdate;

	private final CrossHair crosshair = new CrossHair();

	private final Color onFocusColor = Color.rgb( 255, 255, 255, 180.0 / 255.0 );

	private final Color outOfFocusColor = Color.rgb( 127, 127, 127, 100.0 / 255.0 );
	{
		crosshair.setColor( outOfFocusColor );
	}

	public ViewerPanelInOrthoView(
			final ViewerPanelFX viewer,
			final GlobalTransformManager manager,
			final ViewerAxis viewerAxis,
			final KeyTracker keyTracker )
	{
		super();
		this.viewer = viewer;
		this.globalToViewerTransformWithListeners = new AffineTransformWithListeners( globalToViewer( viewerAxis ) );
		LOG.warn( "Making viewer with axis={} and transform={}", viewerAxis, globalToViewerTransformWithListeners );
		this.displayTransformWithListeners = new AffineTransformWithListeners( makeInitialDisplayTransform( viewer.getWidth(), viewer.getHeight() ) );
		this.concatenator = new TransformConcatenator(
				manager,
				this.displayTransformWithListeners,
				this.globalToViewerTransformWithListeners,
				manager );
		this.displayTransformUpdate = new DisplayTransformUpdateOnResize(
				this.displayTransformWithListeners,
				viewer.widthProperty(),
				viewer.heightProperty(),
				manager );
		this.displayTransformUpdate.listen();
		this.concatenator.setTransformListener( viewer::setCurrentViewerTransform );
		this.concatenator.forceUpdate();
		this.viewer.setMinSize( 0, 0 );

//		this.viewer.showMultibox( false );
		this.state = new ViewerState( this.viewer );
		initializeViewer();
		addCrosshair();
//		this.displayTransformWithListeners.setTransform( makeInitialDisplayTransform( viewer.getWidth(), viewer.getHeight() ) );

//		https://stackoverflow.com/questions/21657034/javafx-keyevent-propagation-order
//		https://stackoverflow.com/questions/32802664/setonkeypressed-event-not-working-properly
//		Node only reacts to key events when focused!
		this.viewer.addEventFilter( MouseEvent.MOUSE_ENTERED, event -> {
			this.viewer.requestFocus();
		} );
		this.viewer.addEventFilter( MouseEvent.MOUSE_PRESSED, event -> {
			if ( !this.viewer.isFocused() )
			{
				this.viewer.requestFocus();
				event.consume();
			}
		} );
	}

	private void addCrosshair()
	{

		this.viewer.focusedProperty().addListener( ( ChangeListener< Boolean > ) ( observable, oldValue, newValue ) -> {
			if ( newValue )
				crosshair.setColor( onFocusColor );
			else
				crosshair.setColor( outOfFocusColor );
			viewer.getDisplay().drawOverlays();
		} );
	}

	public void setCrossHairColor( final int r, final int g, final int b, final int a )
	{
		this.crosshair.setColor( r, g, b, a );
	}

	public void setViewerPanelState( final ViewerState state )
	{
		this.state.set( state );
	}

	private void initializeViewer()
	{
		viewer.setDisplayMode( DisplayMode.FUSED );
		viewer.getDisplay().addOverlayRenderer( crosshair );
	}

	public static AffineTransform3D globalToViewer( final ViewerAxis axis )
	{

		final AffineTransform3D tf = new AffineTransform3D();
		switch ( axis )
		{
		case Z:
			break;
		case Y:
			tf.rotate( 0, -Math.PI / 2 );
			break;
		case X:
			tf.rotate( 1, Math.PI / 2 );
			break;
		}
		return tf;
	}

	@Override
	public void onChanged( final javafx.collections.ListChangeListener.Change< ? extends SourceAndConverter< ? > > c )
	{

		c.next();
		if ( c.wasRemoved() )
			c.getRemoved().forEach( removed -> {
				viewer.removeSource( removed.getSpimSource() );
			} );
		else if ( c.wasAdded() )
			c.getAddedSubList().forEach( added -> {
				viewer.addSource( added );
			} );
	}

	public ViewerState getViewerState()
	{
		return state;
	}

	public ViewerPanelFX getViewer()
	{
		return this.viewer;
	}

	public AffineTransformWithListeners getGlobalToViewerTransform()
	{
		return this.globalToViewerTransformWithListeners;
	}

	public AffineTransformWithListeners getDisplayTransform()
	{
		return this.displayTransformWithListeners;
	}

	public AffineTransform3D getDisplayTransformCopy()
	{
		return this.displayTransformWithListeners.getTransformCopy();
	}

	public void setDisplayTransform( final AffineTransform3D affine )
	{
		this.displayTransformWithListeners.setTransform( affine );
	}

	public static AffineTransform3D makeInitialDisplayTransform( final double w, final double h )
	{
		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				1, 0, 0, 0.5 * w,
				0, 1, 0, 0.5 * h,
				0, 0, 1, 0.0 );
		return transform;
	}
}
