package bdv.bigcat.viewer.viewer3d;

import java.lang.invoke.MethodHandles;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphics.scenery.GeometryType;
import graphics.scenery.HasGeometry;
import graphics.scenery.Mesh;
import net.imglib2.Localizable;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;

public class OrthoSliceMesh extends Mesh implements HasGeometry
{

	public static Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final int NUM_DIMENSIONS = 3;

	private static final int NUM_VERTICES = 4;

	private final float[] vertices = new float[ 1 * NUM_VERTICES * NUM_DIMENSIONS ];

	private final float[] normals = new float[ 1 * NUM_VERTICES * NUM_DIMENSIONS ];

	private final int[] indices = {
			0, 1, 2, 0, 2, 3
	};

	private final float[] texcoords = {
			0.0f, 0.0f,
			1.0f, 0.0f,
			1.0f, 1.0f,
			0.0f, 1.0f
	};

	private final GeometryType geometryType = GeometryType.TRIANGLES;

	public OrthoSliceMesh( final Localizable bottomLeft, final Localizable bottomRight, final Localizable topRight, final Localizable topLeft, final AffineTransform3D pointTransform )
	{
		super();
		final RealPoint p = new RealPoint( 3 );

		final double offset = 0.0;

		transformPoint( bottomLeft, p, pointTransform, offset );
		vertices[ 0 ] = p.getFloatPosition( 0 );
		vertices[ 1 ] = p.getFloatPosition( 1 );
		vertices[ 2 ] = p.getFloatPosition( 2 );

		transformPoint( bottomRight, p, pointTransform, offset );
		vertices[ 3 ] = p.getFloatPosition( 0 );
		vertices[ 4 ] = p.getFloatPosition( 1 );
		vertices[ 5 ] = p.getFloatPosition( 2 );

		transformPoint( topRight, p, pointTransform, offset );
		vertices[ 6 ] = p.getFloatPosition( 0 );
		vertices[ 7 ] = p.getFloatPosition( 1 );
		vertices[ 8 ] = p.getFloatPosition( 2 );

		transformPoint( topLeft, p, pointTransform, offset );
		vertices[ 9 ] = p.getFloatPosition( 0 );
		vertices[ 10 ] = p.getFloatPosition( 1 );
		vertices[ 11 ] = p.getFloatPosition( 2 );

		final float[] normal = new float[] { 0.0f, 0.0f, 1.0f };
		pointTransform.apply( normal, normal );
		final float norm = normal[ 0 ] * normal[ 0 ] + normal[ 1 ] * normal[ 1 ] + normal[ 2 ] * normal[ 2 ];
		normal[ 0 ] /= norm;
		normal[ 1 ] /= norm;
		normal[ 2 ] /= norm;
		this.normals[ 0 ] = normal[ 0 ];
		this.normals[ 1 ] = normal[ 1 ];
		this.normals[ 2 ] = normal[ 2 ];
		this.normals[ 3 ] = normal[ 0 ];
		this.normals[ 4 ] = normal[ 1 ];
		this.normals[ 5 ] = normal[ 2 ];
		this.normals[ 6 ] = normal[ 0 ];
		this.normals[ 7 ] = normal[ 1 ];
		this.normals[ 8 ] = normal[ 2 ];
		this.normals[ 9 ] = normal[ 0 ];
		this.normals[ 10 ] = normal[ 1 ];
		this.normals[ 11 ] = normal[ 2 ];
	}

	private static < T extends RealPositionable & RealLocalizable > void transformPoint(
			final Localizable source2D,
			final T target3D,
			final RealTransform transform,
			final double offset )
	{
		target3D.setPosition( source2D.getDoublePosition( 0 ), 0 );
		target3D.setPosition( source2D.getDoublePosition( 1 ), 1 );
		target3D.setPosition( offset, 2 );
		transform.apply( target3D, target3D );
	}

	@Override
	public FloatBuffer getNormals()
	{
		return FloatBuffer.wrap( normals );
	}

	@Override
	public void setNormals( final FloatBuffer normals )
	{
		LOG.warn( "{} does not support setting normals.", this.getClass() );
	}

	@Override
	public FloatBuffer getVertices()
	{
		return FloatBuffer.wrap( vertices );
	}

	@Override
	public void setVertices( final FloatBuffer vertices )
	{
		LOG.warn( "{} does not support setting vertices.", this.getClass() );
	}

	@Override
	public GeometryType getGeometryType()
	{
		return this.geometryType;
	}

	@Override
	public void setGeometryType( final GeometryType geometryType )
	{
		LOG.warn( "{} does not support setting geometry type.", this.getClass() );
	}

	@Override
	public IntBuffer getIndices()
	{
		return IntBuffer.wrap( this.indices );
	}

	@Override
	public void setIndices( final IntBuffer indices )
	{
		LOG.warn( "{} does not support setting indices.", this.getClass() );
	}

	@Override
	public FloatBuffer getTexcoords()
	{
		return FloatBuffer.wrap( this.texcoords );
	}

	@Override
	public void setTexcoords( final FloatBuffer texcoords )
	{
		LOG.warn( "{} does not support setting texcoords.", this.getClass() );
	}
}
