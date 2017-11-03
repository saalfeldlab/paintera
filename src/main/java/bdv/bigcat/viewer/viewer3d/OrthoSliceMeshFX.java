package bdv.bigcat.viewer.viewer3d;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.ObservableFloatArray;
import javafx.scene.shape.ObservableFaceArray;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;

public class OrthoSliceMeshFX extends TriangleMesh
{

	public static Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final int[] indices = {
			0, 1, 2, 0, 2, 3
	};

	private static final float[] texcoords = {
			0.0f, 0.0f,
			1.0f, 0.0f,
			1.0f, 1.0f,
			0.0f, 1.0f
	};

	public OrthoSliceMeshFX( final RealLocalizable bottomLeft, final RealLocalizable bottomRight, final RealLocalizable topRight, final RealLocalizable topLeft, final AffineTransform3D pointTransform )
	{
		super();
		getTexCoords().addAll( texcoords );
		this.update( bottomLeft, bottomRight, topRight, topLeft, pointTransform );
		final ObservableFaceArray faceIndices = getFaces();
		for ( final int i : indices )
			faceIndices.addAll( i, i, i );
		setVertexFormat( VertexFormat.POINT_NORMAL_TEXCOORD );

	}

	public void update(
			final RealLocalizable bottomLeft,
			final RealLocalizable bottomRight,
			final RealLocalizable topRight,
			final RealLocalizable topLeft,
			final AffineTransform3D pointTransform )
	{
		final RealPoint p = new RealPoint( 3 );

		final double offset = 0.0;

		final ObservableFloatArray vertices = getPoints();
		final ObservableFloatArray normals = getNormals();

		final float[] vertex = new float[ 3 ];

		final float[] vertexBuffer = new float[ 3 * 4 ];

		transformPoint( bottomLeft, p, pointTransform, offset );
		p.localize( vertex );
		System.arraycopy( vertex, 0, vertexBuffer, 0, 3 );

		transformPoint( bottomRight, p, pointTransform, offset );
		p.localize( vertex );
		System.arraycopy( vertex, 0, vertexBuffer, 3, 3 );

		transformPoint( topRight, p, pointTransform, offset );
		p.localize( vertex );
		System.arraycopy( vertex, 0, vertexBuffer, 6, 3 );

		transformPoint( topLeft, p, pointTransform, offset );
		p.localize( vertex );
		System.arraycopy( vertex, 0, vertexBuffer, 9, 3 );

		final float[] normal = new float[] { 0.0f, 0.0f, 1.0f };
		pointTransform.apply( normal, normal );
		final float norm = normal[ 0 ] * normal[ 0 ] + normal[ 1 ] * normal[ 1 ] + normal[ 2 ] * normal[ 2 ];
		normal[ 0 ] /= norm;
		normal[ 1 ] /= norm;
		normal[ 2 ] /= norm;
		final float[] normalBuffer = new float[ 12 ];
		System.arraycopy( normal, 0, normalBuffer, 0, 3 );
		System.arraycopy( normal, 0, normalBuffer, 3, 3 );
		System.arraycopy( normal, 0, normalBuffer, 6, 3 );
		System.arraycopy( normal, 0, normalBuffer, 9, 3 );
		vertices.clear();
		normals.clear();
		vertices.addAll( vertexBuffer );
		normals.addAll( normalBuffer );
	}

	private static < T extends RealPositionable & RealLocalizable > void transformPoint(
			final RealLocalizable source2D,
			final T target3D,
			final RealTransform transform,
			final double offset )
	{
		target3D.setPosition( source2D.getDoublePosition( 0 ), 0 );
		target3D.setPosition( source2D.getDoublePosition( 1 ), 1 );
		target3D.setPosition( offset, 2 );
		transform.apply( target3D, target3D );
	}
}
