package org.janelia.saalfeldlab.paintera.viewer3d;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.scene.Group;
import javafx.scene.shape.MeshView;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.util.LinAlgHelpers;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrthoSlicesManager {

	private static final int CLOSEST_Z_INDEX = -1;
	private static final int MIDDLE_Z_INDEX = 0;
	private static final int FARTHEST_Z_INDEX = 1;

	private final Group group = new Group();

	private final Map<ViewerAndTransforms, OrthoSliceFX> map = new HashMap<>();

	private final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty;

	private final OrthogonalViews<?> views;

	public OrthoSlicesManager(
			final Group scene,
			final OrthogonalViews<?> views,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty) {

		this.views = views;
		this.eyeToWorldTransformProperty = eyeToWorldTransformProperty;

		map.put(views.getTopLeft(), new OrthoSliceFX(views.getTopLeft().viewer()));
		map.put(views.getTopRight(), new OrthoSliceFX(views.getTopRight().viewer()));
		map.put(views.getBottomLeft(), new OrthoSliceFX(views.getBottomLeft().viewer()));

		scene.getChildren().add(this.group);

		eyeToWorldTransformProperty.addListener(obs -> InvokeOnJavaFXApplicationThread.invoke(this::updateMeshViews));
		map.values().forEach(orthoSliceFX -> orthoSliceFX.addListener(obs -> InvokeOnJavaFXApplicationThread.invoke(this::updateMeshViews)));
	}

	public Map<OrthogonalViews.ViewerAndTransforms, OrthoSliceFX> getOrthoSlices() {

		return Collections.unmodifiableMap(this.map);
	}

	/**
	 * To display transparent orthoslices correctly, the {@link MeshView}s representing their quadrants
	 * need to be ordered from the farthest to the closest with respect to the camera orientation.
	 */
	private void updateMeshViews() {

		assert Platform.isFxApplicationThread();
		final AffineTransform3D eyeToWorldTransform = eyeToWorldTransformProperty.get();

		final Map<ViewerAndTransforms, MeshView[][]> orthosliceQuadrants = new HashMap<>();
		final Map<ViewerAndTransforms, Boolean> orthosliceFront = new HashMap<>();

		// Find out which side of each orthoslice we are currently seeing
		for (final Map.Entry<ViewerAndTransforms, OrthoSliceFX> entry : this.map.entrySet()) {
			final ViewerAndTransforms viewer = entry.getKey();
			final OrthoSliceFX orthoSliceFX = entry.getValue();
			final OrthoSliceMeshFX mesh = orthoSliceFX.getMesh();

			final MeshView[][] quadrants = {
					{mesh.getMeshViews().get(0), mesh.getMeshViews().get(1)},
					{mesh.getMeshViews().get(2), mesh.getMeshViews().get(3)}
			};
			orthosliceQuadrants.put(viewer, quadrants);

			// Flip is needed for XZ and YZ views because we want top-left quadrants of each orthoslice
			// to be located in the octant where they all are facing towards the camera
			final Scale3D flipTransform = new Scale3D(1, 1, viewer == views.getTopLeft() ? 1 : -1);

			final AffineTransform3D orthosliceCenterToCameraTransform = new AffineTransform3D();
			orthosliceCenterToCameraTransform
					.preConcatenate(flipTransform)
					.preConcatenate(orthoSliceFX.getWorldTransform())
					.preConcatenate(eyeToWorldTransform.inverse());

			final double[] orthoslicePoint = {0, 0, 0}, orthosliceNormal = {0, 0, 1};
			orthosliceCenterToCameraTransform.apply(orthoslicePoint, orthoslicePoint);
			orthosliceCenterToCameraTransform.apply(orthosliceNormal, orthosliceNormal);
			Arrays.setAll(orthosliceNormal, d -> orthosliceNormal[d] - orthoslicePoint[d]);

			final boolean isFront = LinAlgHelpers.dot(orthoslicePoint, orthosliceNormal) > 0;
			orthosliceFront.put(viewer, isFront);
		}

		// Define intersections and relationship between the orthoslices as a two-element array in the following manner:
		//  [intersection that splits the orthoslice into two columns,
		//   intersection that splits the orthoslice into two rows]
		final Map<ViewerAndTransforms, ViewerAndTransforms[]> orthosliceNeighbors = new HashMap<>();
		orthosliceNeighbors.put(views.getTopLeft(), new ViewerAndTransforms[]{views.getBottomLeft(), views.getTopRight()});
		orthosliceNeighbors.put(views.getTopRight(), new ViewerAndTransforms[]{views.getBottomLeft(), views.getTopLeft()});
		orthosliceNeighbors.put(views.getBottomLeft(), new ViewerAndTransforms[]{views.getTopLeft(), views.getTopRight()});

		// Based on which side of each orthoslice we are currently seeing and the relationship between them,
		// assign an index to each quadrant of each orthoslice. There are three groups of quadrants in total:
		// 1) three quadrants that are closest to the camera (one in each orthoslice)
		// 2) three quadrants that are farthest from the camera (one in each orthoslice)
		// 3) other quadrants (their order does not matter as they do not intersect)
		final Map<ViewerAndTransforms, int[][]> quadrantZIndices = new HashMap<>();
		for (ViewerAndTransforms viewer : map.keySet()) {
			final int[][] zIndices = {
					{MIDDLE_Z_INDEX, MIDDLE_Z_INDEX},
					{MIDDLE_Z_INDEX, MIDDLE_Z_INDEX}
			};
			final int[] firstQuadrantIndex = {0, 0};
			for (int i = 0; i < 2; ++i) {
				// Shift the index of the closest quadrant if the neighboring orthoslice
				// along the corresponding dimension is visible from the back
				final ViewerAndTransforms neighbor = orthosliceNeighbors.get(viewer)[i];
				if (!orthosliceFront.get(neighbor))
					++firstQuadrantIndex[i];
			}

			// Mark the found quadrant as closest and the opposite one as farthest
			zIndices[firstQuadrantIndex[0]][firstQuadrantIndex[1]] = CLOSEST_Z_INDEX;
			zIndices[(firstQuadrantIndex[0] + 1) % 2][(firstQuadrantIndex[1] + 1) % 2] = FARTHEST_Z_INDEX;
			quadrantZIndices.put(viewer, zIndices);
		}

		// Collect the indexed quadrants into three distinct groups
		final List<MeshView> closestQuadrants = new ArrayList<>(), middleQuadrants = new ArrayList<>(), farthestQuadrants = new ArrayList<>();
		for (ViewerAndTransforms viewer : map.keySet()) {
			for (int r = 0; r < 2; ++r) {
				for (int c = 0; c < 2; ++c) {
					final MeshView quadrant = orthosliceQuadrants.get(viewer)[r][c];
					final int zIndex = quadrantZIndices.get(viewer)[r][c];
					switch (zIndex) {
					case CLOSEST_Z_INDEX:
						closestQuadrants.add(quadrant);
						break;
					case MIDDLE_Z_INDEX:
						middleQuadrants.add(quadrant);
						break;
					case FARTHEST_Z_INDEX:
						farthestQuadrants.add(quadrant);
						break;
					default:
						assert false;
						break;
					}
				}
			}
		}

		// JavaFX renders children of a group based on the order in which they are added.
		// Add the quadrants from the farthest that need to be rendered first to the closest that need to be rendered last.
		final List<MeshView> newChildren = new ArrayList<>();
		newChildren.addAll(farthestQuadrants);
		newChildren.addAll(middleQuadrants);
		newChildren.addAll(closestQuadrants);
		group.getChildren().setAll(newChildren);
	}
}
