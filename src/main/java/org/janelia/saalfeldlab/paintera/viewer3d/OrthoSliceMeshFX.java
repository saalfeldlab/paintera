package org.janelia.saalfeldlab.paintera.viewer3d;

import java.lang.invoke.MethodHandles;

import javafx.collections.ObservableFloatArray;
import javafx.scene.shape.ObservableFaceArray;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines a rectangular mesh for displaying an orthoslice in the 3D scene.
 *
 * The mesh is made up of 8 triangles as follows:
 *
 *  -------------
 *  |    /|    /|
 *  |   / |   / |
 *  |  /  |  /  |
 *  | /   | /   |
 *  |/    |/    |
 *  -------------
 *  |    /|    /|
 *  |   / |   / |
 *  |  /  |  /  |
 *  | /   | /   |
 *  |/    |/    |
 *  -------------
 */

public class OrthoSliceMeshFX extends TriangleMesh
{

	public static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public OrthoSliceMeshFX(
			final RealLocalizable min,
			final RealLocalizable max,
			final AffineTransform3D transform)
	{
		setVertexFormat(VertexFormat.POINT_TEXCOORD);

		setVertices(min, max, transform);

		setTexCoords(
				new RealPoint(0, 0),
				new RealPoint(1, 1));

		setFaces();
	}

	public void setTexCoords(final RealLocalizable texCoordMin, final RealLocalizable texCoordMax)
	{
		final ObservableFloatArray texCoords = getTexCoords();
		texCoords.clear();

		final RealPoint pt = new RealPoint(2);
		final float[] texCoord = new float[2];

		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 3; ++col) {
				setCoords2D(col, row, texCoordMin, texCoordMax, pt);
				pt.localize(texCoord);
				texCoords.addAll(texCoord);
			}
		}
	}

	private void setVertices(
			final RealLocalizable min,
			final RealLocalizable max,
			final AffineTransform3D transform)
	{
		final ObservableFloatArray vertices = getPoints();
		vertices.clear();

		final RealPoint pt = new RealPoint(3);
		final float[] vertex = new float[3];

		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 3; ++col) {
				setCoords2D(col, row, min, max, pt);
				transformPoint(pt, transform);
				pt.localize(vertex);
				vertices.addAll(vertex);
			}
		}
	}

	private void setFaces()
	{
		final ObservableFaceArray faces = getFaces();
		faces.clear();

		for (int row = 0; row < 2; ++row) {
			for (int col = 0; col < 2; ++col) {
				final int[] indices = {
						3 * row + col,
						3 * row + (col + 1),
						3 * (row + 1) + (col + 1),

						3 * row + col,
						3 * (row + 1) + (col + 1),
						3 * (row + 1) + col,
				};
				for (final int i : indices)
					faces.addAll(i, i);
			}
		}
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

	private static <T extends RealPositionable & RealLocalizable> void transformPoint(
			final T point3D,
			final RealTransform transform)
	{
		transformPoint(point3D, point3D, transform);
	}

	private static <T extends RealPositionable & RealLocalizable> void transformPoint(
			final RealLocalizable source2D,
			final T target3D,
			final RealTransform transform)
	{
		transformPoint(source2D, target3D, transform, 0.0);
	}

	private static <T extends RealPositionable & RealLocalizable> void transformPoint(
			final RealLocalizable source2D,
			final T target3D,
			final RealTransform transform,
			final double offset)
	{
		target3D.setPosition(source2D.getDoublePosition(0), 0);
		target3D.setPosition(source2D.getDoublePosition(1), 1);
		target3D.setPosition(offset, 2);
		transform.apply(target3D, target3D);
	}
}
