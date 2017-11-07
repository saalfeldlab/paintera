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

import java.awt.image.BufferedImage;

import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.ui.OverlayRenderer;
import net.imglib2.ui.RenderTarget;
import net.imglib2.ui.Renderer;
import net.imglib2.ui.util.Defaults;

/**
 * {@link OverlayRenderer} drawing a {@link BufferedImage}, scaled to fill the
 * canvas. It can be used as a {@link RenderTarget}, such that the
 * {@link BufferedImage} to draw is set by a {@link Renderer}.
 *
 * @author Tobias Pietzsch
 */
public class ImageOverlayRendererFX implements OverlayRendererGeneric< ImageView >, RenderTargetGeneric< ArrayImg< ARGBType, IntArray > >
{

	/**
	 * The {@link BufferedImage} that is actually drawn on the canvas. Depending
	 * on {@link Defaults#discardAlpha} this is either the {@link BufferedImage}
	 * obtained from screen image, or screen image's buffer re-wrapped using a
	 * RGB color model.
	 */
	protected ArrayImg< ARGBType, IntArray > bufferedImage;

	/**
	 * A {@link BufferedImage} that has been previously
	 * {@link #setBufferedImage(BufferedImage) set} for painting. Whenever a new
	 * image is set, this is stored here and marked {@link #pending}. Whenever
	 * an image is painted and a new image is pending, the new image is painted
	 * to the screen. Before doing this, the image previously used for painting
	 * is swapped into {@link #pendingImage}. This is used for double-buffering.
	 */
	protected ArrayImg< ARGBType, IntArray > pendingImage;

	/**
	 * Whether an image is pending.
	 */
	protected boolean pending;

	/**
	 * The current canvas width.
	 */
	protected volatile int width;

	/**
	 * The current canvas height.
	 */
	protected volatile int height;

	public ImageOverlayRendererFX()
	{
		bufferedImage = null;
		pendingImage = null;
		pending = false;
		width = 0;
		height = 0;
	}

	/**
	 * Set the {@link BufferedImage} that is to be drawn on the canvas.
	 *
	 * @param img
	 *            image to draw (may be null).
	 */
	@Override
	public synchronized ArrayImg< ARGBType, IntArray > setBufferedImage( final ArrayImg< ARGBType, IntArray > img )
	{
		final ArrayImg< ARGBType, IntArray > tmp = pendingImage;
		pendingImage = img;
		pending = true;
		return tmp;
	}

	@Override
	public int getWidth()
	{
		return width;
	}

	@Override
	public int getHeight()
	{
		return height;
	}

	@Override
	public void drawOverlays( final ImageView g )
	{
		synchronized ( this )
		{
			if ( pending )
			{
				final ArrayImg< ARGBType, IntArray > tmp = bufferedImage;
				bufferedImage = pendingImage;
				pendingImage = tmp;
				pending = false;
			}
		}
		if ( bufferedImage != null )
		{
			final int w = ( int ) bufferedImage.dimension( 0 );
			final int h = ( int ) bufferedImage.dimension( 1 );
			final WritableImage wimg = new WritableImage( w, h );
			wimg.getPixelWriter().setPixels( 0, 0, w, h, PixelFormat.getIntArgbInstance(), bufferedImage.update( null ).getCurrentStorageArray(), 0, w );
			g.setImage( wimg );
		}
	}

	@Override
	public void setCanvasSize( final int width, final int height )
	{
		this.width = width;
		this.height = height;
	}
}
