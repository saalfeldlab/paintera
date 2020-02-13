package org.janelia.saalfeldlab.paintera.viewer3d;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.render.RenderUnit;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import org.janelia.saalfeldlab.fx.ObservableWithListenersList;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.janelia.saalfeldlab.util.concurrent.PriorityLatestTaskExecutor;
import org.janelia.saalfeldlab.util.fx.Transforms;

import java.util.ArrayList;
import java.util.List;

public class OrthoSliceFX extends ObservableWithListenersList
{
	private static class Texture
	{
		WritableImage originalImage;
		WritableImage selfIlluminationMapImage;
		WritableImage diffuseMapImage;

		Texture(final int width, final int height)
		{
			originalImage = new WritableImage(width, height);
			selfIlluminationMapImage = new WritableImage(width, height);
			diffuseMapImage = new WritableImage(width, height);
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
			if (currentTextureScreenScaleIndex != -1)
				setTextureOpacityAndShading(textures.get(currentTextureScreenScaleIndex));
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

		setTextureOpacityAndShading(texture);

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

	private void setTextureOpacityAndShading(final Texture texture)
	{
		// NOTE: the opacity property of the MeshView object does not have any effect.
		// But the transparency can still be controlled by modifying the alpha channel in the texture images.

		final double alpha = this.opacity.get();
		final double shading = this.shading.get();
		final WritableImage[] targetImages = {texture.selfIlluminationMapImage, texture.diffuseMapImage};
		final double[] brightnessFactor = {1 - shading, shading};
		for (int i = 0; i < 2; ++i) {
			final WritableImage targetImage = targetImages[i];
			final PixelReader pixelReader = texture.originalImage.getPixelReader();
			final PixelWriter pixelWriter = targetImage.getPixelWriter();
			for (int x = 0; x < (int) targetImage.getWidth(); ++x) {
				for (int y = 0; y < (int) targetImage.getHeight(); ++y) {
					final Color c = pixelReader.getColor(x, y);
					pixelWriter.setColor(x, y, c.deriveColor(0, 1, brightnessFactor[i], alpha));
				}
			}
		}
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
