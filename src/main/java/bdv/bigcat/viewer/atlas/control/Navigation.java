package bdv.bigcat.viewer.atlas.control;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import bdv.bigcat.viewer.atlas.control.navigation.AffineTransformWithListeners;
import bdv.bigcat.viewer.atlas.control.navigation.TranslateAlongNormal;
import bdv.bigcat.viewer.atlas.control.navigation.TranslateWithinPlane;
import bdv.bigcat.viewer.atlas.control.navigation.Zoom;
import bdv.bigcat.viewer.bdvfx.EventFX;
import bdv.bigcat.viewer.bdvfx.InstallAndRemove;
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.MouseDragFX;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import net.imglib2.realtransform.AffineTransform3D;

public class Navigation implements ToOnEnterOnExit
{

	private static final double[] factors = { 1.0, 10.0, 0.1 };

	private final DoubleProperty zoomSpeed = new SimpleDoubleProperty( 1.05 );

	private final DoubleProperty translationSpeed = new SimpleDoubleProperty( 1.0 );

	private final GlobalTransformManager manager;

	private final KeyTracker keyTracker;

	private final HashMap< ViewerPanelFX, Collection< InstallAndRemove< Node > > > mouseAndKeyHandlers = new HashMap<>();

	private final Function< ViewerPanelFX, AffineTransformWithListeners > displayTransform;

	private final Function< ViewerPanelFX, AffineTransformWithListeners > globalToViewerTransform;

	public Navigation(
			final GlobalTransformManager manager,
			final Function< ViewerPanelFX, AffineTransformWithListeners > displayTransform,
			final Function< ViewerPanelFX, AffineTransformWithListeners > globalToViewerTransform,
			final KeyTracker keyTracker )
	{
		super();
		this.manager = manager;
		this.displayTransform = displayTransform;
		this.globalToViewerTransform = globalToViewerTransform;
		this.keyTracker = keyTracker;
	}

	@Override
	public Consumer< ViewerPanelFX > getOnEnter()
	{
		return t -> {
			if ( !this.mouseAndKeyHandlers.containsKey( t ) )
			{

				final AffineTransform3D viewerTransform = new AffineTransform3D();
				t.addTransformListener( viewerTransform::set );

				final AffineTransform3D worldToSharedViewerSpace = new AffineTransform3D();

				this.displayTransform.apply( t ).addListener( tf -> {
					globalToViewerTransform.apply( t ).getTransformCopy( worldToSharedViewerSpace );
					worldToSharedViewerSpace.preConcatenate( tf );
				} );

				this.globalToViewerTransform.apply( t ).addListener( tf -> {
					displayTransform.apply( t ).getTransformCopy( worldToSharedViewerSpace );
					worldToSharedViewerSpace.concatenate( tf );
				} );

				final TranslateWithinPlane translateXY = new TranslateWithinPlane(
						manager,
						this.displayTransform.apply( t ),
						this.globalToViewerTransform.apply( t ),
						manager );

				final List< InstallAndRemove< Node > > iars = new ArrayList<>();

				final TranslateAlongNormal scrollDefault = new TranslateAlongNormal( translationSpeed.multiply( factors[ 0 ] )::get, manager, worldToSharedViewerSpace, manager );
				final TranslateAlongNormal scrollFast = new TranslateAlongNormal( translationSpeed.multiply( factors[ 1 ] )::get, manager, worldToSharedViewerSpace, manager );
				final TranslateAlongNormal scrollSlow = new TranslateAlongNormal( translationSpeed.multiply( factors[ 2 ] )::get, manager, worldToSharedViewerSpace, manager );

				iars.add( EventFX.SCROLL( "translate along normal", e -> scrollDefault.scroll( e.getDeltaY() ), event -> keyTracker.noKeysActive() ) );
				iars.add( EventFX.SCROLL( "translate along normal fast", e -> scrollFast.scroll( e.getDeltaY() ), event -> keyTracker.areOnlyTheseKeysDown( KeyCode.SHIFT ) ) );
				iars.add( EventFX.SCROLL( "translate along normal slow", e -> scrollSlow.scroll( e.getDeltaY() ), event -> keyTracker.areOnlyTheseKeysDown( KeyCode.CONTROL ) ) );

				iars.add( MouseDragFX.createDrag(
						"translate xy",
						e -> e.isSecondaryButtonDown() && keyTracker.noKeysActive(),
						true,
						manager,
						e -> translateXY.init(),
						( dX, dY ) -> translateXY.drag( dX, dY ),
						false ) );

				final Zoom zoom = new Zoom( zoomSpeed, manager, viewerTransform, manager );
				iars.add( EventFX.SCROLL(
						"zoom",
						event -> zoom.zoomCenteredAt( -event.getDeltaY(), event.getX(), event.getY() ),
						event -> keyTracker.areOnlyTheseKeysDown( KeyCode.META ) ||
								keyTracker.areOnlyTheseKeysDown( KeyCode.CONTROL, KeyCode.SHIFT ) ) );

				this.mouseAndKeyHandlers.put( t, iars );

			}
//			t.getDisplay().addHandler( this.mouseAndKeyHandlers.get( t ) );
			this.mouseAndKeyHandlers.get( t ).forEach( iar -> iar.installInto( t ) );
		};
	}

	@Override
	public Consumer< ViewerPanelFX > getOnExit()
	{
		return t -> {
			Optional
					.ofNullable( this.mouseAndKeyHandlers.get( t ) )
					.ifPresent( hs -> hs.forEach( h -> h.removeFrom( t ) ) );
		};
	}

}
