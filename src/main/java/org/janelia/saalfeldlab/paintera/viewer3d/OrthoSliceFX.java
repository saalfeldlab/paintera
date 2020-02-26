package org.janelia.saalfeldlab.paintera.viewer3d;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.render.BufferExposingWritableImage;
import bdv.fx.viewer.render.RenderUnit;
import com.sun.javafx.image.PixelUtils;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.scene.image.*;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import net.imglib2.*;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.fx.ObservableWithListenersList;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.util.Colors;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.janelia.saalfeldlab.util.concurrent.PriorityLatestTaskExecutor;
import org.janelia.saalfeldlab.util.fx.Transforms;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OrthoSliceFX extends ObservableWithListenersList
{
	private static class Texture
	{
		final BufferExposingWritableImage originalImage;
		final BufferExposingWritableImage selfIlluminationMapImage;
		final BufferExposingWritableImage diffuseMapImage;

		Texture(final int width, final int height)
		{
			try {
				originalImage = new BufferExposingWritableImage(width, height);
				selfIlluminationMapImage = new BufferExposingWritableImage(width, height);
				diffuseMapImage = new BufferExposingWritableImage(width, height);
			} catch (final NoSuchMethodException | NoSuchFieldException | IllegalAccessException | InvocationTargetException e) {
				throw new RuntimeException(e);
			}
		}
	}

	// this delay is used to avoid blinking when switching between different resolutions of the texture
	private static final long textureUpdateDelayNanoSec = 1000000 * 50; // 50 msec

	private final ViewerPanelFX viewer;

	private final AffineTransform3D worldTransform = new AffineTransform3D();

	private final Affine worldTransformFX = new Affine();

	private final ObjectProperty<OrthoSliceMeshFX> orthoslicesMesh = new SimpleObjectProperty<>();

	private final PriorityLatestTaskExecutor delayedTextureUpdateExecutor = new PriorityLatestTaskExecutor(textureUpdateDelayNanoSec, new NamedThreadFactory("texture-update-thread-%d", true));

	/**
	 * List of texture images for each scale level.
	 * In each pair the first image is fully opaque, and the second is with modified alpha channel which is displayed on the screen.
	 * When the user changes the opacity value, the texture is copied from the first image into the second, and the new proper alpha value is set.
	 */
	private final List<Texture> textures = new ArrayList<>();

	private int currentTextureScreenScaleIndex = -1;

	private double[] screenScales;

	private long[] dimensions;

	private final BooleanProperty isVisible = new SimpleBooleanProperty(false);

	private final DoubleProperty opacity = new SimpleDoubleProperty(1.0);

	private final DoubleProperty shading = new SimpleDoubleProperty(0.1);

	public OrthoSliceFX(final ViewerPanelFX viewer)
	{
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
			if (currentTextureScreenScaleIndex != -1) {
				final Texture texture = textures.get(currentTextureScreenScaleIndex);
				final Interval interval = new FinalInterval(
						(long) texture.originalImage.getWidth(),
						(long) texture.originalImage.getHeight());
				setTextureOpacityAndShading(texture, interval);
			}
		};

		this.opacity.addListener(textureColorUpdateListener);
		this.shading.addListener(textureColorUpdateListener);
	}

	public OrthoSliceMeshFX getMesh()
	{
		return orthoslicesMesh.get();
	}

	public long[] getDimensions()
	{
		return dimensions;
	}

	public AffineTransform3D getWorldTransform()
	{
		return worldTransform;
	}

	private void updateScreenScales(final double[] screenScales)
	{
		this.screenScales = screenScales.clone();
		delayedTextureUpdateExecutor.cancel();

		textures.clear();
		for (int i = 0; i < screenScales.length; ++i)
			textures.add(null);
	}

	private void updateTexture(final RenderUnit.RenderResult newv)
	{
		if (newv.getImage() == null || newv.getScreenScaleIndex() == -1)
			return;

		// FIXME: there is a race condition that sometimes may cause an ArrayIndexOutOfBounds exception:
		// Screen scales are first initialized with the default setting (see RenderUnit),
		// then the project metadata is loaded, and the screen scales are changed to the saved configuration.
		// If the project screen scales are [1.0], sometimes the renderer receives a request to re-render the screen at screen scale 1, which results in the exception.
		if (newv.getScreenScaleIndex() >= textures.size())
			return;

		final int[] textureImageSize = {(int) newv.getImage().getWidth(), (int) newv.getImage().getHeight()};
		final Texture texture = getTexture(newv.getScreenScaleIndex(), textureImageSize);

		final Interval roi = Intervals.intersect(
			Intervals.smallestContainingInterval(newv.getRenderTargetRealInterval()),
			new FinalInterval(new FinalDimensions(textureImageSize))
		);

		// copy relevant part of the rendered image into the first texture image
		final PixelReader pixelReader = newv.getImage().getPixelReader();
		final PixelWriter pixelWriter = texture.originalImage.getPixelWriter();
		pixelWriter.setPixels(
			(int) roi.min(0), // dst x
			(int) roi.min(1), // dst y
			(int) roi.dimension(0), // w
			(int) roi.dimension(1),	// h
			pixelReader, // src
			(int) roi.min(0), // src x
			(int) roi.min(1)  // src y
		);

		setTextureOpacityAndShading(texture, roi);

		// setup a task for setting the texture of the mesh
		final int newScreenScaleIndex = newv.getScreenScaleIndex();
		final Runnable updateTextureTask = () -> InvokeOnJavaFXApplicationThread.invoke(
			() -> {
				// calculate new texture coordinates depending on the ratio between the screen size and the rendered image
				final RealPoint texCoordMin = new RealPoint(2), texCoordMax = new RealPoint(2);
				for (int d = 0; d < 2; ++d)
					texCoordMax.setPosition(dimensions[d] / (textureImageSize[d] / screenScales[newScreenScaleIndex]), d);

				if (orthoslicesMesh.get() != null) {
					orthoslicesMesh.get().getMaterial().setSelfIlluminationMap(texture.selfIlluminationMapImage);
					orthoslicesMesh.get().getMaterial().setDiffuseMap(texture.diffuseMapImage);
					orthoslicesMesh.get().setTexCoords(texCoordMin, texCoordMax);
				}

				this.currentTextureScreenScaleIndex = newScreenScaleIndex;
			}
		);

		if (currentTextureScreenScaleIndex == newv.getScreenScaleIndex() || currentTextureScreenScaleIndex == -1)
		{
			// got a new texture at the same screen scale, set it immediately
			delayedTextureUpdateExecutor.cancel();
			updateTextureTask.run();
		}
		else
		{
			// the new texture has lower resolution than the current one, schedule setting the texture after a delay
			// (this is to avoid blinking because of constant switching between low-res and high-res)
			final int priority = -newv.getScreenScaleIndex();
			delayedTextureUpdateExecutor.schedule(updateTextureTask, priority);
		}
	}

	private void setTextureOpacityAndShading(final Texture texture, final Interval interval)
	{
		// NOTE: the opacity property of the MeshView object does not have any effect.
		// But the transparency can still be controlled by modifying the alpha channel in the texture images.

		final double alpha = this.opacity.get();
		final double shading = this.shading.get();
		final BufferExposingWritableImage[] targetImages = {texture.selfIlluminationMapImage, texture.diffuseMapImage};
		final double[] brightnessFactors = {1 - shading, shading};

		long elapsedMsec = System.currentTimeMillis();

		final RandomAccessibleInterval<ARGBType> src = Views.interval(texture.originalImage.asArrayImg(), interval);

		for (int i = 0; i < 2; ++i)
		{
			final BufferExposingWritableImage targetImage = targetImages[i];
			final double brightnessFactor = brightnessFactors[i];

			final RandomAccessibleInterval<ARGBType> dst = Views.interval(targetImage.asArrayImg(), interval);
			final Cursor<ARGBType> srcCursor = Views.flatIterable(src).cursor();
			final Cursor<ARGBType> dstCursor = Views.flatIterable(dst).cursor();
			while (srcCursor.hasNext())
			{
				final int srcArgb = srcCursor.next().get();
				final int dstArgb = ARGBType.rgba(
						ARGBType.red(srcArgb) * brightnessFactor,
						ARGBType.green(srcArgb) * brightnessFactor,
						ARGBType.blue(srcArgb) * brightnessFactor,
						alpha * 255);

				dstCursor.next().set(PixelUtils.NonPretoPre(dstArgb));
			}
		}

		Arrays.stream(targetImages).forEach(BufferExposingWritableImage::setPixelsDirty);

		elapsedMsec = System.currentTimeMillis() - elapsedMsec;
		System.out.println("Elapsed: " + elapsedMsec + " msec");
	}

	private Texture getTexture(final int screenScaleIndex, final int[] size)
	{
		Texture texture = textures.get(screenScaleIndex);
		final boolean create = texture == null || (int) texture.originalImage.getWidth() != size[0] || (int) texture.originalImage.getHeight() != size[1];
		if (create)
		{
			texture = new Texture(size[0], size[1]);
			textures.set(screenScaleIndex, texture);
		}
		return texture;
	}

	private void initializeMeshes()
	{
		delayedTextureUpdateExecutor.cancel();

		this.dimensions = this.viewer.getRenderUnit().getDimensions().clone();
		final OrthoSliceMeshFX mesh = new OrthoSliceMeshFX(dimensions, worldTransformFX);
		mesh.getMeshViews().forEach(meshView -> meshView.visibleProperty().bind(isVisible));
		this.orthoslicesMesh.set(mesh);
	}

	public BooleanProperty isVisibleProperty()
	{
		return this.isVisible;
	}

	public DoubleProperty opacityProperty()
	{
		return this.opacity;
	}

	public DoubleProperty shadingProperty()
	{
		return this.shading;
	}
}
