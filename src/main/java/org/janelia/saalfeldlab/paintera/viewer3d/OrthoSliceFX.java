package org.janelia.saalfeldlab.paintera.viewer3d;

import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX;
import org.janelia.saalfeldlab.bdv.fx.viewer.render.PixelBufferWritableImage;
import bdv.fx.viewer.render.ViewerRenderUnit;
import com.sun.javafx.image.PixelUtils;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.transform.Affine;
import net.imglib2.Cursor;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.fx.ObservableWithListenersList;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.janelia.saalfeldlab.util.concurrent.PriorityLatestTaskExecutor;
import org.janelia.saalfeldlab.util.fx.Transforms;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class OrthoSliceFX extends ObservableWithListenersList {

	private static final ExecutorService TEXTURE_UPDATOR = Executors.newCachedThreadPool(new ThreadFactory() {

		private final AtomicInteger count = new AtomicInteger(0);

		@Override public Thread newThread(Runnable r) {

			Thread thread = new Thread(r, "updateTexture-" + count.getAndIncrement());
			thread.setDaemon(true);
			return thread;
		}
	});

	private static class Texture {

		final PixelBufferWritableImage originalImage;
		final PixelBufferWritableImage selfIlluminationMapImage;
		final PixelBufferWritableImage diffuseMapImage;

		Texture(final int width, final int height) {

			originalImage = PixelBufferWritableImage.newImage(width, height);
			selfIlluminationMapImage = PixelBufferWritableImage.newImage(width, height);
			diffuseMapImage = PixelBufferWritableImage.newImage(width, height);
		}
	}

	// this delay is used to avoid blinking when switching between different resolutions of the texture
	private static final long textureUpdateDelayNanoSec = 1000000 * 50; // 50 msec

	private final ViewerPanelFX viewer;

	private final AffineTransform3D worldTransform = new AffineTransform3D();

	private final Affine worldTransformFX = new Affine();

	private final ObjectProperty<OrthoSliceMeshFX> orthoslicesMesh = new SimpleObjectProperty<>();

	private final PriorityLatestTaskExecutor delayedTextureUpdateExecutor = new PriorityLatestTaskExecutor(textureUpdateDelayNanoSec,
			new NamedThreadFactory("texture-update-thread-%d", true));

	private int currentTextureScreenScaleIndex = -1;

	private double[] screenScales;

	private Texture[] textures;

	private Interval[] updateIntervals;

	private long[] dimensions;

	private final BooleanProperty isVisible = new SimpleBooleanProperty(false);

	private final DoubleProperty opacity = new SimpleDoubleProperty(1.0);

	private final DoubleProperty shading = new SimpleDoubleProperty(0.1);

	public OrthoSliceFX(final ViewerPanelFX viewer) {

		this.viewer = viewer;

		this.viewer.addTransformListener(displayTransform -> {
			worldTransform.set(displayTransform.inverse());
			worldTransformFX.setToTransform(Transforms.toTransformFX(worldTransform));
			stateChanged();
		});

		this.viewer.getRenderUnit().addUpdateListener(this::initializeMeshes);
		this.viewer.getRenderUnit().getRenderedImageProperty().addListener((obs, oldVal, newVal) -> updateTexture(newVal));
		this.viewer.getRenderUnit().getScreenScalesProperty().addListener((obs, oldVal, newVal) -> updateScreenScales(newVal));

		orthoslicesMesh.addListener(obs -> stateChanged());
		isVisible.addListener(obs -> stateChanged());

		final InvalidationListener textureColorUpdateListener = obs -> {
			// Change textures for all scale levels.
			// If painting is initiated after this, partial updates will be consistent with the rest of the texture image.
			if (textures == null)
				return;
			delayedTextureUpdateExecutor.cancel();
			for (final Texture texture : textures) {
				if (texture != null) {
					final Interval interval = new FinalInterval(
							(long)texture.originalImage.getWidth(),
							(long)texture.originalImage.getHeight());
					setTextureOpacityAndShading(texture, interval);
				}
			}
		};

		this.opacity.addListener(textureColorUpdateListener);
		this.shading.addListener(textureColorUpdateListener);
	}

	public OrthoSliceMeshFX getMesh() {

		return orthoslicesMesh.get();
	}

	public long[] getDimensions() {

		return dimensions;
	}

	public AffineTransform3D getWorldTransform() {

		return worldTransform;
	}

	private void updateScreenScales(final double[] screenScales) {

		this.screenScales = screenScales.clone();
		reset();
	}

	private void reset() {

		delayedTextureUpdateExecutor.cancel();
		if (screenScales != null) {
			textures = new Texture[screenScales.length];
			updateIntervals = new Interval[screenScales.length];
		}
	}

	private void updateTexture(final ViewerRenderUnit.RenderResult newv) {

		if (newv.getImage() == null || newv.getScreenScaleIndex() == -1 || textures == null)
			return;

		// FIXME: there is a race condition that sometimes may cause an ArrayIndexOutOfBounds exception:
		// 	Screen scales are first initialized with the default setting (see ViewerRenderUnit),
		// 	then the project metadata is loaded, and the screen scales are changed to the saved configuration.
		// 	If the project screen scales are [1.0], sometimes the renderer receives a request to re-render the screen at screen scale 1, which results in the exception.
		if (newv.getScreenScaleIndex() >= textures.length)
			return;

		final int[] textureImageSize = {(int)newv.getImage().getWidth(), (int)newv.getImage().getHeight()};
		final Texture texture = getTexture(newv.getScreenScaleIndex(), textureImageSize);

		final Interval interval = Intervals.intersect(
				Intervals.smallestContainingInterval(newv.getRenderTargetRealInterval()),
				new FinalInterval(new FinalDimensions(textureImageSize))
		);

		// copy relevant part of the rendered image into the first texture image
		final PixelReader pixelReader = newv.getImage().getPixelReader();
		int width = (int)interval.dimension(0);
		int height = (int)interval.dimension(1);
		int xOff = (int)interval.min(0);
		int yOff = (int)interval.min(1);

		texture.originalImage.getPixelBuffer().getBuffer().position((int)(yOff * texture.originalImage.getWidth() + xOff));
		pixelReader.getPixels(
				xOff,
				yOff,
				width,
				height,
				PixelFormat.getIntArgbPreInstance(),
				texture.originalImage.getBuffer(),
				(int)texture.originalImage.getWidth()
		);

		if (updateIntervals[newv.getScreenScaleIndex()] == null)
			updateIntervals[newv.getScreenScaleIndex()] = interval;
		else
			updateIntervals[newv.getScreenScaleIndex()] = Intervals.union(interval, updateIntervals[newv.getScreenScaleIndex()]);

		// set up a task for updating the texture of the mesh
		final int newScreenScaleIndex = newv.getScreenScaleIndex();
		final Runnable updateTextureTask = () -> InvokeOnJavaFXApplicationThread.invoke(
				() -> {
					if (newScreenScaleIndex >= textures.length || textures[newScreenScaleIndex] != texture)
						return;

					final Interval textureImageInterval = new FinalInterval(textureImageSize[0], textureImageSize[1]);
					final Interval updateInterval = updateIntervals[newScreenScaleIndex] != null
							? Intervals.intersect(updateIntervals[newScreenScaleIndex], textureImageInterval)
							: textureImageInterval;
					setTextureOpacityAndShading(texture, updateInterval);
					updateIntervals[newScreenScaleIndex] = null;

					// calculate new texture coordinates depending on the ratio between the screen size and the rendered image
					final RealPoint texCoordMin = new RealPoint(2), texCoordMax = new RealPoint(2);
					for (int d = 0; d < 2; ++d) {
						texCoordMax.setPosition(dimensions[d] / (textureImageSize[d] / screenScales[newScreenScaleIndex]), d);
					}

					if (orthoslicesMesh.get() != null) {
						orthoslicesMesh.get().getMaterial().setSelfIlluminationMap(texture.selfIlluminationMapImage);
						orthoslicesMesh.get().getMaterial().setDiffuseMap(texture.diffuseMapImage);
						orthoslicesMesh.get().setTexCoords(texCoordMin, texCoordMax);
					}

					this.currentTextureScreenScaleIndex = newScreenScaleIndex;
				}
		);

		// prioritize higher resolution texture to minimize blinking because of switching between low-res and high-res
		final int priority = -newv.getScreenScaleIndex();
		delayedTextureUpdateExecutor.schedule(updateTextureTask, priority);
	}

	private void setTextureOpacityAndShading(final Texture texture, final Interval interval) {
		// NOTE: the opacity property of the MeshView object does not have any effect.
		// But the transparency can still be controlled by modifying the alpha channel in the texture images.

		final double alpha = this.opacity.get();
		final double shading = this.shading.get();
		final PixelBufferWritableImage[] targetImages = {texture.selfIlluminationMapImage, texture.diffuseMapImage};
		final double[] brightnessFactors = {1 - shading, shading};

		final RandomAccessibleInterval<ARGBType> src = Views.interval(texture.originalImage.asArrayImg(), interval);
		final var futures = new ArrayList<Future<PixelBufferWritableImage>>();

		for (int j = 0; j < 2; ++j) {
			final int i = j;

			futures.add(TEXTURE_UPDATOR.submit(() -> {
				final PixelBufferWritableImage targetImage = targetImages[i];
				final double brightnessFactor = brightnessFactors[i];

				final RandomAccessibleInterval<ARGBType> dst = Views.interval(targetImage.asArrayImg(), interval);
				final Cursor<ARGBType> srcCursor = Views.flatIterable(src).cursor();
				final Cursor<ARGBType> dstCursor = Views.flatIterable(dst).cursor();

				while (dstCursor.hasNext()) {
					final int srcArgb = srcCursor.next().get();
					final int dstArgb = ARGBType.rgba(
							Util.round(ARGBType.red(srcArgb) * brightnessFactor),
							Util.round(ARGBType.green(srcArgb) * brightnessFactor),
							Util.round(ARGBType.blue(srcArgb) * brightnessFactor),
							Util.round(alpha * 255));
					dstCursor.next().set(PixelUtils.NonPretoPre(dstArgb));
				}
				return targetImage;
			}));
		}
		futures.forEach(future -> {
			try {
				future.get().setPixelsDirty();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException("Execption while setting Texture Opacity and Shading", e);
			}
		});
	}

	private Texture getTexture(final int screenScaleIndex, final int[] size) {

		Texture texture = textures[screenScaleIndex];
		final boolean create = texture == null || (int)texture.originalImage.getWidth() != size[0] || (int)texture.originalImage.getHeight() != size[1];
		if (create) {
			texture = new Texture(size[0], size[1]);
			textures[screenScaleIndex] = texture;
		}
		return texture;
	}

	private void initializeMeshes() {

		reset();
		this.dimensions = this.viewer.getRenderUnit().getDimensions().clone();
		final OrthoSliceMeshFX mesh = new OrthoSliceMeshFX(dimensions, worldTransformFX);
		mesh.getMeshViews().forEach(meshView -> meshView.visibleProperty().bind(isVisible));
		this.orthoslicesMesh.set(mesh);
	}

	public BooleanProperty isVisibleProperty() {

		return this.isVisible;
	}

	public DoubleProperty opacityProperty() {

		return this.opacity;
	}

	public DoubleProperty shadingProperty() {

		return this.shading;
	}
}
