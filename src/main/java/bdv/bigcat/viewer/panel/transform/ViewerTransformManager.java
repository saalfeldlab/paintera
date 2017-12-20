package bdv.bigcat.viewer.panel.transform;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.util.AbstractNamedAction;

import bdv.bigcat.viewer.atlas.mode.Merges;
import bdv.bigcat.viewer.bdvfx.EventFX;
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.bdvfx.MouseDragFX;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.panel.ViewerNode.ViewerAxis;
import bdv.bigcat.viewer.panel.ViewerState;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import bdv.viewer.Source;
import bdv.viewer.state.SourceState;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableMap;
import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformEventHandler;
import net.imglib2.ui.TransformListener;

public class ViewerTransformManager implements TransformListener< AffineTransform3D >, TransformEventHandler< AffineTransform3D >
{

	/**
	 * One step of rotation (radian).
	 */
	final private static double step = Math.PI / 180;

	private static final double[] factors = { 1.0, 10.0, 0.1 };

	private final KeyTracker keyTracker;

	final SimpleDoubleProperty rotationSpeed = new SimpleDoubleProperty( 1.0 );

	private final SimpleDoubleProperty zoomSpeed = new SimpleDoubleProperty( 1.0 );

	private final Property< GlobalTransformManager > manager = new SimpleObjectProperty<>();

	private final AffineTransform3D global = new AffineTransform3D();

	private final AffineTransform3D concatenated = new AffineTransform3D();

	private final AffineTransform3D displayTransform = new AffineTransform3D();

	final AffineTransform3D globalToViewer;

	private TransformListener< AffineTransform3D > listener;

	private final ViewerPanelFX viewer;

	private final ViewerAxis axis;

	private final ViewerState state;

	private int canvasW = 1, canvasH = 1;

	private int centerX = 0, centerY = 0;

	private final ObservableMap< Source< ? >, Boolean > visibilityMap;

	private final ArrayList< TransformListener< AffineTransform3D > > globalTransformListeners = new ArrayList<>();

	public void rotationSpeed( final double speed )
	{
		this.rotationSpeed.set( speed );
	}

	public void zoomSpeed( final double speed )
	{
		this.zoomSpeed.set( speed );
	}

	public ViewerTransformManager(
			final ViewerPanelFX viewer,
			final ViewerAxis axis,
			final ViewerState state,
			final AffineTransform3D globalToViewer,
			final KeyTracker keyTracker,
			final ObservableMap< Source< ? >, Boolean > visibilityMap )
	{
		super();
		this.viewer = viewer;
		this.axis = axis;
		this.state = state;
		this.globalToViewer = globalToViewer;
		this.canvasH = 1;
		this.canvasW = 1;
		this.centerX = this.canvasW / 2;
		this.centerY = this.canvasH / 2;
		this.visibilityMap = visibilityMap;

		setTransformListener( viewer );
		manager.addListener( ( obs, oldv, newv ) -> {
			Optional.ofNullable( oldv ).ifPresent( this::hangUp );
			Optional.ofNullable( newv ).ifPresent( this::listen );
		} );

		this.keyTracker = keyTracker;

		this.manager.bind( state.globalTransformProperty() );

		setUpViewer();
	}

	public void setGlobalToViewer( final AffineTransform3D affine )
	{
		this.globalToViewer.set( affine );
		update();
	}

	private void notifyListener()
	{
		final AffineTransform3D copy = concatenated.copy();
//			copy.preConcatenate( globalToViewer );
		listener.transformChanged( copy );
	}

	private synchronized void update()
	{
		concatenated.set( global );
		concatenated.preConcatenate( globalToViewer );
		concatenated.preConcatenate( displayTransform );
		notifyListener();
	}

	@Override
	public synchronized void setTransform( final AffineTransform3D transform )
	{
		global.set( transform );
		update();
	}

	@Override
	public void transformChanged( final AffineTransform3D transform )
	{
		setTransform( transform );
	}

	@Override
	public void setCanvasSize( final int width, final int height, final boolean updateTransform )
	{
		if ( width == 0 || height == 0 )
			return;
		if ( updateTransform ) // && false )
			synchronized ( this )
			{
				displayTransform.set( displayTransform.get( 0, 3 ) - canvasW / 2, 0, 3 );
				displayTransform.set( displayTransform.get( 1, 3 ) - canvasH / 2, 1, 3 );
				displayTransform.scale( ( double ) width / canvasW );
				displayTransform.set( displayTransform.get( 0, 3 ) + width / 2, 0, 3 );
				displayTransform.set( displayTransform.get( 1, 3 ) + height / 2, 1, 3 );
				update();
				notifyListener();
			}
		canvasW = width;
		canvasH = height;
		centerX = width / 2;
		centerY = height / 2;
	}

	@Override
	public synchronized AffineTransform3D getTransform()
	{
		return concatenated.copy();
	}

	@Override
	public void setTransformListener( final TransformListener< AffineTransform3D > transformListener )
	{
		this.listener = transformListener;
	}

	private void setUpViewer()
	{

		final TranslateXY translateXY = new TranslateXY(
				"drag translate",
				event -> keyTracker.noKeysActive() && event.getButton().equals( MouseButton.SECONDARY ),
				event -> keyTracker.noKeysActive() && event.getButton().equals( MouseButton.MIDDLE ) );

		final Rotate[] rotations = {
				new Rotate( "rotate", rotationSpeed, factors[ 0 ], event -> {
					return keyTracker.noKeysActive() && event.getButton().equals( MouseButton.PRIMARY );
				} ),
				new Rotate( "rotate fast", rotationSpeed, factors[ 1 ], event -> {
					return keyTracker.areOnlyTheseKeysDown( KeyCode.SHIFT ) && event.getButton().equals( MouseButton.PRIMARY );
				} ),
				new Rotate( "rotate slow", rotationSpeed, factors[ 2 ], event -> {
					return keyTracker.areOnlyTheseKeysDown( KeyCode.CONTROL ) && event.getButton().equals( MouseButton.PRIMARY );
				} )
		};

//		final EventFX< KeyEvent > cycleForward = EventFX.KEY_PRESSED( "cycle sources forward", new CycleSources( CycleSources.FORWARD )::cycle, event -> activeKeys.size() == 2 && activeKeys.containsAll( Arrays.asList( KeyCode.CONTROL, KeyCode.TAB ) ) );
//		final EventFX< KeyEvent > cycleBackward = EventFX.KEY_PRESSED( "cycle sources backward", new CycleSources( CycleSources.BACKWARD )::cycle, event -> activeKeys.size() == 3 && activeKeys.containsAll( Arrays.asList( KeyCode.CONTROL, KeyCode.TAB, KeyCode.SHIFT ) ) );
		final EventFX< KeyEvent > cycleForward = EventFX.KEY_PRESSED( "cycle sources forward", new CycleSources( CycleSources.FORWARD )::cycle, event -> event.isControlDown() && !event.isShiftDown() && event.getCode().equals( KeyCode.TAB ) );
		final EventFX< KeyEvent > cycleBackward = EventFX.KEY_PRESSED( "cycle sources backward", new CycleSources( CycleSources.BACKWARD )::cycle, event -> event.isControlDown() && event.isShiftDown() && event.getCode().equals( KeyCode.TAB ) );

		final Zoom zoom = new Zoom(
				zoomSpeed,
				event -> keyTracker.areOnlyTheseKeysDown( KeyCode.META ),
				event -> keyTracker.areOnlyTheseKeysDown( KeyCode.CONTROL, KeyCode.SHIFT ) );

		final EventFX< KeyEvent > removeRotation = EventFX.KEY_PRESSED( "remove rotation", new RemoveRotation()::handle, event -> Merges.shiftOnly( event ) && event.getCode().equals( KeyCode.Z ) );

//		addActiveKey.installInto( viewer );
//		removeActiveKey.installInto( viewer );
		translateXY.installInto( this.viewer );
		Arrays.stream( rotations ).forEach( r -> r.installInto( viewer ) );
		cycleForward.installInto( viewer );
		cycleBackward.installInto( viewer );
		viewer.addEventHandler( ScrollEvent.SCROLL, zoom );
		removeRotation.installInto( this.viewer );

//		behaviours.behaviour( new ButtonZoom( 1.05 ), "zoom", "UP" );
//		behaviours.behaviour( new ButtonZoom( 1.0 / 1.05 ), "zoom", "DOWN" );
		viewer.addEventHandler( ScrollEvent.SCROLL, new TranslateZ( zoomSpeed, manager, global, axis, factors[ 0 ], viewer, event -> keyTracker.noKeysActive() )::scroll );
		viewer.addEventHandler( ScrollEvent.SCROLL, new TranslateZ( zoomSpeed, manager, global, axis, factors[ 1 ], viewer, event -> keyTracker.areOnlyTheseKeysDown( KeyCode.SHIFT ) )::scroll );
		viewer.addEventHandler( ScrollEvent.SCROLL, new TranslateZ( zoomSpeed, manager, global, axis, factors[ 2 ], viewer, event -> keyTracker.areOnlyTheseKeysDown( KeyCode.CONTROL ) )::scroll );
		viewer.addEventHandler( KeyEvent.KEY_PRESSED, EventFX.KEY_PRESSED( "toggle visibility", new ToggleVisibility()::handle, event -> keyTracker.areOnlyTheseKeysDown( KeyCode.V ) ) );

	}

	@Override
	public String getHelpString()
	{
		return "TODO";
	}

	private class GetOuter
	{
		public ViewerTransformManager getOuter()
		{
			return ViewerTransformManager.this;
		}
	}

	public GlobalTransformManager getGlobalTransform()
	{
		return this.manager.getValue();
	}

	private class TranslateXY2
	{
		private final double[] delta = new double[ 3 ];

		private final AffineTransform3D affineDrag = new AffineTransform3D();

		private double startX;

		private double startY;

		private boolean isDragging;

		public void initDrag( final javafx.scene.input.MouseEvent event )
		{
			synchronized ( global )
			{
				affineDrag.set( global );
			}
			this.startX = event.getX();
			this.startY = event.getY();
			isDragging = true;
		}

		public void drag( final javafx.scene.input.MouseEvent event )
		{
			if ( isDragging )
			{
				event.consume();
				synchronized ( global )
				{
					final double x = event.getX();
					final double y = event.getY();
					final double dX = ( x - startX ) / displayTransform.get( 0, 0 );
					final double dY = ( y - startY ) / displayTransform.get( 0, 0 );
					global.set( affineDrag );
					delta[ 0 ] = dX;
					delta[ 1 ] = dY;
					delta[ 2 ] = 0.0;

					globalToViewer.applyInverse( delta, delta );
					for ( int d = 0; d < delta.length; ++d )
						global.set( global.get( d, 3 ) + delta[ d ], d, 3 );
					getGlobalTransform().setTransform( global );
				}
			}
		}

		public void endDrag( final javafx.scene.input.MouseEvent event )
		{
			if ( isDragging )
			{
				event.consume();
				isDragging = false;
			}
		}
	}

	private class TranslateXY extends MouseDragFX
	{

		public TranslateXY( final String name, final Predicate< MouseEvent >... eventFilter )
		{
			super( name, eventFilter, global );
		}

		private final double[] delta = new double[ 3 ];

		private final AffineTransform3D affineDrag = new AffineTransform3D();

		@Override
		public void initDrag( final javafx.scene.input.MouseEvent event )
		{
			synchronized ( transformLock )
			{
				affineDrag.set( global );
			}
		}

		@Override
		public void drag( final javafx.scene.input.MouseEvent event )
		{
			synchronized ( transformLock )
			{
				final double x = event.getX();
				final double y = event.getY();
				final double dX = ( x - startX ) / displayTransform.get( 0, 0 );
				final double dY = ( y - startY ) / displayTransform.get( 0, 0 );
				global.set( affineDrag );
				delta[ 0 ] = dX;
				delta[ 1 ] = dY;
				delta[ 2 ] = 0.0;

				globalToViewer.applyInverse( delta, delta );
				for ( int d = 0; d < delta.length; ++d )
					global.set( global.get( d, 3 ) + delta[ d ], d, 3 );
				getGlobalTransform().setTransform( global );
			}

		}
	}

	private class Zoom extends GetOuter implements EventHandler< ScrollEvent >
	{

		private final SimpleDoubleProperty speed = new SimpleDoubleProperty();

		private final Predicate< ScrollEvent >[] check;

		public Zoom( final DoubleProperty speed, final Predicate< ScrollEvent >... check )
		{
			this.speed.set( speed.get() );
			this.speed.bind( speed );
			this.check = check;
		}

		@Override
		public void handle( final ScrollEvent event )
		{
			if ( Arrays.stream( check ).filter( c -> c.test( event ) ).count() == 0 )
				return;
			final AffineTransform3D global = new AffineTransform3D();
			synchronized ( getOuter().global )
			{
				global.set( getOuter().global );
			}
			final double x = event.getX();
			final double y = event.getY();
			final double[] location = new double[] { x, y, 0 };
			concatenated.applyInverse( location, location );
			global.apply( location, location );

			final double wheelRotation = -event.getDeltaY();

			final double s = speed.get() * wheelRotation;
			final double dScale = 1.0 + 0.05;
			final double scale = s > 0 ? 1.0 / dScale : dScale;

			for ( int d = 0; d < location.length; ++d )
				global.set( global.get( d, 3 ) - location[ d ], d, 3 );
			global.scale( scale );
			for ( int d = 0; d < location.length; ++d )
				global.set( global.get( d, 3 ) + location[ d ], d, 3 );

			getGlobalTransform().setTransform( global );
		}
	}

	private class ButtonZoom extends GetOuter implements ClickBehaviour
	{
		private final double factor;

		public ButtonZoom( final double factor )
		{
			this.factor = factor;
		}

		@Override
		public void click( final int x, final int y )
		{
			final AffineTransform3D global;
			synchronized ( getOuter().global )
			{
				global = getOuter().global.copy();
			}
			final double[] location = new double[] { x, y, 0 };
			concatenated.applyInverse( location, location );
			global.apply( location, location );

			for ( int d = 0; d < location.length; ++d )
				global.set( global.get( d, 3 ) - location[ d ], d, 3 );
			global.scale( factor );
			for ( int d = 0; d < location.length; ++d )
				global.set( global.get( d, 3 ) + location[ d ], d, 3 );

			getGlobalTransform().setTransform( global );
		}
	}

	private class Rotate extends MouseDragFX
	{
		private final SimpleDoubleProperty speed = new SimpleDoubleProperty();

		private final AffineTransform3D affineDragStart = new AffineTransform3D();

		private final double factor;

		public Rotate( final String name, final DoubleProperty speed, final double factor, final Predicate< MouseEvent >... eventFilter )
		{
			super( name, eventFilter, global );
			this.factor = factor;
			this.speed.set( speed.get() * this.factor );
			speed.addListener( ( obs, old, newv ) -> this.speed.set( this.factor * speed.get() ) );
		}

		@Override
		public void initDrag( final javafx.scene.input.MouseEvent event )
		{
			synchronized ( transformLock )
			{
				affineDragStart.set( global );
			}
		}

		@Override
		public void drag( final javafx.scene.input.MouseEvent event )
		{
			final AffineTransform3D affine = new AffineTransform3D();
			synchronized ( transformLock )
			{
				final double v = step * this.speed.get();
				affine.set( affineDragStart );
				final double x = event.getX();
				final double y = event.getY();
				final double[] point = new double[] { x, y, 0 };
				final double[] origin = new double[] { startX, startY, 0 };

				displayTransform.applyInverse( point, point );
				displayTransform.applyInverse( origin, origin );

				final double[] delta = new double[] { point[ 0 ] - origin[ 0 ], point[ 1 ] - origin[ 1 ], 0 };
				// TODO do scaling separately. need to swap .get( 0, 0 ) and
				// .get( 1, 1 ) ?
				final double[] rotation = new double[] { delta[ 1 ] * v * displayTransform.get( 0, 0 ), -delta[ 0 ] * v * displayTransform.get( 1, 1 ), 0 };

				globalToViewer.applyInverse( origin, origin );
				globalToViewer.applyInverse( rotation, rotation );

				// center shift
				for ( int d = 0; d < origin.length; ++d )
					affine.set( affine.get( d, 3 ) - origin[ d ], d, 3 );

				for ( int d = 0; d < rotation.length; ++d )
					affine.rotate( d, rotation[ d ] );

				// center un-shift
				for ( int d = 0; d < origin.length; ++d )
					affine.set( affine.get( d, 3 ) + origin[ d ], d, 3 );

				getGlobalTransform().setTransform( affine );
			}
		}
	}

	private class RemoveRotation
	{

//		This only works when we assume that affine can be
//		https://stackoverflow.com/questions/10546320/remove-rotation-from-a-4x4-homogeneous-transformation-matrix
//		scaling S is symmetric
//		tr is transpose
//		| x2 |   | R11*SX R12*SY R13*SZ TX | | x1 |
//		| y2 | = | R21*SX R22*SY R23*SZ TY | | y1 |
//		| z2 |   | R31*SX R32*SY R33*SZ TZ | | z1 |
//		| 1  |   | 0      0      0      1  | |  1 |
//		tr(A)*A = tr(R*S)*(R*S) = tr(S)*tr(R)*R*S = tr(S)*S == S * S
//		S = sqrt( tr(A) * A )
//		( tr(A)*A )_ij = sum_k( tr(A)_ik * A_kj ) = sum_k( A_ki * A_kj )
//		( tr(A)*A )_ii = sum_k( (A_ki)^2 )

		public RemoveRotation()
		{
			super();
		}

		private final double[] mouseLocation = new double[ 3 ];

		private final double[] inOriginalSpace = new double[ 3 ];

		private final RealPoint p = RealPoint.wrap( mouseLocation );

		public void handle( final KeyEvent event )
		{
			synchronized ( viewer )
			{
				if ( viewer.isMouseInside() )
					viewer.getMouseCoordinates( p );
				else
				{
					p.setPosition( centerX, 0 );
					p.setPosition( centerY, 1 );
				}
				p.setPosition( 0l, 2 );
			}
			synchronized ( global )
			{
				viewer.getMouseCoordinates( p );
				p.setPosition( 0l, 2 );
				displayTransform.applyInverse( mouseLocation, mouseLocation );
				globalToViewer.applyInverse( mouseLocation, mouseLocation );

				global.applyInverse( inOriginalSpace, mouseLocation );

				final AffineTransform3D affine = new AffineTransform3D();
				for ( int i = 0; i < affine.numDimensions(); ++i )
				{
					double val = 0.0;
					for ( int k = 0; k < affine.numDimensions(); ++k )
					{
						final double entry = global.get( k, i );
						val += entry * entry;
					}
					val = Math.sqrt( val );
					affine.set( val, i, i );
					affine.set( mouseLocation[ i ] - inOriginalSpace[ i ] * val, i, 3 );
				}

				getGlobalTransform().setTransform( affine );
			}
		}

	}

	private class CycleSources
	{

		private final static int FORWARD = 1;

		private final static int BACKWARD = -1;

		private final int direction;

		public CycleSources( final int direction )
		{
			this.direction = direction;
		}

		public void cycle( final KeyEvent arg0 )
		{
			synchronized ( state )
			{
				synchronized ( viewer )
				{
					final int activeSource = viewer.getVisibilityAndGrouping().getCurrentSource();
					final List< SourceState< ? > > sources = viewer.getVisibilityAndGrouping().getSources();
					final int numSources = sources.size();
					if ( numSources > 0 )
					{
						final int sourceIndex = activeSource + Integer.signum( direction );
						final int selectedSource = ( sourceIndex < 0 ? sources.size() + sourceIndex : sourceIndex ) % numSources;
						state.setCurrentSource( sources.get( selectedSource ).getSpimSource() );
					}
				}
			}
		}
	}

	private class ToggleInterpolation extends AbstractNamedAction
	{
		public ToggleInterpolation()
		{
			super( "toggle interpolation" );
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			synchronized ( state )
			{
				state.toggleInterpolation();
			}
		}

	}

	private class ToggleVisibility implements EventHandler< KeyEvent >
	{
		// TODO track state and show in status bar
		@Override
		public void handle( final KeyEvent event )
		{
			final bdv.viewer.state.ViewerState state = viewer.getState();
			final int currentSource = state.getCurrentSource();
			final List< SourceState< ? > > sources = state.getSources();
			visibilityMap.put( sources.get( currentSource ).getSpimSource(), !state.isSourceVisible( currentSource ) );
		}
	}

	private void listen( final GlobalTransformManager m )
	{
		m.addListener( this );
	}

	private void hangUp( final GlobalTransformManager m )
	{
		m.removeListener( this );
	}

}
