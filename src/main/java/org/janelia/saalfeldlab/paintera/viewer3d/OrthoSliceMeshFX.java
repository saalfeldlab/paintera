package org.janelia.saalfeldlab.paintera.viewer3d;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.*;
import javafx.scene.transform.Affine;
import net.imglib2.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines a rectangular mesh for displaying an orthoslice in the 3D scene.
 *
 * An orthoslice view is made up of 4 {@link MeshView}s consisting of 2 triangles each:
 *
 *  -------  -------
 *  |    /|  |    /|
 *  |   / |  |   / |
 *  |  /  |  |  /  |
 *  | /   |  | /   |
 *  |/    |  |/    |
 *  -------  -------
 *
 *  -------  -------
 *  |    /|  |    /|
 *  |   / |  |   / |
 *  |  /  |  |  /  |
 *  | /   |  | /   |
 *  |/    |  |/    |
 *  -------  -------
 */

public class OrthoSliceMeshFX
{

	public static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	// two sets of faces are required for proper shading on either side of the orthoslice
	private static final int[] facesFront = { 0, 1, 2, 0, 2, 3 };
	private static final int[] facesBack = { 0, 2, 1, 0, 3, 2 };

	private final List<MeshView> meshViews = new ArrayList<>();

	private final List<RealInterval> meshViewIntervals = new ArrayList<>();

	private final PhongMaterial material = new PhongMaterial();

	final float[] buf2D = new float[2];
	final float[] buf3D = new float[3];

	public OrthoSliceMeshFX(final long[] dimensions, final Affine viewerTransformFX)
	{
		final Point min = new Point(2), max = new Point(dimensions);

		final List<RealPoint> vertexPoints = calculateVertexPoints(min, max);
		final List<RealPoint> texCoordsPoints = calculateTexCoords(new Point(0, 0), new Point(1, 1));

		// create 4 mesh views for each quarter, where each mesh view consists of two triangles
		for (int row = 0; row < 2; ++row) {
			for (int col = 0; col < 2; ++col) {
				final TriangleMesh mesh = new TriangleMesh();
				mesh.setVertexFormat(VertexFormat.POINT_TEXCOORD);

				final int[] pointIndices = getPointIndicesForQuarter(row, col);

				// set vertices
				for (final int ptIndex : pointIndices) {
					vertexPoints.get(ptIndex).localize(buf3D);
					mesh.getPoints().addAll(buf3D);
				}

				// set texture coordinates
				for (final int ptIndex : pointIndices) {
					texCoordsPoints.get(ptIndex).localize(buf2D);
					mesh.getTexCoords().addAll(buf2D);
				}

				// set faces
				for (final int i : facesFront)
					mesh.getFaces().addAll(i, i);
				for (final int i : facesBack)
					mesh.getFaces().addAll(i, i);

				final MeshView meshView = new MeshView(mesh);
				meshView.setCullFace(CullFace.BACK);
				meshView.setMaterial(material);
				meshView.getTransforms().setAll(viewerTransformFX);

				meshViews.add(meshView);

				final double[] meshViewMin = new double[2], meshViewMax = new double[2];
				Arrays.fill(meshViewMin, Double.POSITIVE_INFINITY);
				Arrays.fill(meshViewMax, Double.NEGATIVE_INFINITY);
				for (final int ptIndex : pointIndices) {
					final RealPoint pt = vertexPoints.get(ptIndex);
					for (int d = 0; d < 2; ++d) {
						meshViewMin[d] = Math.min(pt.getDoublePosition(d), meshViewMin[d]);
						meshViewMax[d] = Math.max(pt.getDoublePosition(d), meshViewMax[d]);
					}
				}
				final RealInterval meshViewInterval = new FinalRealInterval(meshViewMin, meshViewMax);
				meshViewIntervals.add(meshViewInterval);
			}
		}
	}

	/**
	 * @return
	 * 		list of 4 {@link MeshView}s representing quarters of the orthoslice
	 */
	public List<MeshView> getMeshViews()
	{
		return meshViews;
	}

	/**
	 * @return
	 * 		list of 4 two-dimensional {@link RealInterval}s representing min and max positions of each quarter in the orthoslice
	 */
	public List<RealInterval> getMeshViewIntervals()
	{
		return meshViewIntervals;
	}

	public PhongMaterial getMaterial()
	{
		return material;
	}

	public void setTexCoords(final RealLocalizable texCoordMin, final RealLocalizable texCoordMax)
	{
		final List<RealPoint> texCoordsPoints = calculateTexCoords(texCoordMin, texCoordMax);
		for (int row = 0; row < 2; ++row) {
			for (int col = 0; col < 2; ++col) {
				final MeshView meshView = meshViews.get(2 * row + col);
				if (meshView == null)
					continue;
				final TriangleMesh mesh = (TriangleMesh) meshView.getMesh();

				mesh.getTexCoords().clear();
				final int[] pointIndices = getPointIndicesForQuarter(row, col);
				for (final int ptIndex : pointIndices) {
					texCoordsPoints.get(ptIndex).localize(buf2D);
					mesh.getTexCoords().addAll(buf2D);
				}
			}
		}
	}

	private static int[] getPointIndicesForQuarter(final int row, final int col)
	{
		return new int[] {
				3 * row + col,
				3 * row + (col + 1),
				3 * (row + 1) + (col + 1),
				3 * (row + 1) + col
		};
	}

	private static List<RealPoint> calculateVertexPoints(final RealLocalizable min, final RealLocalizable max)
	{
		final List<RealPoint> vertexPoints = new ArrayList<>();
		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 3; ++col) {
				final RealPoint pt = new RealPoint(3);
				setCoords2D(col, row, min, max, pt);
				vertexPoints.add(pt);
			}
		}
		return vertexPoints;
	}

	private static List<RealPoint> calculateTexCoords(final RealLocalizable texCoordMin, final RealLocalizable texCoordMax)
	{
		final List<RealPoint> texCoords = new ArrayList<>();
		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 3; ++col) {
				final RealPoint pt = new RealPoint(2);
				setCoords2D(col, row, texCoordMin, texCoordMax, pt);
				texCoords.add(pt);
			}
		}
		return texCoords;
	}

	private static void setCoords2D(
			final int col,
			final int row,
			final RealLocalizable min,
			final RealLocalizable max,
			final RealPositionable target)
	{
		final int[] pos = new int[] {col, row};
		for (int d = 0; d < 2; ++d) {
			switch (pos[d]) {
				case 0:
					target.setPosition(min.getDoublePosition(d), d);
					break;
				case 1:
					target.setPosition((min.getDoublePosition(d) + max.getDoublePosition(d)) / 2, d);
					break;
				case 2:
					target.setPosition(max.getDoublePosition(d), d);
					break;
			}
		}
	}
}
