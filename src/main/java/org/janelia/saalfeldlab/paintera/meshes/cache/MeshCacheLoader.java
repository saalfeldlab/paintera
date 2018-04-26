package org.janelia.saalfeldlab.paintera.meshes.cache;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongFunction;

import org.janelia.saalfeldlab.paintera.meshes.Interruptible;
import org.janelia.saalfeldlab.paintera.meshes.MeshGenerator.ShapeKey;
import org.janelia.saalfeldlab.paintera.meshes.marchingcubes.MarchingCubes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class MeshCacheLoader< T > implements CacheLoader< ShapeKey, Pair< float[], float[] > >, Interruptible< ShapeKey >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final int[] cubeSize;

	private final RandomAccessibleInterval< T > data;

	private final LongFunction< Converter< T, BoolType > > getMaskGenerator;

	private final AffineTransform3D transform;

	private final List< Consumer< ShapeKey > > interruptListeners = new ArrayList<>();

	public MeshCacheLoader(
			final int[] cubeSize,
			final RandomAccessibleInterval< T > data,
			final LongFunction< Converter< T, BoolType > > getMaskGenerator,
			final AffineTransform3D transform )
	{
		super();
		this.cubeSize = cubeSize;
		this.data = data;
		this.getMaskGenerator = getMaskGenerator;
		this.transform = transform;
	}

	@Override
	public void interruptFor( final ShapeKey key )
	{
		synchronized ( interruptListeners )
		{
			interruptListeners.forEach( l -> l.accept( key ) );
		}
	}

	@Override
	public Pair< float[], float[] > get( final ShapeKey key ) throws Exception
	{

//		if ( key.meshSimplificationIterations() > 0 )
//		{
		// TODO deal with mesh simplification
//		}

		final RandomAccessibleInterval< BoolType > mask = Converters.convert( data, getMaskGenerator.apply( key.shapeId() ), new BoolType( false ) );

		final boolean[] isInterrupted = new boolean[] { false };
		final Consumer< ShapeKey > listener = interruptedKey -> {
			if ( interruptedKey.equals( key ) )
				isInterrupted[ 0 ] = true;
		};
		synchronized ( interruptListeners )
		{
			interruptListeners.add( listener );
		}

		try
		{
			final float[] mesh = new MarchingCubes<>(
					Views.extendZero( mask ),
					Intervals.expand( key.interval(), Arrays.stream( cubeSize ).mapToLong( size -> size ).toArray() ),
					transform,
					cubeSize,
					() -> isInterrupted[ 0 ] ).generateMesh();
			final float[] normals = new float[ mesh.length ];
			MarchingCubes.averagedSurfaceNormals( mesh, normals );
			for ( int i = 0; i < normals.length; ++i )
				normals[ i ] *= -1;
			return new ValuePair<>( mesh, normals );
		}
		finally
		{
			synchronized ( interruptListeners )
			{
				interruptListeners.remove( listener );
			}
		}
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
