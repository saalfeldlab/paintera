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
package bdv.fx.viewer;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

import org.janelia.saalfeldlab.fx.event.InstallAndRemove;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import net.imglib2.ui.TransformListener;

/**
 * A {@link Pane} that acts like {@link InteractiveDisplayCanvasGeneric}.
 *
 * @param <A>
 *            transform type
 *
 * @author Tobias Pietzsch
 * @author Philipp Hanslovsky
 */
public class InteractiveDisplayPaneComponentCanvas< A > extends StackPane
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final long serialVersionUID = -5546719724928785878L;

	/**
	 * The {@link OverlayRendererGeneric} that draws on top of the current
	 * buffered image.
	 */
	final protected CopyOnWriteArrayList< OverlayRendererGeneric< GraphicsContext > > overlayRenderers;

	protected final ImageOverlayRendererFX renderTarget;

	private final ResizableCanvas canvas = new ResizableCanvas();

	private final CanvasPane canvasPane = new CanvasPane( 1, 1 );

	private final ObjectProperty< Image > currentImage = new SimpleObjectProperty<>();

	{
		this.canvas.widthProperty().bind( this.widthProperty() );
		this.canvas.heightProperty().bind( this.heightProperty() );
		this.getChildren().add( canvas );
		this.getChildren().add( canvasPane );
		this.setBackground( new Background( new BackgroundFill( Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY ) ) );
	}

//	private final Canvas canvas;

	/**
	 * Create a new {@link InteractiveDisplayCanvasGeneric} with initially no
	 * {@link OverlayRendererGeneric OverlayRenderers} and no
	 * {@link TransformListener TransformListeners}.
	 *
	 * @param width
	 *            preferred component width.
	 * @param height
	 *            preferred component height.
	 * @param ImageOverlayRendererFX
	 */
	public InteractiveDisplayPaneComponentCanvas(
			final int width,
			final int height,
			final ImageOverlayRendererFX renderTarget )
	{
		super();
		setWidth( width );
		setHeight( height );

		this.overlayRenderers = new CopyOnWriteArrayList<>();
		this.renderTarget = renderTarget;

		final ChangeListener< Number > sizeChangeListener = ( ChangeListener< Number > ) ( observable, oldValue, newValue ) -> {
			final double wd = widthProperty().get();
			final double hd = heightProperty().get();
			final int w = ( int ) wd;
			final int h = ( int ) hd;
			if ( w <= 0 || h <= 0 )
				return;
			overlayRenderers.forEach( or -> or.setCanvasSize( w, h ) );
			renderTarget.setCanvasSize( w, h );
		};

		widthProperty().addListener( sizeChangeListener );
		heightProperty().addListener( sizeChangeListener );

	}

	public void drawOverlays()
	{
		final Runnable r = () -> {
			final Canvas canvas = this.canvasPane.getCanvas();
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
	 * Add an {@link OverlayRendererGeneric} that draws on top of the current
	 * buffered image.
	 *
	 * @param renderer
	 *            overlay renderer to add.
	 */
	public void addOverlayRenderer( final OverlayRendererGeneric< GraphicsContext > renderer )
	{
		overlayRenderers.add( renderer );
		renderer.setCanvasSize( ( int ) getWidth(), ( int ) getHeight() );
	}

	/**
	 * Remove an {@link OverlayRendererGeneric}.
	 *
	 * @param renderer
	 *            overlay renderer to remove.
	 */
	public void removeOverlayRenderer( final OverlayRendererGeneric< GraphicsContext > renderer )
	{
		overlayRenderers.remove( renderer );
	}

	/**
	 * Add handler that installs itself into the pane.
	 *
	 * @param h
	 *            handler to remove
	 */
	public void addHandler( final Collection< InstallAndRemove< Node > > h )
	{
		h.forEach( i -> i.installInto( this ) );
	}

	/**
	 * Add handler that removes itself from the pane.
	 *
	 * @param h
	 *            handler to remove
	 */
	public void removeHandler( final Collection< InstallAndRemove< Node > > h )
	{
		h.forEach( i -> i.removeFrom( this ) );
	}

	public void repaint()
	{
		this.renderTarget.drawOverlays( img -> {
			this.currentImage.set( img );
			imageToCanvas( img, canvas );
		} );
		drawOverlays();
		layout();
	}

	public void addImageChangeListener( final ChangeListener< Image > listener )
	{
		this.currentImage.addListener( listener );
	}

	public void removeImageChangeListener( final ChangeListener< Image > listener )
	{
		this.currentImage.removeListener( listener );
	}

	private static void imageToCanvas( final Image img, final Canvas canvas )
	{
		if ( img == null )
		{
			LOG.debug( "Got null-pointer image, returning" );
			return;
		}
		final double w = canvas.getWidth();
		final double h = canvas.getHeight();
		LOG.debug( "Drawing img {} into canvas of size ({}x{})", img, w, h );
		final GraphicsContext gc = canvas.getGraphicsContext2D();
		gc.clearRect( 0, 0, w, h );
		gc.drawImage( img, 0, 0, w, h );
	}
}
