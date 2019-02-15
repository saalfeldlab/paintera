package org.janelia.saalfeldlab.paintera.viewer3d;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.render.RenderUnit;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Affine;
import net.imglib2.FinalInterval;
import net.imglib2.RealPoint;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OrthoSliceFX
{
	private final Group scene;

	private final ViewerPanelFX viewer;

	private final Group meshesGroup = new Group();

	private boolean initializeMeshesWithCurrentTexture = false;
	private boolean postponeMeshesInitialization = false;

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

		/*
		// This listener keeps track of rendering mode changes, which allows to make the transition completely unnoticeable
		this.viewer.getRenderingModeController().getModeProperty().addListener((obs, oldv, newv) -> {
			if (newv == RenderingMode.MULTI_TILE) {
				// Switch from single tile to multi tile, the current texture image will be used to initialize textures for the new meshes
				this.initializeMeshesWithCurrentTexture = true;
			} else if (newv == RenderingMode.SINGLE_TILE ) {
				// Switch from multi tile to single tile, initialization will be postponed until first rendered frame is received
				this.postponeMeshesInitialization = true;
			}
		});
		*/
	}

	/*private void initializeMeshes()
	{
		if (this.postponeMeshesInitialization)
		{
			if (this.imagePropertyGrid == null)
				return;
			final CellGrid grid = this.imagePropertyGrid.getGrid();
			final int numMeshes = (int) Intervals.numElements(grid.getGridDimensions());
			assert numMeshes == 1;
			for (int meshIndex = 0; meshIndex < numMeshes; ++meshIndex)
			{
				final ReadOnlyObjectProperty<RenderUnit.RenderedImage> renderedImage = this.imagePropertyGrid.renderedImagePropertyAt(meshIndex);
				renderedImage.addListener((obsIm, oldvIm, newvIm) -> {
					if (newvIm != null && newvIm.getImage() != null) {
						if (this.postponeMeshesInitialization) {
							this.postponeMeshesInitialization = false;
							initializeMeshes(newvIm.getImage());
						}
					}
				});
			}
		}
		else
		{
			final Image currentTextureImage;
			if (this.initializeMeshesWithCurrentTexture) {
				this.initializeMeshesWithCurrentTexture = false;
				assert this.meshViews.size() == 1;
				currentTextureImage = ((PhongMaterial) meshViews.get(0).getMaterial()).getSelfIlluminationMap();
			} else {
				currentTextureImage = null;
			}
			initializeMeshes(currentTextureImage);
		}
	}*/

	private void initializeMeshes()
	{
		this.meshViews.clear();
//		if (renderedImage == null)
//		    return;

		final CellGrid grid = this.viewer.getRenderUnit().getCellGrid();
		final long[] dimensions = this.viewer.getRenderUnit().getDimensions();
		final int[] padding = this.viewer.getRenderUnit().getPadding();

		final long[] min = {0, 0};
		final long[] max = new long[2];
		final long[] dims = grid.getImgDimensions();
		Arrays.setAll(max, d -> Math.min(min[d] + dims[d], dimensions[d]));

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

		final double[] meshSizeToTextureSizeRatio = new double[2];
		Arrays.setAll(meshSizeToTextureSizeRatio, d -> (double) (max[d] - min[d]) / dims[d]);
		final int[] paddedTextureSize = new int[2];
		this.viewer.getRenderUnit().getRenderedImageProperty().addListener((obsIm, oldvIm, newvIm) -> {
			if (newvIm != null && newvIm.getImage() != null/* && this.viewer.getRenderingModeController().validateTag(newvIm.getTag())*/) {
				paddedTextureSize[0] = (int) newvIm.getImage().getWidth();
				paddedTextureSize[1] = (int) newvIm.getImage().getHeight();
				mesh.updateTexCoords(paddedTextureSize, padding, meshSizeToTextureSizeRatio);
				material.setSelfIlluminationMap(newvIm.getImage());
			}
		});
		newMeshViews.add(mv);

		/*
		if (textureImage != null) {
			// Use the current single-tile texture image to initialize texture for the new (smaller) meshes.
			// Texture coordinates are computed for each mesh to crop the corresponding region from the texture image.
			final int[] currentTextureImageSize = new int[] {(int) Math.round(textureImage.getWidth()), (int) Math.round(textureImage.getHeight())};
			final float[] texCoordMin = new float[2], texCoordMax = new float[2];
			for (int d = 0; d < 2; ++d) {
				texCoordMin[d] = (((float) min[d] / dimensions[d]) * (currentTextureImageSize[d] - 2 * padding[d]) + padding[d]) / (float) currentTextureImageSize[d];
				texCoordMax[d] = (((float) max[d] / dimensions[d]) * (currentTextureImageSize[d] - 2 * padding[d]) + padding[d]) / (float) currentTextureImageSize[d];
			}
			mesh.updateTexCoords(texCoordMin, texCoordMax);
			material.setSelfIlluminationMap(textureImage);
		}
		*/

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
