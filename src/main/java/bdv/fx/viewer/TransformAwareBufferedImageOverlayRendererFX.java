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
package bdv.fx.viewer;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javafx.scene.image.Image;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransformAwareBufferedImageOverlayRendererFX
		extends ImageOverlayRendererFX
		implements TransformAwareBufferedImageOverlayRendererGeneric<Consumer<Image>, BufferExposingWritableImage>
{

	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	protected AffineTransform3D pendingTransform;

	protected AffineTransform3D paintedTransform;

	/**
	 * These listeners will be notified about the transform that is associated to the currently rendered image. This is
	 * intended for example for {@link OverlayRendererGeneric OverlayRendererGenerics} that need to exactly match the
	 * transform of their overlaid content to the transform of the image.
	 */
	protected final CopyOnWriteArrayList<TransformListener<AffineTransform3D>> paintedTransformListeners;

	public TransformAwareBufferedImageOverlayRendererFX()
	{
		super();
		pendingTransform = new AffineTransform3D();
		paintedTransform = new AffineTransform3D();
		paintedTransformListeners = new CopyOnWriteArrayList<>();
	}

	@Override
	public synchronized BufferExposingWritableImage setBufferedImageAndTransform(final BufferExposingWritableImage
			                                                                                 img, final
	AffineTransform3D transform)
	{
		pendingTransform.set(transform);
		return super.setBufferedImage(img);
	}

	@Override
	public void drawOverlays(final Consumer<Image> g)
	{
		boolean notifyTransformListeners = false;
		synchronized (this)
		{
			if (pending)
			{
				final BufferExposingWritableImage tmp = bufferedImage;
				bufferedImage = pendingImage;
				paintedTransform.set(pendingTransform);
				pendingImage = tmp;
				pending = false;
				notifyTransformListeners = true;
			}
		}
		final BufferExposingWritableImage sourceImage = this.bufferedImage;
		if (sourceImage != null)
		{
			final boolean notify = notifyTransformListeners;
			InvokeOnJavaFXApplicationThread.invoke(() -> {

				LOG.debug("Setting image to {}", sourceImage);
				g.accept(null);
				sourceImage.setPixelsDirty();
				g.accept(sourceImage);
				// TODO add countdown latch to wait for setImage to return
				// before
				// notifying listeners
				if (notify)
					for (final TransformListener<AffineTransform3D> listener : paintedTransformListeners)
						listener.transformChanged(paintedTransform);
			});
			//			LOG.debug( String.format( "g.drawImage() :%4d ms", watch.nanoTime() / 1000000 ) );
		}
	}

	/**
	 * Add a {@link TransformListener} to notify about viewer transformation changes. Listeners will be notified when a
	 * new image has been rendered (immediately before that image is displayed) with the viewer transform used to
	 * render
	 * that image.
	 *
	 * @param listener
	 * 		the transform listener to add.
	 */
	@Override
	public void addTransformListener(final TransformListener<AffineTransform3D> listener)
	{
		addTransformListener(listener, Integer.MAX_VALUE);
	}

	/**
	 * Add a {@link TransformListener} to notify about viewer transformation changes. Listeners will be notified when a
	 * new image has been rendered (immediately before that image is displayed) with the viewer transform used to
	 * render
	 * that image.
	 *
	 * @param listener
	 * 		the transform listener to add.
	 * @param index
	 * 		position in the list of listeners at which to insert this one.
	 */
	@Override
	public void addTransformListener(final TransformListener<AffineTransform3D> listener, final int index)
	{
		synchronized (paintedTransformListeners)
		{
			final int s = paintedTransformListeners.size();
			paintedTransformListeners.add(index < 0 ? 0 : index > s ? s : index, listener);
			listener.transformChanged(paintedTransform);
		}
	}

	/**
	 * Remove a {@link TransformListener}.
	 *
	 * @param listener
	 * 		the transform listener to remove.
	 */
	@Override
	public void removeTransformListener(final TransformListener<AffineTransform3D> listener)
	{
		synchronized (paintedTransformListeners)
		{
			paintedTransformListeners.remove(listener);
		}
	}

	/**
	 * DON'T USE THIS.
	 * <p>
	 * This is a work around for JDK bug https://bugs.openjdk.java.net/browse/JDK-8029147 which leads to ViewerPanel
	 * not
	 * being garbage-collected when ViewerFrame is closed. So instead we need to manually let go of resources...
	 */
	void kill()
	{
		bufferedImage = null;
		pendingImage = null;
	}
}
