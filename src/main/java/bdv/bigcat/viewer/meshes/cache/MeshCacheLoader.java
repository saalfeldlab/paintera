package bdv.bigcat.viewer.meshes.cache;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.LongFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.meshes.MeshGenerator.ShapeKey;
import bdv.bigcat.viewer.meshes.marchingcubes.MarchingCubes;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.CacheLoader;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

public class MeshCacheLoader< T > implements CacheLoader< ShapeKey, Pair< float[], float[] > >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final int[] cubeSize;

	private final RandomAccessibleInterval< T > data;

	private Function< ShapeKey, Pair< float[], float[] > > getHigherResMesh;

	private final LongFunction< Converter< T, BoolType > > getMaskGenerator;

	private final AffineTransform3D transform;

	public MeshCacheLoader(
			final int[] cubeSize,
			final RandomAccessibleInterval< T > data,
			final LongFunction< Converter< T, BoolType > > getMaskGenerator,
			final AffineTransform3D transform )
	{
		super();
		this.cubeSize = cubeSize;
		this.data = data;
		this.getHigherResMesh = key -> new ValuePair<>( new float[ 0 ], new float[ 0 ] );
		this.getMaskGenerator = getMaskGenerator;
		this.transform = transform;
	}

	public void setGetHigherResMesh( final Function< ShapeKey, Pair< float[], float[] > > getHigherResMesh )
	{
		this.getHigherResMesh = getHigherResMesh;
	}

	@Override
	public Pair< float[], float[] > get( final ShapeKey key ) throws Exception
	{

		if ( key.meshSimplificationIterations() > 0 )
		{
			final ShapeKey k = new ShapeKey( key.shapeId(), key.scaleIndex(), key.meshSimplificationIterations() - 1, key.min(), key.max() );
			final Pair< float[], float[] > highResMesh = getHigherResMesh.apply( k );
			return simplifyMesh( highResMesh.getA(), highResMesh.getB() );
		}

		final RandomAccessibleInterval< BoolType > mask = Converters.convert( data, getMaskGenerator.apply( key.shapeId() ), new BoolType( false ) );

		final float[] mesh = new MarchingCubes<>(
				Views.extendZero( mask ),
				Intervals.expand( key.interval(), Arrays.stream( cubeSize ).mapToLong( size -> size ).toArray() ),
				transform,
				cubeSize ).generateMesh();
		final float[] normals = new float[ mesh.length ];
		MarchingCubes.averagedSurfaceNormals( mesh, normals );
		for ( int i = 0; i < normals.length; ++i )
			normals[ i ] *= -1;
		return new ValuePair<>( mesh, normals );
	}

	public static Pair< float[], float[] > simplifyMesh( final float[] vertices, final float[] normals )
	{
		LOG.warn( "This is just a mock mesh simplification currently!" );
		final float[] newVertices = new float[ vertices.length / 2 / 9 * 9 ];
		final float[] newNormals = new float[ vertices.length / 2 / 9 * 9 ];
		System.arraycopy( vertices, 0, newVertices, 0, newVertices.length );
		System.arraycopy( normals, 0, newNormals, 0, newNormals.length );
		return new ValuePair<>( newVertices, newNormals );
	}

}
