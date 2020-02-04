package org.janelia.saalfeldlab.paintera.viewer3d;

import javafx.beans.property.ObjectProperty;
import javafx.scene.Group;
import javafx.scene.shape.MeshView;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;

import java.util.*;
import java.util.stream.Collectors;

public class OrthoSlicesManager
{
	private final Group group = new Group();

	private final Map<ViewerAndTransforms, OrthoSliceFX> map = new HashMap<>();

	private final GlobalTransformManager globalTransformManager;

	private final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty;

	public OrthoSlicesManager(
			final Group scene,
			final OrthogonalViews<?> views,
			final GlobalTransformManager globalTransformManager,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty)
	{
		this.globalTransformManager = globalTransformManager;
		this.eyeToWorldTransformProperty = eyeToWorldTransformProperty;

		map.put(views.topLeft(), new OrthoSliceFX(views.topLeft().viewer()));
		map.put(views.topRight(), new OrthoSliceFX(views.topRight().viewer()));
		map.put(views.bottomLeft(), new OrthoSliceFX(views.bottomLeft().viewer()));

		scene.getChildren().add(this.group);

		eyeToWorldTransformProperty.addListener(obs -> updateMeshViews());
		globalTransformManager.addListener(tf -> updateMeshViews());
		map.values().forEach(orthoSliceFX -> orthoSliceFX.addListener(obs -> updateMeshViews()));
	}

	public Map<OrthogonalViews.ViewerAndTransforms, OrthoSliceFX> getOrthoSlices()
	{
		return Collections.unmodifiableMap(this.map);
	}

	/**
	 * To display transparent orthoslices correctly, the {@link MeshView}s representing their quarters
	 * need to be sorted by the distance to the camera (from the farthest to the closest).
	 */
	private void updateMeshViews()
	{
		final AffineTransform3D eyeToWorldTransform = eyeToWorldTransformProperty.get();
		final List<Pair<MeshView, Double>> meshViewsAndPoints = new ArrayList<>();
		for (final Map.Entry<ViewerAndTransforms, OrthoSliceFX> entry : this.map.entrySet())
		{
			final ViewerAndTransforms viewerAndTransforms = entry.getKey();
			final OrthoSliceFX orthoSliceFX = entry.getValue();
			final OrthoSliceMeshFX mesh = orthoSliceFX.getMesh();

			if (!orthoSliceFX.getIsVisible() || mesh == null)
				continue;

			final List<MeshView> meshViews = mesh.getMeshViews();
			final List<RealInterval> meshViewIntervals = mesh.getMeshViewIntervals();
			for (int i = 0; i < meshViews.size(); ++i)
			{
				final MeshView meshView = meshViews.get(i);
				final RealInterval meshViewInterval = meshViewIntervals.get(i);
				final RealPoint centerPoint = new RealPoint(3);
				for (int d = 0; d < meshViewInterval.numDimensions(); ++d)
					centerPoint.setPosition((meshViewInterval.realMin(d) + meshViewInterval.realMax(d)) / 2, d);

				viewerAndTransforms.viewer().displayToGlobalCoordinates(centerPoint);
				eyeToWorldTransform.applyInverse(centerPoint, centerPoint);
				final double distanceFromCamera = centerPoint.getDoublePosition(2);

				// distanceFromCamera with minus because we want the farthest mesh views to come first
				meshViewsAndPoints.add(new ValuePair<>(meshView, -distanceFromCamera));
			}
		}

		Collections.sort(meshViewsAndPoints, Comparator.comparingDouble(Pair::getB));

		// NOTE: JavaFX renders children of a group based on the order in which they are added.
		final List<MeshView> newChildren = meshViewsAndPoints.stream().map(Pair::getA).collect(Collectors.toList());
		group.getChildren().setAll(newChildren);
	}
}
