/*
 * #%L
 * ImgLib2: a general-purpose, multidimensional image processing library.
 * %%
 * Copyright (C) 2009 - 2016 Tobias Pietzsch, Stephan Preibisch, Stephan Saalfeld,
 * John Bogovic, Albert Cardona, Barry DeZonia, Christian Dietz, Jan Funke,
 * Aivar Grislis, Jonathan Hale, Grant Harris, Stefan Helfrich, Mark Hiner,
 * Martin Horn, Steffen Jaensch, Lee Kamentsky, Larry Lindsey, Melissa Linkert,
 * Mark Longair, Brian Northan, Nick Perry, Curtis Rueden, Johannes Schindelin,
 * Jean-Yves Tinevez and Michael Zinsmaier.
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

import java.awt.Component;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.JComponent;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import net.imglib2.ui.InteractiveDisplayCanvas;
import net.imglib2.ui.OverlayRenderer;
import net.imglib2.ui.TransformEventHandler;
import net.imglib2.ui.TransformEventHandlerFactory;
import net.imglib2.ui.TransformListener;

/**
 * A {@link JComponent} that is a {@link InteractiveDisplayCanvas}.
 *
 * @param <A>
 *            transform type
 *
 * @author Tobias Pietzsch
 */
public class InteractiveDisplayPaneComponent< A > extends StackPane implements InteractiveDisplayCanvasGeneric< A, GraphicsContext, Collection< InstallAndRemove > >
{

	private static final long serialVersionUID = -5546719724928785878L;

	/**
	 * Mouse/Keyboard handler that manipulates the view transformation.
	 */
	protected TransformEventHandler< A > handler;

	/**
	 * Listeners that we have to notify about view transformation changes.
	 */
	final protected CopyOnWriteArrayList< TransformListener< A > > transformListeners;

	/**
	 * The {@link OverlayRenderer} that draws on top of the current buffered
	 * image.
	 */
	final protected CopyOnWriteArrayList< OverlayRendererGeneric< GraphicsContext > > overlayRenderers;

	protected final ImageOverlayRendererFX renderTarget;

	private final CanvasPane canvasPane = new CanvasPane( 1, 1 );

	protected final ImageView imageView = new ImageView();
	{
		this.imageView.setPreserveRatio( false );
		this.imageView.setSmooth( false );
		this.imageView.fitWidthProperty().bind( this.widthProperty() );
		this.imageView.fitHeightProperty().bind( this.heightProperty() );
		this.getChildren().add( imageView );
		this.getChildren().add( canvasPane );
		this.setBackground( new Background( new BackgroundFill( Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY ) ) );
	}

//	private final Canvas canvas;

	/**
	 * Create a new {@link InteractiveDisplayCanvas} with initially no
	 * {@link OverlayRenderer OverlayRenderers} and no {@link TransformListener
	 * TransformListeners}. A {@link TransformEventHandler} is instantiated
	 * using the given factory, and registered for mouse and key events if it
	 * implements the appropriate interfaces ({@link MouseListener} etc.)
	 *
	 * @param width
	 *            preferred component width.
	 * @param height
	 *            preferred component height.
	 * @param transformEventHandlerFactory
	 *            factory to create a {@link TransformEventHandler} appropriate
	 *            for our transform type A.
	 */
	public InteractiveDisplayPaneComponent(
			final int width,
			final int height,
			final TransformEventHandlerFactory< A > transformEventHandlerFactory,
			final ImageOverlayRendererFX renderTarget )
	{
		super();
		setWidth( width );
		setHeight( height );

		this.overlayRenderers = new CopyOnWriteArrayList<>();
		this.transformListeners = new CopyOnWriteArrayList<>();
		this.renderTarget = renderTarget;

		final ChangeListener< Number > sizeChangeListener = ( ChangeListener< Number > ) ( observable, oldValue, newValue ) -> {
			final double wd = widthProperty().get();
			final double hd = heightProperty().get();
			final int w = ( int ) wd;
			final int h = ( int ) hd;
			if ( w <= 0 || h <= 0 )
				return;
			if ( handler != null )
				handler.setCanvasSize( w, h, true );
			overlayRenderers.forEach( or -> or.setCanvasSize( w, h ) );
			renderTarget.setCanvasSize( w, h );
		};

		widthProperty().addListener( sizeChangeListener );
		heightProperty().addListener( sizeChangeListener );

		handler = transformEventHandlerFactory.create( this );
		handler.setCanvasSize( width, height, false );
	}

	public void drawOverlays()
	{
		final Runnable r = () -> {
			final Canvas canvas = canvasPane.getCanvas();
			final GraphicsContext gc = canvas.getGraphicsContext2D();
			gc.clearRect( 0, 0, canvas.getWidth(), canvas.getHeight() );
			overlayRenderers.forEach( or -> or.drawOverlays( gc ) );
		};
		if ( !Platform.isFxApplicationThread() )
			Platform.runLater( r );
		else
			r.run();
	}

	/**
	 * Add an {@link OverlayRenderer} that draws on top of the current buffered
	 * image.
	 *
	 * @param renderer
	 *            overlay renderer to add.
	 */
	@Override
	public void addOverlayRenderer( final OverlayRendererGeneric< GraphicsContext > renderer )
	{
		overlayRenderers.add( renderer );
		renderer.setCanvasSize( ( int ) getWidth(), ( int ) getHeight() );
	}

	/**
	 * Remove an {@link OverlayRenderer}.
	 *
	 * @param renderer
	 *            overlay renderer to remove.
	 */
	@Override
	public void removeOverlayRenderer( final OverlayRendererGeneric< GraphicsContext > renderer )
	{
		overlayRenderers.remove( renderer );
	}

	/**
	 * Add a {@link TransformListener} to notify about view transformation
	 * changes.
	 *
	 * @param listener
	 *            the transform listener to add.
	 */
	@Override
	public void addTransformListener( final TransformListener< A > listener )
	{
		transformListeners.add( listener );
	}

	/**
	 * Remove a {@link TransformListener}.
	 *
	 * @param listener
	 *            the transform listener to remove.
	 */
	@Override
	public void removeTransformListener( final TransformListener< A > listener )
	{
		transformListeners.remove( listener );
	}

	/**
	 * Add new event handler. Depending on the interfaces implemented by
	 * <code>handler</code> calls {@link Component#addKeyListener(KeyListener)},
	 * {@link Component#addMouseListener(MouseListener)},
	 * {@link Component#addMouseMotionListener(MouseMotionListener)},
	 * {@link Component#addMouseWheelListener(MouseWheelListener)}.
	 *
	 * @param h
	 *            handler to remove
	 */
	@Override
	public void addHandler( final Collection< InstallAndRemove > h )
	{
		h.forEach( i -> i.installInto( this ) );
	}

	/**
	 * Remove an event handler. Add new event handler. Depending on the
	 * interfaces implemented by <code>handler</code> calls
	 * {@link Component#removeKeyListener(KeyListener)},
	 * {@link Component#removeMouseListener(MouseListener)},
	 * {@link Component#removeMouseMotionListener(MouseMotionListener)},
	 * {@link Component#removeMouseWheelListener(MouseWheelListener)}.
	 *
	 * @param h
	 *            handler to remove
	 */
	@Override
	public void removeHandler( final Collection< InstallAndRemove > h )
	{
		h.forEach( i -> i.removeFrom( this ) );
	}

	/**
	 * Get the {@link TransformEventHandler} that handles mouse and key events
	 * to update our view transform.
	 *
	 * @return handles mouse and key events to update the view transform.
	 */
	@Override
	public TransformEventHandler< A > getTransformEventHandler()
	{
		return handler;
	}

	/**
	 * Set the {@link TransformEventHandler} that handles mouse and key events
	 * to update our view transform.
	 *
	 * @param transformEventHandler
	 *            handler to use
	 */
	@Override
	public synchronized void setTransformEventHandler( final TransformEventHandler< A > transformEventHandler )
	{
		handler = transformEventHandler;
		handler.setCanvasSize( ( int ) getWidth(), ( int ) getHeight(), false );
	}

	public void repaint()
	{
		this.renderTarget.drawOverlays( this.imageView );
		drawOverlays();
		layout();
	}

	/**
	 * This is called by our {@link #getTransformEventHandler() transform event
	 * handler} when the transform is changed. In turn, we notify all our
	 * {@link TransformListener TransformListeners} that the view transform has
	 * changed.
	 */
	@Override
	public void transformChanged( final A transform )
	{
		for ( final TransformListener< A > l : transformListeners )
			l.transformChanged( transform );
	}

	public void addImageChangeListener( final ChangeListener< Image > listener )
	{
		this.imageView.imageProperty().addListener( listener );
	}

	public void removeImageChangeListener( final ChangeListener< Image > listener )
	{
		this.imageView.imageProperty().removeListener( listener );
	}
}
