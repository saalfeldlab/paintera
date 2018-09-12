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

import java.util.function.Consumer;

import javafx.scene.image.Image;
import net.imglib2.img.ImgView;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.ui.Renderer;

/**
 * {@link OverlayRendererGeneric} drawing an {@link ArrayImg}, scaled to fill an {@link ImgView}. It can be used as a
 * {@link RenderTargetGeneric}, such that the {@link ArrayImg} to draw is set by a {@link Renderer}.
 *
 * @author Tobias Pietzsch
 * @author Philipp Hanslovsky
 */
public class ImageOverlayRendererFX
		implements OverlayRendererGeneric<Consumer<Image>>, RenderTargetGeneric<BufferExposingWritableImage>
{

	protected BufferExposingWritableImage bufferedImage;

	/**
	 * An {@link ArrayImg} that has been previously set for painting. Whenever a
	 * new image is set, this is stored here and marked {@link #pending}. Whenever an image is painted and a new image
	 * is pending, the new image is painted to the screen. Before doing this, the image previously used for painting is
	 * swapped into {@link #pendingImage}. This is used for double-buffering.
	 */
	protected BufferExposingWritableImage pendingImage;

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
	 * Set the {@link ArrayImg} that is to be drawn on the canvas.
	 *
	 * @param img
	 * 		image to draw (may be null).
	 */
	@Override
	public synchronized BufferExposingWritableImage setBufferedImage(final BufferExposingWritableImage img)
	{
		final BufferExposingWritableImage tmp = pendingImage;
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
	public void drawOverlays(final Consumer<Image> g)
	{
		synchronized (this)
		{
			if (pending)
			{
				final BufferExposingWritableImage tmp = bufferedImage;
				bufferedImage = pendingImage;
				pendingImage = tmp;
				pending = false;
			}
		}
		if (bufferedImage != null)
		{
			g.accept(bufferedImage);
		}
	}

	@Override
	public void setCanvasSize(final int width, final int height)
	{
		this.width = width;
		this.height = height;
	}
}
