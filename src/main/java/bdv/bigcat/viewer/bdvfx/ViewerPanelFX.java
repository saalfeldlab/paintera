/*
 * #%L
 * BigDataViewer core classes with minimal dependencies
 * %%
 * Copyright (C) 2012 - 2016 Tobias Pietzsch, Stephan Saalfeld, Stephan Preibisch,
 * Jean-Yves Tinevez, HongKee Moon, Johannes Schindelin, Curtis Rueden, John Bogovic
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bdv.bigcat.viewer.bdvfx;

import static bdv.viewer.VisibilityAndGrouping.Event.CURRENT_SOURCE_CHANGED;
import static bdv.viewer.VisibilityAndGrouping.Event.DISPLAY_MODE_CHANGED;
import static bdv.viewer.VisibilityAndGrouping.Event.GROUP_ACTIVITY_CHANGED;
import static bdv.viewer.VisibilityAndGrouping.Event.GROUP_NAME_CHANGED;
import static bdv.viewer.VisibilityAndGrouping.Event.NUM_GROUPS_CHANGED;
import static bdv.viewer.VisibilityAndGrouping.Event.NUM_SOURCES_CHANGED;
import static bdv.viewer.VisibilityAndGrouping.Event.SOURCE_ACTVITY_CHANGED;
import static bdv.viewer.VisibilityAndGrouping.Event.VISIBILITY_CHANGED;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.jdom2.Element;

import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import bdv.cache.CacheControl;
import bdv.util.Prefs;
import bdv.viewer.DisplayMode;
import bdv.viewer.Interpolation;
import bdv.viewer.InterpolationModeListener;
import bdv.viewer.RequestRepaint;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.TimePointListener;
import bdv.viewer.ViewerOptions;
import bdv.viewer.state.SourceGroup;
import bdv.viewer.state.ViewerState;
import bdv.viewer.state.XmlIoViewerState;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Slider;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import net.imglib2.Positionable;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.OverlayRenderer;
import net.imglib2.ui.PainterThread;
import net.imglib2.ui.TransformListener;

/**
 * A JPanel for viewing multiple of {@link Source}s. The panel contains a
 * {@link InteractiveDisplayPaneComponent canvas} and a time slider (if there
 * are multiple time-points). Maintains a {@link ViewerState render state}, the
 * renderer, and basic navigation help overlays. It has it's own
 * {@link PainterThread} for painting, which is started on construction (use
 * {@link #stop() to stop the PainterThread}.
 *
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
public class ViewerPanelFX
		extends BorderPane
		implements OverlayRendererGeneric< GraphicsContext >, TransformListener< AffineTransform3D >, PainterThread.Paintable, VisibilityAndGrouping.UpdateListener, RequestRepaint
{
	private static final long serialVersionUID = 1L;

	/**
	 * Currently rendered state (visible sources, transformation, timepoint,
	 * etc.) A copy can be obtained by {@link #getState()}.
	 */
	protected final ViewerState state;

	/**
	 * Renders the current state for the {@link #display}.
	 */
	protected final MultiResolutionRendererFX imageRenderer;

	/**
	 * TODO
	 */
	protected final TransformAwareBufferedImageOverlayRendererFX renderTarget;

	/**
	 * Transformation set by the interactive viewer.
	 */
	protected final AffineTransform3D viewerTransform;

	/**
	 * Canvas used for displaying the rendered {@link #renderTarget image} and
	 * overlays.
	 */
	protected final InteractiveDisplayPaneComponent< AffineTransform3D > display;

	protected final Slider sliderTime;

	protected final SimpleBooleanProperty showSlider = new SimpleBooleanProperty( true );

	/**
	 * A {@link ThreadGroup} for (only) the threads used by this
	 * {@link ViewerPanelFX}, that is, {@link #painterThread} and
	 * {@link #renderingExecutorService}.
	 */
	protected ThreadGroup threadGroup;

	/**
	 * Thread that triggers repainting of the display.
	 */
	protected final PainterThread painterThread;

	/**
	 * The {@link ExecutorService} used for rendereing.
	 */
	protected final ExecutorService renderingExecutorService;

	/**
	 * Manages visibility and currentness of sources and groups, as well as
	 * grouping of sources, and display mode.
	 */
	protected final VisibilityAndGrouping visibilityAndGrouping;

	/**
	 * These listeners will be notified about changes to the
	 * {@link #viewerTransform}. This is done <em>before</em> calling
	 * {@link #requestRepaint()} so listeners have the chance to interfere.
	 */
	protected final CopyOnWriteArrayList< TransformListener< AffineTransform3D > > transformListeners;

	/**
	 * These listeners will be notified about changes to the
	 * {@link #viewerTransform} that was used to render the current image. This
	 * is intended for example for {@link OverlayRenderer}s that need to exactly
	 * match the transform of their overlaid content to the transform of the
	 * image.
	 */
	protected final CopyOnWriteArrayList< TransformListener< AffineTransform3D > > lastRenderTransformListeners;

	/**
	 * These listeners will be notified about changes to the current timepoint
	 * {@link ViewerState#getCurrentTimepoint()}. This is done <em>before</em>
	 * calling {@link #requestRepaint()} so listeners have the chance to
	 * interfere.
	 */
	protected final CopyOnWriteArrayList< TimePointListener > timePointListeners;

	protected final CopyOnWriteArrayList< InterpolationModeListener > interpolationModeListeners;

	protected final ViewerOptions.Values options;

	protected final SimpleDoubleProperty mouseX = new SimpleDoubleProperty();

	protected final SimpleDoubleProperty mouseY = new SimpleDoubleProperty();

	protected final SimpleBooleanProperty isInside = new SimpleBooleanProperty();

	protected final MultiBoxOverlayRendererFX multiBoxOverlayRenderer = new MultiBoxOverlayRendererFX( 1, 1 );

	protected final SimpleBooleanProperty showMultibox = new SimpleBooleanProperty( false );

	public ViewerPanelFX( final List< SourceAndConverter< ? > > sources, final int numTimePoints, final CacheControl cacheControl )
	{
		this( sources, numTimePoints, cacheControl, ViewerOptions.options() );
	}

	/**
	 * @param sources
	 *            the {@link SourceAndConverter sources} to display.
	 * @param numTimepoints
	 *            number of available timepoints.
	 * @param cacheControl
	 *            to control IO budgeting and fetcher queue.
	 * @param optional
	 *            optional parameters. See {@link ViewerOptions#options()}.
	 */
	public ViewerPanelFX( final List< SourceAndConverter< ? > > sources, final int numTimepoints, final CacheControl cacheControl, final ViewerOptions optional )
	{
		super();
		options = optional.values;
		setWidth( options.getWidth() );
		setHeight( options.getHeight() );

		final int numGroups = options.getNumSourceGroups();
		final ArrayList< SourceGroup > groups = new ArrayList<>( numGroups );
		for ( int i = 0; i < numGroups; ++i )
			groups.add( new SourceGroup( "group " + Integer.toString( i + 1 ) ) );
		state = new ViewerState( sources, groups, numTimepoints );
		for ( int i = Math.min( numGroups, sources.size() ) - 1; i >= 0; --i )
			state.getSourceGroups().get( i ).addSource( i );

		if ( !sources.isEmpty() )
			state.setCurrentSource( 0 );
//		multiBoxOverlayRenderer = new MultiBoxOverlayRenderer();
//		sourceInfoOverlayRenderer = new SourceInfoOverlayRenderer();
//		scaleBarOverlayRenderer = Prefs.showScaleBar() ? new ScaleBarOverlayRenderer() : null;

		threadGroup = new ThreadGroup( this.toString() );
		painterThread = new PainterThread( threadGroup, this );
		viewerTransform = new AffineTransform3D();
		renderTarget = new TransformAwareBufferedImageOverlayRendererFX();
		display = new InteractiveDisplayPaneComponent<>( options.getWidth(), options.getHeight(), options.getTransformEventHandlerFactory(), renderTarget );
		display.addTransformListener( this );
		renderTarget.setCanvasSize( options.getWidth(), options.getHeight() );
		display.addOverlayRenderer( this );

		renderingExecutorService = Executors.newFixedThreadPool(
				options.getNumRenderingThreads(),
				new RenderThreadFactory() );
		imageRenderer = new MultiResolutionRendererFX(
				renderTarget,
				painterThread,
				options.getScreenScales(),
				options.getTargetRenderNanos(),
				options.isDoubleBuffered(),
				options.getNumRenderingThreads(),
				renderingExecutorService,
				options.isUseVolatileIfAvailable(),
				options.getAccumulateProjectorFactory(),
				cacheControl );

		sliderTime = new Slider( 0, numTimepoints - 1, 0 );
		sliderTime.valueProperty().addListener( ( observable, oldValue, newValue ) -> setTimepoint( ( int ) sliderTime.getValue() ) );

		display.setMinSize( 0, 0 );
		setCenter( display );
		if ( numTimepoints > 1 && this.showSlider.get() )
			setBottom( sliderTime );

		visibilityAndGrouping = new VisibilityAndGrouping( state );
		visibilityAndGrouping.addUpdateListener( this );

		transformListeners = new CopyOnWriteArrayList<>();
		lastRenderTransformListeners = new CopyOnWriteArrayList<>();
		timePointListeners = new CopyOnWriteArrayList<>();
		interpolationModeListeners = new CopyOnWriteArrayList<>();

		showMultibox( Prefs.showMultibox() );

		addEventHandler( MouseEvent.MOUSE_MOVED, event -> {
			synchronized ( isInside )
			{
				if ( isInside.get() )
				{
					mouseX.set( event.getX() );
					mouseY.set( event.getY() );
				}
			}
		} );
		addEventHandler( MouseEvent.MOUSE_ENTERED, event -> {
			synchronized ( isInside )
			{
				isInside.set( true );
			}
		} );
		addEventHandler( MouseEvent.MOUSE_EXITED, event -> {
			synchronized ( isInside )
			{
				isInside.set( false );
			}
		} );

		final ChangeListener< Number > initialListener = new ChangeListener< Number >()
		{

			@Override
			public void changed( final ObservableValue< ? extends Number > observable, final Number oldValue, final Number newValue )
			{
				requestRepaint();
				synchronized ( display )
				{
					display.widthProperty().removeListener( this );
					display.heightProperty().removeListener( this );
				}
			}

		};

		synchronized ( display )
		{
			display.widthProperty().addListener( initialListener );
			display.heightProperty().addListener( initialListener );
		}

		painterThread.start();
	}

	public void addSource( final SourceAndConverter< ? > sourceAndConverter )
	{
		synchronized ( visibilityAndGrouping )
		{
			state.addSource( sourceAndConverter );
			visibilityAndGrouping.update( NUM_SOURCES_CHANGED );
		}
		requestRepaint();
	}

	public void addSources( final Collection< ? extends SourceAndConverter< ? > > sourceAndConverter )
	{
		synchronized ( visibilityAndGrouping )
		{
			sourceAndConverter.forEach( state::addSource );
			visibilityAndGrouping.update( NUM_SOURCES_CHANGED );
		}
		requestRepaint();
	}

	public void removeSource( final Source< ? > source )
	{
		synchronized ( visibilityAndGrouping )
		{
			state.removeSource( source );
			visibilityAndGrouping.update( NUM_SOURCES_CHANGED );
		}
		requestRepaint();
	}

	public void removeSources( final Collection< Source< ? > > sources )
	{
		synchronized ( visibilityAndGrouping )
		{
			sources.forEach( state::removeSource );
			visibilityAndGrouping.update( NUM_SOURCES_CHANGED );
		}
		requestRepaint();
	}

	public void removeAllSources()
	{
		synchronized ( visibilityAndGrouping )
		{
			removeSources( getState().getSources().stream().map( SourceAndConverter::getSpimSource ).collect( Collectors.toList() ) );
		}
	}

	public void setAllSources( final Collection< ? extends SourceAndConverter< ? > > sources )
	{
		synchronized ( visibilityAndGrouping )
		{
			getState().getSources().stream().map( SourceAndConverter::getSpimSource ).forEach( state::removeSource );
			addSources( sources );
		}
	}

	public void addGroup( final SourceGroup group )
	{
		synchronized ( visibilityAndGrouping )
		{
			state.addGroup( group );
			visibilityAndGrouping.update( NUM_GROUPS_CHANGED );
		}
		requestRepaint();
	}

	public void removeGroup( final SourceGroup group )
	{
		synchronized ( visibilityAndGrouping )
		{
			state.removeGroup( group );
			visibilityAndGrouping.update( NUM_GROUPS_CHANGED );
		}
		requestRepaint();
	}

	/**
	 * Set {@code gPos} to the display coordinates at gPos transformed into the
	 * global coordinate system.
	 *
	 * @param gPos
	 *            is set to the corresponding global coordinates.
	 */
	public < P extends RealLocalizable & RealPositionable > void displayToGlobalCoordinates( final double[] gPos )
	{
		assert gPos.length >= 3;

		viewerTransform.applyInverse( gPos, gPos );
	}

	/**
	 * Set {@code gPos} to the display coordinates at gPos transformed into the
	 * global coordinate system.
	 *
	 * @param gPos
	 *            is set to the corresponding global coordinates.
	 */
	public < P extends RealLocalizable & RealPositionable > void displayToGlobalCoordinates( final P gPos )
	{
		assert gPos.numDimensions() >= 3;

		viewerTransform.applyInverse( gPos, gPos );
	}

	/**
	 * Set {@code gPos} to the display coordinates (x,y,0)<sup>T</sup>
	 * transformed into the global coordinate system.
	 *
	 * @param gPos
	 *            is set to the global coordinates at display
	 *            (x,y,0)<sup>T</sup>.
	 */
	public void displayToGlobalCoordinates( final double x, final double y, final RealPositionable gPos )
	{
		assert gPos.numDimensions() >= 3;
		final RealPoint lPos = new RealPoint( 3 );
		lPos.setPosition( x, 0 );
		lPos.setPosition( y, 1 );
		viewerTransform.applyInverse( gPos, lPos );
	}

	/**
	 * Set {@code gPos} to the current mouse coordinates transformed into the
	 * global coordinate system.
	 *
	 * @param gPos
	 *            is set to the current global coordinates.
	 */
	public void getGlobalMouseCoordinates( final RealPositionable gPos )
	{
		assert gPos.numDimensions() == 3;
		final RealPoint lPos = new RealPoint( 3 );
		lPos.setPosition( mouseX.longValue(), 0 );
		lPos.setPosition( mouseY.longValue(), 1 );
		viewerTransform.applyInverse( gPos, lPos );
	}

	/**
	 * TODO
	 *
	 * @param p
	 */
	public synchronized void getMouseCoordinates( final Positionable p )
	{
		assert p.numDimensions() == 2;
		p.setPosition( mouseX.longValue(), 0 );
		p.setPosition( mouseY.longValue(), 1 );
	}

	@Override
	public void paint()
	{
		imageRenderer.paint( state );

		display.repaint();
	}

	/**
	 * Repaint as soon as possible.
	 */
	@Override
	public void requestRepaint()
	{
		imageRenderer.requestRepaint();
	}

//	@Override
//	public void layoutChildren()
//	{
//		super.layoutChildren();
//		final boolean requiresRepaint = false;
////		if ( Prefs.showMultibox() )
////		{
////			multiBoxOverlayRenderer.setViewerState( state );
////			multiBoxOverlayRenderer.updateVirtualScreenSize( ( int ) display.getWidth(), ( int ) display.getHeight() );
////			multiBoxOverlayRenderer.paint( display.canvas() );
////			requiresRepaint = multiBoxOverlayRenderer.isHighlightInProgress();
////		}
//
////		if ( Prefs.showTextOverlay() )
////		{
////			sourceInfoOverlayRenderer.setViewerState( state );
////			sourceInfoOverlayRenderer.paint( ( Graphics2D ) g );
////
////			final RealPoint gPos = new RealPoint( 3 );
////			getGlobalMouseCoordinates( gPos );
////			final String mousePosGlobalString = String.format( "(%6.1f,%6.1f,%6.1f)", gPos.getDoublePosition( 0 ), gPos.getDoublePosition( 1 ), gPos.getDoublePosition( 2 ) );
////
////			g.setFont( new Font( "Monospaced", Font.PLAIN, 12 ) );
////			g.setColor( Color.white );
////			g.drawString( mousePosGlobalString, ( int ) g.getClipBounds().getWidth() - 170, 25 );
////		}
//
////		if ( Prefs.showScaleBar() )
////		{
////			scaleBarOverlayRenderer.setViewerState( state );
////			scaleBarOverlayRenderer.paint( ( Graphics2D ) g );
////		}
//
////		final long currentTimeMillis = System.currentTimeMillis();
////		final ArrayList< OverlayAnimator > overlayAnimatorsToRemove = new ArrayList<>();
////		for ( final OverlayAnimator animator : overlayAnimators )
////		{
////			animator.paint( ( Graphics2D ) g, currentTimeMillis );
////			requiresRepaint |= animator.requiresRepaint();
////			if ( animator.isComplete() )
////				overlayAnimatorsToRemove.add( animator );
////		}
////		overlayAnimators.removeAll( overlayAnimatorsToRemove );
//
//		if ( requiresRepaint )
//			display.requestLayout();
//	}

	@Override
	public synchronized void transformChanged( final AffineTransform3D transform )
	{
		viewerTransform.set( transform );
		state.setViewerTransform( transform );
		for ( final TransformListener< AffineTransform3D > l : transformListeners )
			l.transformChanged( viewerTransform );
		requestRepaint();
	}

	@Override
	public void visibilityChanged( final VisibilityAndGrouping.Event e )
	{
		switch ( e.id )
		{
		case CURRENT_SOURCE_CHANGED:
//			multiBoxOverlayRenderer.highlight( visibilityAndGrouping.getCurrentSource() );
			display.repaint();
			break;
		case DISPLAY_MODE_CHANGED:
			display.repaint();
			break;
		case GROUP_NAME_CHANGED:
			display.repaint();
			break;
		case SOURCE_ACTVITY_CHANGED:
			// TODO multiBoxOverlayRenderer.highlight() all sources that became
			// visible
			break;
		case GROUP_ACTIVITY_CHANGED:
			// TODO multiBoxOverlayRenderer.highlight() all sources that became
			// visible
			break;
		case VISIBILITY_CHANGED:
			requestRepaint();
			break;
		}
	}

	/**
	 * Switch to next interpolation mode. (Currently, there are two
	 * interpolation modes: nearest-neighbor and N-linear.)
	 */
	public synchronized void toggleInterpolation()
	{
		final int i = state.getInterpolation().ordinal();
		final int n = Interpolation.values().length;
		final Interpolation mode = Interpolation.values()[ ( i + 1 ) % n ];
		setInterpolation( mode );
	}

	/**
	 * Set the {@link Interpolation} mode.
	 */
	public synchronized void setInterpolation( final Interpolation mode )
	{
		final Interpolation interpolation = state.getInterpolation();
		if ( mode != interpolation )
		{
			state.setInterpolation( mode );
			for ( final InterpolationModeListener l : interpolationModeListeners )
				l.interpolationModeChanged( state.getInterpolation() );
			requestRepaint();
		}
	}

	/**
	 * Set the {@link DisplayMode}.
	 */
	public synchronized void setDisplayMode( final DisplayMode displayMode )
	{
		visibilityAndGrouping.setDisplayMode( displayMode );
	}

	/**
	 * Set the viewer transform.
	 */
	public synchronized void setCurrentViewerTransform( final AffineTransform3D viewerTransform )
	{
		display.getTransformEventHandler().setTransform( viewerTransform );
		transformChanged( viewerTransform );
	}

	/**
	 * Show the specified time-point.
	 *
	 * @param timepoint
	 *            time-point index.
	 */
	public synchronized void setTimepoint( final int timepoint )
	{
		if ( state.getCurrentTimepoint() != timepoint )
		{
			state.setCurrentTimepoint( timepoint );
			sliderTime.setValue( timepoint );
			for ( final TimePointListener l : timePointListeners )
				l.timePointChanged( timepoint );
			requestRepaint();
		}
	}

	/**
	 * Show the next time-point.
	 */
	public synchronized void nextTimePoint()
	{
		if ( state.getNumTimepoints() > 1 )
			sliderTime.setValue( sliderTime.getValue() + 1 );
	}

	/**
	 * Show the previous time-point.
	 */
	public synchronized void previousTimePoint()
	{
		if ( state.getNumTimepoints() > 1 )
			sliderTime.setValue( sliderTime.getValue() - 1 );
	}

	/**
	 * Set the number of available timepoints. If {@code numTimepoints == 1}
	 * this will hide the time slider, otherwise show it. If the currently
	 * displayed timepoint would be out of range with the new number of
	 * timepoints, the current timepoint is set to {@code numTimepoints - 1}.
	 *
	 * @param numTimepoints
	 *            number of available timepoints. Must be {@code >= 1}.
	 */
	public void setNumTimepoints( final int numTimepoints )
	{
		try
		{
			InvokeOnJavaFXApplicationThread.invokeAndWait( () -> setNumTimepointsSynchronized( numTimepoints ) );
		}
		catch ( final InterruptedException e )
		{
			e.printStackTrace();
		}
	}

	private synchronized void setNumTimepointsSynchronized( final int numTimepoints )
	{

		if ( !showSlider.get() )
			setBottom( null );

		if ( numTimepoints < 1 || state.getNumTimepoints() == numTimepoints )
			return;
		else if ( numTimepoints == 1 && state.getNumTimepoints() > 1 )
			setBottom( null );
		else if ( numTimepoints > 1 && state.getNumTimepoints() == 1 && showSlider.get() )
			setBottom( sliderTime );

		state.setNumTimepoints( numTimepoints );
		if ( state.getCurrentTimepoint() >= numTimepoints )
		{
			final int timepoint = numTimepoints - 1;
			state.setCurrentTimepoint( timepoint );
			for ( final TimePointListener l : timePointListeners )
				l.timePointChanged( timepoint );
		}
		sliderTime.setMin( 0 );
		sliderTime.setMax( numTimepoints - 1 );
		sliderTime.setValue( state.getCurrentTimepoint() );
		requestRepaint();
	}

	/**
	 * Get a copy of the current {@link ViewerState}.
	 *
	 * @return a copy of the current {@link ViewerState}.
	 */
	public ViewerState getState()
	{
		return state.copy();
	}

	/**
	 * Get the viewer canvas.
	 *
	 * @return the viewer canvas.
	 */
	public InteractiveDisplayPaneComponent< AffineTransform3D > getDisplay()
	{
		return display;
	}

	/**
	 * Add a {@link InterpolationModeListener} to notify when the interpolation
	 * mode is changed. Listeners will be notified <em>before</em> calling
	 * {@link #requestRepaint()} so they have the chance to interfere.
	 *
	 * @param listener
	 *            the interpolation mode listener to add.
	 */
	public void addInterpolationModeListener( final InterpolationModeListener listener )
	{
		interpolationModeListeners.add( listener );
	}

	/**
	 * Remove a {@link InterpolationModeListener}.
	 *
	 * @param listener
	 *            the interpolation mode listener to remove.
	 */
	public void removeInterpolationModeListener( final InterpolationModeListener listener )
	{
		interpolationModeListeners.remove( listener );
	}

	/**
	 * Add a {@link TransformListener} to notify about viewer transformation
	 * changes. Listeners will be notified when a new image has been painted
	 * with the viewer transform used to render that image.
	 *
	 * This happens immediately after that image is painted onto the screen,
	 * before any overlays are painted.
	 *
	 * @param listener
	 *            the transform listener to add.
	 */
	public void addRenderTransformListener( final TransformListener< AffineTransform3D > listener )
	{
		renderTarget.addTransformListener( listener );
	}

	/**
	 * Add a {@link TransformListener} to notify about viewer transformation
	 * changes. Listeners will be notified when a new image has been painted
	 * with the viewer transform used to render that image.
	 *
	 * This happens immediately after that image is painted onto the screen,
	 * before any overlays are painted.
	 *
	 * @param listener
	 *            the transform listener to add.
	 * @param index
	 *            position in the list of listeners at which to insert this one.
	 */
	public void addRenderTransformListener( final TransformListener< AffineTransform3D > listener, final int index )
	{
		renderTarget.addTransformListener( listener, index );
	}

	/**
	 * Add a {@link TransformListener} to notify about viewer transformation
	 * changes. Listeners will be notified <em>before</em> calling
	 * {@link #requestRepaint()} so they have the chance to interfere.
	 *
	 * @param listener
	 *            the transform listener to add.
	 */
	public void addTransformListener( final TransformListener< AffineTransform3D > listener )
	{
		addTransformListener( listener, Integer.MAX_VALUE );
	}

	/**
	 * Add a {@link TransformListener} to notify about viewer transformation
	 * changes. Listeners will be notified <em>before</em> calling
	 * {@link #requestRepaint()} so they have the chance to interfere.
	 *
	 * @param listener
	 *            the transform listener to add.
	 * @param index
	 *            position in the list of listeners at which to insert this one.
	 */
	public void addTransformListener( final TransformListener< AffineTransform3D > listener, final int index )
	{
		synchronized ( transformListeners )
		{
			final int s = transformListeners.size();
			transformListeners.add( index < 0 ? 0 : index > s ? s : index, listener );
			listener.transformChanged( viewerTransform );
		}
	}

	/**
	 * Remove a {@link TransformListener}.
	 *
	 * @param listener
	 *            the transform listener to remove.
	 */
	public void removeTransformListener( final TransformListener< AffineTransform3D > listener )
	{
		synchronized ( transformListeners )
		{
			transformListeners.remove( listener );
		}
		renderTarget.removeTransformListener( listener );
	}

	/**
	 * Add a {@link TimePointListener} to notify about time-point changes.
	 * Listeners will be notified <em>before</em> calling
	 * {@link #requestRepaint()} so they have the chance to interfere.
	 *
	 * @param listener
	 *            the listener to add.
	 */
	public void addTimePointListener( final TimePointListener listener )
	{
		addTimePointListener( listener, Integer.MAX_VALUE );
	}

	/**
	 * Add a {@link TimePointListener} to notify about time-point changes.
	 * Listeners will be notified <em>before</em> calling
	 * {@link #requestRepaint()} so they have the chance to interfere.
	 *
	 * @param listener
	 *            the listener to add.
	 * @param index
	 *            position in the list of listeners at which to insert this one.
	 */
	public void addTimePointListener( final TimePointListener listener, final int index )
	{
		synchronized ( timePointListeners )
		{
			final int s = timePointListeners.size();
			timePointListeners.add( index < 0 ? 0 : index > s ? s : index, listener );
			listener.timePointChanged( state.getCurrentTimepoint() );
		}
	}

	/**
	 * Remove a {@link TimePointListener}.
	 *
	 * @param listener
	 *            the listener to remove.
	 */
	public void removeTimePointListener( final TimePointListener listener )
	{
		synchronized ( timePointListeners )
		{
			timePointListeners.remove( listener );
		}
	}

	public synchronized Element stateToXml()
	{
		return new XmlIoViewerState().toXml( state );
	}

	public synchronized void stateFromXml( final Element parent )
	{
		final XmlIoViewerState io = new XmlIoViewerState();
		io.restoreFromXml( parent.getChild( io.getTagName() ), state );
	}

	/**
	 * does nothing.
	 */
	@Override
	public void setCanvasSize( final int width, final int height )
	{}

	/**
	 * Returns the {@link VisibilityAndGrouping} that can be used to modify
	 * visibility and currentness of sources and groups, as well as grouping of
	 * sources, and display mode.
	 */
	public VisibilityAndGrouping getVisibilityAndGrouping()
	{
		return visibilityAndGrouping;
	}

	public ViewerOptions.Values getOptionValues()
	{
		return options;
	}

//	public SourceInfoOverlayRenderer getSourceInfoOverlayRenderer()
//	{
//		return sourceInfoOverlayRenderer;
//	}

	/**
	 * Stop the {@link #painterThread} and shutdown rendering
	 * {@link ExecutorService}.
	 */
	public void stop()
	{
		painterThread.interrupt();
		try
		{
			painterThread.join( 0 );
		}
		catch ( final InterruptedException e )
		{
			e.printStackTrace();
		}
		renderingExecutorService.shutdown();
		state.kill();
		imageRenderer.kill();
	}

	protected static final AtomicInteger panelNumber = new AtomicInteger( 1 );

	protected class RenderThreadFactory implements ThreadFactory
	{
		private final String threadNameFormat = String.format(
				"bdv-panel-%d-thread-%%d",
				panelNumber.getAndIncrement() );

		private final AtomicInteger threadNumber = new AtomicInteger( 1 );

		@Override
		public Thread newThread( final Runnable r )
		{
			final Thread t = new Thread( threadGroup, r,
					String.format( threadNameFormat, threadNumber.getAndIncrement() ),
					0 );
			if ( t.isDaemon() )
				t.setDaemon( false );
			if ( t.getPriority() != Thread.NORM_PRIORITY )
				t.setPriority( Thread.NORM_PRIORITY );
			return t;
		}
	}

	public TransformAwareBufferedImageOverlayRendererFX renderTarget()
	{
		return this.renderTarget;
	}

	@Override
	public void drawOverlays( final GraphicsContext g )
	{
		boolean requiresRepaint = false;
		if ( this.showMultibox.get() )
		{
			multiBoxOverlayRenderer.setViewerState( state );
			multiBoxOverlayRenderer.updateVirtualScreenSize( ( int ) display.getWidth(), ( int ) display.getHeight() );
			multiBoxOverlayRenderer.paint( g );
			requiresRepaint = multiBoxOverlayRenderer.isHighlightInProgress();
		}

		if ( requiresRepaint )
			display.requestLayout();
	}

	public void showMultibox( final boolean show )
	{
		this.showMultibox.set( show );
	}

	public boolean isMouseInside()
	{
		return this.isInside.get();
	}

	public void showTimeSlider( final boolean show )
	{
		this.showSlider.set( show );
	}

}
