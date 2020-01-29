package org.janelia.saalfeldlab.paintera.viewer3d;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.render.RenderUnit;
import javafx.beans.property.*;
import javafx.scene.Group;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Affine;
import net.imglib2.*;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.janelia.saalfeldlab.util.concurrent.PriorityLatestTaskExecutor;
import org.janelia.saalfeldlab.util.fx.Transforms;

import java.util.ArrayList;
import java.util.List;

public class OrthoSliceFX
{
	// this delay is used to avoid blinking when switching between different resolutions of the texture
	private static final long textureUpdateDelayNanoSec = 1000000 * 50; // 50 msec

	private static final Color textureDiffuseOpaqueColor = new Color(0.1, 0.1, 0.1, 1.0);

	private final Group scene;

	private final ViewerPanelFX viewer;

	private final Group meshesGroup = new Group();

	private final PriorityLatestTaskExecutor delayedTextureUpdateExecutor = new PriorityLatestTaskExecutor(textureUpdateDelayNanoSec, new NamedThreadFactory("texture-update-thread-%d", true));

	/**
	 * List of texture images for each scale level.
	 * In each pair the first image is fully opaque, and the second is with modified alpha channel which is displayed on the screen.
	 * When the user changes the opacity value, the texture is copied from the first image into the second, and the new proper alpha value is set.
	 */
	private final List<Pair<WritableImage, WritableImage>> textures = new ArrayList<>();

	private int currentTextureScreenScaleIndex = -1;

	private double[] screenScales;
	private long[] dimensions;

	private final ObjectProperty<MeshView> meshView = new SimpleObjectProperty<>();
	{
		meshView.addListener((obs, oldv, newv) -> InvokeOnJavaFXApplicationThread.invoke(() -> meshesGroup.getChildren().setAll(newv)));
	}

	private final BooleanProperty isVisible = new SimpleBooleanProperty(false);
	{
		this.isVisible.addListener((oldv, obs, newv) -> {
			synchronized (this)
			{
				if (newv)
				{
					InvokeOnJavaFXApplicationThread.invoke(() -> this.getScene().getChildren().add(meshesGroup));
				}
				else
				{
					InvokeOnJavaFXApplicationThread.invoke(() -> this.getScene().getChildren().remove(meshesGroup));
				}
			}
		});
	}

	private final DoubleProperty opacity = new SimpleDoubleProperty(1.0);
	{
		this.opacity.addListener((obs, oldv, newv) -> {
			((PhongMaterial) this.meshView.get().getMaterial()).setDiffuseColor(new Color(
					textureDiffuseOpaqueColor.getRed(),
					textureDiffuseOpaqueColor.getGreen(),
					textureDiffuseOpaqueColor.getBlue(),
					newv.doubleValue()));

			if (currentTextureScreenScaleIndex != -1)
				setTextureAlpha(textures.get(currentTextureScreenScaleIndex), newv.doubleValue());
		});
	}

	public OrthoSliceFX(final Group scene, final ViewerPanelFX viewer)
	{
		this.scene = scene;
		this.viewer = viewer;

		final Affine viewerTransform = new Affine();
		this.meshesGroup.getTransforms().setAll(viewerTransform);

		this.viewer.addTransformListener(tf -> {
			final Affine newTransform = Transforms.toTransformFX(tf.inverse());
			InvokeOnJavaFXApplicationThread.invoke(() -> viewerTransform.setToTransform(newTransform));
		});

		this.viewer.getRenderUnit().addUpdateListener(() -> InvokeOnJavaFXApplicationThread.invoke(this::initializeMeshes));
		this.viewer.getRenderUnit().getRenderedImageProperty().addListener((obs, oldVal, newVal) -> updateTexture(newVal));
		this.viewer.getRenderUnit().getScreenScalesProperty().addListener((obs, oldVal, newVal) -> updateScreenScales(newVal));
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
		final Pair<WritableImage, WritableImage> textureImagePair = getTextureImagePair(newv.getScreenScaleIndex(), textureImageSize);

		final Interval roi = Intervals.intersect(
			Intervals.smallestContainingInterval(newv.getRenderTargetRealInterval()),
			new FinalInterval(new FinalDimensions(textureImageSize))
		);

		// copy relevant part of the rendered image into the first texture image
		final PixelReader pixelReader = newv.getImage().getPixelReader();
		final PixelWriter pixelWriter = textureImagePair.getA().getPixelWriter();
		pixelWriter.setPixels(
			(int) roi.min(0), // dst x
			(int) roi.min(1), // dst y
			(int) roi.dimension(0), // w
			(int) roi.dimension(1),	// h
			pixelReader, // src
			(int) roi.min(0), // src x
			(int) roi.min(1)  // src y
		);

		// copy into the second texture image and set alpha channel
		setTextureAlpha(textureImagePair, this.opacity.get());

		// setup a task for setting the texture of the mesh
		final int newScreenScaleIndex = newv.getScreenScaleIndex();
		final Runnable updateTextureTask = () -> InvokeOnJavaFXApplicationThread.invoke(
			() -> {
				// calculate new texture coordinates depending on the ratio between the screen size and the rendered image
				final RealPoint texCoordMin = new RealPoint(2), texCoordMax = new RealPoint(2);
				for (int d = 0; d < 2; ++d)
					texCoordMax.setPosition(dimensions[d] / (textureImageSize[d] / screenScales[newScreenScaleIndex]), d);

				((PhongMaterial) this.meshView.get().getMaterial()).setSelfIlluminationMap(textureImagePair.getB());
				((OrthoSliceMeshFX) this.meshView.get().getMesh()).setTexCoords(texCoordMin, texCoordMax);

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

	private void setTextureAlpha(final Pair<WritableImage, WritableImage> textureImagePair, final double alpha)
	{
		final PixelReader pixelReader = textureImagePair.getA().getPixelReader();
		final PixelWriter pixelWriter = textureImagePair.getB().getPixelWriter();
		for (int x = 0; x < (int) textureImagePair.getA().getWidth(); ++x) {
			for (int y = 0; y < (int) textureImagePair.getA().getHeight(); ++y) {
				final Color c = pixelReader.getColor(x, y);
				pixelWriter.setColor(x, y, new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
			}
		}
	}

	private Pair<WritableImage, WritableImage> getTextureImagePair(final int screenScaleIndex, final int[] size)
	{
		Pair<WritableImage, WritableImage> textureImagePair = textures.get(screenScaleIndex);
		final boolean create = textureImagePair == null || (int) textureImagePair.getA().getWidth() != size[0] || (int) textureImagePair.getA().getHeight() != size[1];
		if (create)
		{
			textureImagePair = new ValuePair<>(new WritableImage(size[0], size[1]), new WritableImage(size[0], size[1]));
			textures.set(screenScaleIndex, textureImagePair);
		}
		return textureImagePair;
	}

	private void initializeMeshes()
	{
		delayedTextureUpdateExecutor.cancel();

		this.dimensions = this.viewer.getRenderUnit().getDimensions().clone();
		final long[] min = {0, 0}, max = this.dimensions;

		final OrthoSliceMeshFX mesh = new OrthoSliceMeshFX(
			new Point(2),
			new Point(this.dimensions),
			new AffineTransform3D()
		);

		final MeshView mv = new MeshView(mesh);
		final PhongMaterial material = new PhongMaterial();
		mv.setCullFace(CullFace.NONE);
		mv.setMaterial(material);

		// NOTE: the opacity property of the MeshView object does not have any effect.
		// But the transparency can still be controlled by modifying the opacity value of the diffuse color.
		material.setDiffuseColor(new Color(
				textureDiffuseOpaqueColor.getRed(),
				textureDiffuseOpaqueColor.getGreen(),
				textureDiffuseOpaqueColor.getBlue(),
				this.opacity.get()));

		this.meshView.set(mv);
	}

	public Group getScene()
	{
		return this.scene;
	}

	public boolean getIsVisible()
	{
		return this.isVisible.get();
	}

	public void setIsVisible(final boolean isVisible)
	{
		this.isVisible.set(isVisible);
	}

	public BooleanProperty isVisibleProperty()
	{
		return this.isVisible;
	}

	public DoubleProperty opacityProperty()
	{
		return this.opacity;
	}
}
