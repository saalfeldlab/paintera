package org.janelia.saalfeldlab.paintera.viewer3d;

import java.lang.invoke.MethodHandles;

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

public class OrthoSliceMeshFX extends TriangleMesh
{

	public static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final int[] indices = {
			0, 1, 2, 0, 2, 3
	};

	public OrthoSliceMeshFX(
		final RealLocalizable bottomLeft,
		final RealLocalizable bottomRight,
		final RealLocalizable topRight,
		final RealLocalizable topLeft,
		final AffineTransform3D pointTransform)
	{
		setVertices(bottomLeft, bottomRight, topRight, topLeft, pointTransform);

		setNormals(pointTransform);

		updateTexCoords();

		final ObservableFaceArray faceIndices = getFaces();
		for (final int i : indices)
			faceIndices.addAll(i, i, i);

		setVertexFormat(VertexFormat.POINT_NORMAL_TEXCOORD);
	}

	private void updateTexCoords() {

		final float[] texCoordMin = {0.0f, 0.0f}, texCoordMax = {1.0f, 1.0f};
		final float[] texCoords = new float[2 * 4];

		texCoords[0] = texCoordMin[0]; texCoords[1] = texCoordMin[1];
		texCoords[2] = texCoordMax[0]; texCoords[3] = texCoordMin[1];
		texCoords[4] = texCoordMax[0]; texCoords[5] = texCoordMax[1];
		texCoords[6] = texCoordMin[0]; texCoords[7] = texCoordMax[1];

		getTexCoords().setAll(texCoords);
	}

	private void setVertices(
			final RealLocalizable bottomLeft,
			final RealLocalizable bottomRight,
			final RealLocalizable topRight,
			final RealLocalizable topLeft,
			final AffineTransform3D pointTransform)
	{
		final RealPoint p = new RealPoint(3);
		final double offset = 0.0;

		final float[] vertex = new float[3];
		final float[] vertexBuffer = new float[3 * 4];

		transformPoint(bottomLeft, p, pointTransform, offset);
		p.localize(vertex);
		System.arraycopy(vertex, 0, vertexBuffer, 0, 3);

		transformPoint(bottomRight, p, pointTransform, offset);
		p.localize(vertex);
		System.arraycopy(vertex, 0, vertexBuffer, 3, 3);

		transformPoint(topRight, p, pointTransform, offset);
		p.localize(vertex);
		System.arraycopy(vertex, 0, vertexBuffer, 6, 3);

		transformPoint(topLeft, p, pointTransform, offset);
		p.localize(vertex);
		System.arraycopy(vertex, 0, vertexBuffer, 9, 3);

		getPoints().setAll(vertexBuffer);
	}

	private void setNormals(final AffineTransform3D pointTransform)
	{
		final float[] normal = new float[] {0.0f, 0.0f, 1.0f};
		pointTransform.apply(normal, normal);
		final float norm = normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2];
		normal[0] /= norm;
		normal[1] /= norm;
		normal[2] /= norm;
		final float[] normalBuffer = new float[12];
		System.arraycopy(normal, 0, normalBuffer, 0, 3);
		System.arraycopy(normal, 0, normalBuffer, 3, 3);
		System.arraycopy(normal, 0, normalBuffer, 6, 3);
		System.arraycopy(normal, 0, normalBuffer, 9, 3);

		getNormals().setAll(normalBuffer);
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
