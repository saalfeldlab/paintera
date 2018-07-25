package org.janelia.saalfeldlab.paintera.viewer3d;

import java.util.ArrayList;
import java.util.List;

import bdv.fx.viewer.ViewerPanelFX;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.util.NamedThreadFactory;

public class OrthoSliceFX
{
	private final Group scene;

	private final ViewerPanelFX viewer;

	private final RenderTransformListener renderTransformListener = new RenderTransformListener();

	private final List<Node> planes = new ArrayList<>();

	private final OrthoSliceMeshFX mesh = new OrthoSliceMeshFX(
			new RealPoint(0, 0),
			new RealPoint(1, 0),
			new RealPoint(1, 1),
			new RealPoint(0, 1),
			new AffineTransform3D()
	);

	private final MeshView mv = new MeshView(mesh);

	private final PhongMaterial material;

	private final BooleanProperty isVisible = new SimpleBooleanProperty(false);

	{
		this.isVisible.addListener((oldv, obs, newv) -> {
			synchronized (this)
			{
				if (newv)
				{
					InvokeOnJavaFXApplicationThread.invoke(() -> this.getScene().getChildren().add(mv));
				}
				else
				{
					InvokeOnJavaFXApplicationThread.invoke(() -> this.getScene().getChildren().remove(mv));
				}
			}
		});
	}

	// TODO re-think/reduce this delay
	// 500ms delay
	LatestTaskExecutor es = new LatestTaskExecutor(
			500 * 1000 * 1000,
			new NamedThreadFactory("ortho-slice-executor", true)
	);

	public OrthoSliceFX(final Group scene, final ViewerPanelFX viewer)
	{
		super();
		this.scene = scene;
		this.viewer = viewer;
		this.viewer.getDisplay().addImageChangeListener(this.renderTransformListener);
		this.planes.add(mv);

		this.material = new PhongMaterial();

		mv.setCullFace(CullFace.NONE);
		mv.setMaterial(material);

		material.setDiffuseColor(Color.BLACK);
		material.setSpecularColor(Color.BLACK);
	}

	private void paint(final Image image)
	{
		final AffineTransform3D viewerTransform = new AffineTransform3D();
		final double            w;
		final double            h;
		synchronized (viewer)
		{
			w = viewer.getWidth();
			h = viewer.getHeight();
			this.viewer.getState().getViewerTransform(viewerTransform);
		}
		if (w <= 0 || h <= 0) { return; }
		InvokeOnJavaFXApplicationThread.invoke(() -> {
			mesh.update(
					new RealPoint(0, 0),
					new RealPoint(w, 0),
					new RealPoint(w, h),
					new RealPoint(0, h),
					viewerTransform.inverse()
			           );
		});
		es.execute(() -> {
			Thread.currentThread().setName("ortho-slice-executor");
			final double    scale     = 512.0 / Math.max(w, h);
			final int       fitWidth  = (int) Math.round(w * scale);
			final int       fitHeight = (int) Math.round(h * scale);
			final ImageView imageView = new ImageView(image);
			imageView.setPreserveRatio(true);
			imageView.setFitWidth(fitWidth);
			imageView.setFitHeight(fitHeight);
			final SnapshotParameters snapshotParameters = new SnapshotParameters();
			snapshotParameters.setFill(Color.BLACK);
			InvokeOnJavaFXApplicationThread.invoke(() -> {
				imageView.snapshot(snapshotResult -> {
					InvokeOnJavaFXApplicationThread.invoke(() -> {

						material.setSelfIlluminationMap(snapshotResult.getImage());
						mesh.update(
								new RealPoint(0, 0),
								new RealPoint(w, 0),
								new RealPoint(w, h),
								new RealPoint(0, h),
								viewerTransform.inverse()
						           );
					});
					return null;
				}, snapshotParameters, null);
			});
		});
	}

	private final class RenderTransformListener implements ChangeListener<Image>
	{

		@Override
		public void changed(final ObservableValue<? extends Image> observable, final Image oldValue, final Image
				newValue)
		{
			if (newValue != null)
			{
				paint(newValue);
			}
		}

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

	public void stop()
	{
		this.es.shutDown();
	}

	public void setDelay(final long delayInNanoSeconds)
	{
		this.es.setDelay(delayInNanoSeconds);
	}
}
