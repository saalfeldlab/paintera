package org.janelia.saalfeldlab.paintera.viewer3d;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.render.RenderUnit;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Affine;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.util.NamedThreadFactory;

import java.util.ArrayList;
import java.util.List;

public class OrthoSliceFX
{
	// this delay is used to avoid blinking when changing texture resolution
	private static final long textureUpdateDelayNanoSec = 1000000 * 50; // 50 msec

	private final Group scene;

	private final ViewerPanelFX viewer;

	private final Group meshesGroup = new Group();
	
	private final PriorityLatestTaskExecutor delayedTextureUpdateExecutor = new PriorityLatestTaskExecutor(textureUpdateDelayNanoSec, new NamedThreadFactory("texture-update-thread-%d", true));

	private final List<WritableImage> textures = new ArrayList<>();

	private int currentTextureScreenScaleIndex = -1;

	private double[] screenScales;
	private long[] dimensions;

	private final ObservableList<MeshView> meshViews = FXCollections.observableArrayList();
	{
		meshViews.addListener((ListChangeListener<? super MeshView>) change -> InvokeOnJavaFXApplicationThread.invoke(() -> meshesGroup.getChildren().setAll(meshViews)));
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

	public OrthoSliceFX(final Group scene, final ViewerPanelFX viewer)
	{
		super();
		this.scene = scene;
		this.viewer = viewer;

		final Affine viewerTransform = new Affine();
		this.meshesGroup.getTransforms().setAll(viewerTransform);

		this.viewer.addTransformListener(tf -> {
			AffineTransform3D inverse = tf.inverse();
			final Affine newViewerTransform = new Affine(
					inverse.get(0, 0), inverse.get(0, 1), inverse.get(0, 2), inverse.get(0, 3),
					inverse.get(1, 0), inverse.get(1, 1), inverse.get(1, 2), inverse.get(1, 3),
					inverse.get(2, 0), inverse.get(2, 1), inverse.get(2, 2), inverse.get(2, 3)
			);
			InvokeOnJavaFXApplicationThread.invoke(() -> viewerTransform.setToTransform(newViewerTransform));
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
		final WritableImage textureImage = getTextureImage(newv.getScreenScaleIndex(), textureImageSize);

		final Interval roi = Intervals.intersect(
			Intervals.smallestContainingInterval(newv.getRenderTargetRealInterval()),
			new FinalInterval(new FinalDimensions(textureImageSize))
		);

		// copy relevant part of the rendered image into the texture image
		final PixelReader pixelReader = newv.getImage().getPixelReader();
		final PixelWriter pixelWriter = textureImage.getPixelWriter();
		pixelWriter.setPixels(
			(int) roi.min(0), // dst x
			(int) roi.min(1), // dst y
			(int) roi.dimension(0), // w
			(int) roi.dimension(1),	// h
			pixelReader, // src
			(int) roi.min(0), // src x
			(int) roi.min(1)  // src y
		);

		// setup a task for setting the texture of the mesh
		final int newScreenScaleIndex = newv.getScreenScaleIndex();
		final Runnable updateTextureTask = () -> InvokeOnJavaFXApplicationThread.invoke(
			() -> {
				// calculate new texture coordinates depending on the ratio between the screen size and the rendered image
				final float[] texCoordMin = {0.0f, 0.0f}, texCoordMax = new float[2];
				for (int d = 0; d < 2; ++d)
					texCoordMax[d] = (float) (dimensions[d] / (textureImageSize[d] / screenScales[newScreenScaleIndex]));

				((PhongMaterial) this.meshViews.get(0).getMaterial()).setSelfIlluminationMap(textureImage);
				((OrthoSliceMeshFX) this.meshViews.get(0).getMesh()).setTexCoords(texCoordMin, texCoordMax);

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

	private WritableImage getTextureImage(final int screenScaleIndex, final int[] size)
	{
		WritableImage textureImage = textures.get(screenScaleIndex);
		final boolean create = textureImage == null || (int) textureImage.getWidth() != size[0] || (int) textureImage.getHeight() != size[1];
		if (create)
		{
			textureImage = new WritableImage(size[0], size[1]);
			textures.set(screenScaleIndex, textureImage);
		}
		return textureImage;
	}

	private void initializeMeshes()
	{
		this.meshViews.clear();
		delayedTextureUpdateExecutor.cancel();

		this.dimensions = this.viewer.getRenderUnit().getDimensions().clone();
		final long[] min = {0, 0}, max = this.dimensions;

		final List<MeshView> newMeshViews = new ArrayList<>();

		final OrthoSliceMeshFX mesh = new OrthoSliceMeshFX(
			new RealPoint(min[0], min[1]),
			new RealPoint(max[0], min[1]),
			new RealPoint(max[0], max[1]),
			new RealPoint(min[0], max[1]),
			new AffineTransform3D()
		);

		final MeshView mv = new MeshView(mesh);
		final PhongMaterial material = new PhongMaterial();
		mv.setCullFace(CullFace.NONE);
		mv.setMaterial(material);
		material.setDiffuseColor(Color.BLACK);
		material.setSpecularColor(Color.BLACK);

		newMeshViews.add(mv);

		this.meshViews.setAll(newMeshViews);
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

}
