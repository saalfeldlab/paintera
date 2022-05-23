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
package bdv.fx.viewer.render;

import javafx.scene.image.Image;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.ui.TransformListener;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class TransformAwareBufferedImageOverlayRendererFX extends ImageOverlayRendererFX
		implements TransformAwareBufferedImageOverlayRendererGeneric<Consumer<Image>, PixelBufferWritableImage> {

  private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final int FULL_OPACITY = 0xff << 24;

  protected AffineTransform3D pendingTransform;

  protected AffineTransform3D paintedTransform;

  /**
   * These listeners will be notified about the transform that is associated to the currently rendered image. This is
   * intended for example for {@link OverlayRendererGeneric OverlayRendererGenerics} that need to exactly match the
   * transform of their overlaid content to the transform of the image.
   */
  protected final CopyOnWriteArrayList<TransformListener<AffineTransform3D>> paintedTransformListeners;

  public TransformAwareBufferedImageOverlayRendererFX() {

	super();
	pendingTransform = new AffineTransform3D();
	paintedTransform = new AffineTransform3D();
	paintedTransformListeners = new CopyOnWriteArrayList<>();
  }

  @Override
  public synchronized PixelBufferWritableImage setBufferedImageAndTransform(
		  final PixelBufferWritableImage img,
		  final AffineTransform3D transform) {

	pendingTransform.set(transform);
	return super.setBufferedImage(img);
  }

  @Override
  public void drawOverlays(final Consumer<Image> g) {

	boolean notifyTransformListeners = false;
	synchronized (this) {
	  if (pending) {
		final PixelBufferWritableImage tmp = bufferedImage;
		bufferedImage = pendingImage;
		paintedTransform.set(pendingTransform);
		pendingImage = tmp;
		pending = false;
		notifyTransformListeners = true;
	  }
	}
	final PixelBufferWritableImage sourceImage = this.bufferedImage;
	if (sourceImage != null) {
	  final boolean notify = notifyTransformListeners;
	  InvokeOnJavaFXApplicationThread.invoke(() -> {

		LOG.trace("Setting image to {}", sourceImage);
		g.accept(null);

		/*
		 * NOTE: need to make the final image fully opaque to ensure that it is properly drawn on the screen later on.
		 * It is only necessary because existing BlendMode options in JavaFX always perform some kind of blending with old contents of the displayed component.
		 *
		 * Ideally, we would use BlendMode.SRC if it was available (that would overwrite the destination with the source regardless of alpha).
		 * See discussion about possibility of adding this mode in the future versions and more information here:
		 *
		 * https://bugs.openjdk.java.net/browse/JDK-8092156
		 *
		 * https://books.google.com/books?id=UxM2iXiFqbMC&pg=PA97&lpg=PA97&dq=Porter-Duff+%22source+over+destination%22&source=bl&ots=hYj5U2X5Lk&sig=ACfU3U095X67T4FghqCpgYrAhEWbNtd_xQ&hl=en&sa=X&ved=2ahUKEwjyg4rRufXfAhXvuFkKHfK8BysQ6AEwAnoECAcQAQ#v=snippet&q=%22overwrites%20the%20destination%20with%20the%20source%2C%20regardless%20of%20alpha%22&f=false
		 *
		 * https://docs.oracle.com/javase/8/javafx/api/javafx/scene/effect/BlendMode.html
		 */
		for (final ARGBType px : sourceImage.asArrayImg()) {
		  px.set(px.get() | FULL_OPACITY);
		}

		sourceImage.setPixelsDirty();
		g.accept(sourceImage);
		// TODO add countdown latch to wait for setImage to return before notifying listeners
		if (notify)
		  for (final TransformListener<AffineTransform3D> listener : paintedTransformListeners) {
			listener.transformChanged(paintedTransform);
		  }
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
   * @param listener the transform listener to add.
   */
  @Override
  public void addTransformListener(final TransformListener<AffineTransform3D> listener) {

	addTransformListener(listener, Integer.MAX_VALUE);
  }

  /**
   * Add a {@link TransformListener} to notify about viewer transformation changes. Listeners will be notified when a
   * new image has been rendered (immediately before that image is displayed) with the viewer transform used to
   * render
   * that image.
   *
   * @param listener the transform listener to add.
   * @param index    position in the list of listeners at which to insert this one.
   */
  @Override
  public void addTransformListener(final TransformListener<AffineTransform3D> listener, final int index) {

	synchronized (paintedTransformListeners) {
	  final int s = paintedTransformListeners.size();
	  paintedTransformListeners.add(index < 0 ? 0 : index > s ? s : index, listener);
	  listener.transformChanged(paintedTransform);
	}
  }

  /**
   * Remove a {@link TransformListener}.
   *
   * @param listener the transform listener to remove.
   */
  @Override
  public void removeTransformListener(final TransformListener<AffineTransform3D> listener) {

	synchronized (paintedTransformListeners) {
	  paintedTransformListeners.remove(listener);
	}
  }
}
