package bdv.bigcat.viewer.viewer3d.util;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.viewer3d.marchingCubes.ForegroundCheck;
import bdv.bigcat.viewer.viewer3d.marchingCubes.MarchingCubes;
import graphics.scenery.Mesh;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;

/**
 * This class is responsible for generate region of interests (map of
 * boundaries).
 *
 * This class Will call the VolumePartitioner. It contains a callNext method (it
 * uses the chunk index based on x, y and z) that calls the mesh creation for
 * the next chunk.
 *
 * @author vleite
 * @param <T>
 *            Can be either LabelMultisetType or IntegerType<I>
 */
public class MeshExtractor< T >
{
	/** logger */
	static final Logger LOGGER = LoggerFactory.getLogger( MeshExtractor.class );

	static final int MAX_CUBE_SIZE = 16;

	private final RandomAccessible< T > volumeLabels;

	private final Interval interval;

	private final int[] partitionSize;

	private final int[] cubeSize;

	private final ForegroundCheck< T > isForeground;

	private final Map< Future< float[] >, Chunk< T > > resultMeshMap;

	private final CompletionService< float[] > executor;

	private final VolumePartitioner< T > partitioner;

	public MeshExtractor(
			final RandomAccessible< T > volumeLabels,
			final Interval interval,
			final int[] partitionSize,
			final int[] cubeSize,
			final Localizable startingPoint,
			final ForegroundCheck< T > isForeground )
	{
		this.volumeLabels = volumeLabels;
		this.interval = interval;
		this.partitionSize = partitionSize;
		this.cubeSize = cubeSize;
		this.isForeground = isForeground;

		executor = new ExecutorCompletionService<>( Executors.newWorkStealingPool() );

		resultMeshMap = new HashMap<>();

		partitioner = new VolumePartitioner<>( this.interval, this.partitionSize );

		final long[] offset = new long[ startingPoint.numDimensions() ];
		startingPoint.localize( offset );
		partitioner.getVolumeOffset( offset );
		createChunk( new Point( offset ) );
	}

	public boolean hasNext()
	{
		LOGGER.trace( "There is/are {} threads to calculate the chunk mesh", resultMeshMap.size() );
		return resultMeshMap.size() > 0;
	}

	public Optional< Mesh > next()
	{
		// System.out.println( "next method" );
		Future< float[] > completedFuture = null;
		// block until any task completes
		try
		{
			completedFuture = executor.take();
			if ( LOGGER.isTraceEnabled() )
				LOGGER.trace( "task " + completedFuture + " is ready: " + completedFuture.isDone() );
		}
		catch ( final InterruptedException e )
		{
			LOGGER.error( " task interrupted: " + e.getCause() );
		}

		// System.out.println( "get completed future" );

		final Chunk< T > chunk = resultMeshMap.remove( completedFuture );

		// get the mesh, if the task was able to create it
		Optional< float[] > verticesOptional;
		try
		{
			verticesOptional = Optional.of( completedFuture.get() );
			LOGGER.info( "getting mesh" );
		}
		catch ( InterruptedException | ExecutionException e )
		{
			LOGGER.error( "Mesh creation failed: " + e.getCause() );
			return Optional.empty();
		}

		final Optional< Mesh > sceneryMesh;
		if ( verticesOptional.isPresent() )
		{
			final Mesh mesh = new Mesh();
			final float[] vertices = verticesOptional.get();
			final int numVertices = vertices.length / 3;
			updateMesh( vertices, mesh );
			sceneryMesh = Optional.of( mesh );

			if ( numVertices > 0 )
			{
				final long index = chunk.index();
				final long[] offset = new long[ 3 ];
				partitioner.indexToGridOffset( index, offset );

				LOGGER.debug( "chunk {} ", chunk );

				final Localizable newLocation = new Point( offset );
				createNeighboringChunks( newLocation );
			}
		}
		else
			return Optional.empty();

		// a mesh was created, return it
		return sceneryMesh;
	}

	/**
	 * Given a initial position, creates the chunks for the position and for its
	 * six neighbors
	 *
	 * @param position
	 *            x, y, z coordinates
	 */
	private void createNeighboringChunks( final Localizable location )
	{
		if ( location == null )
		{
			LOGGER.info( "The initial position to create the chunk is null" );
			return;
		}

		final long[] offset = new long[ location.numDimensions() ];
		location.localize( offset );

		final Point p = Point.wrap( offset );

		LOGGER.trace( "Initial position is: {}, {}, {}", offset[ 0 ], offset[ 1 ], offset[ 2 ] );

		boolean[] neighboring = new boolean[ interval.numDimensions() * 2];
		hasNeighboringData( location, neighboring );

		System.out.println( "Initial position is: " + offset[ 0 ] + " " + offset[ 1 ] + " " + offset[ 2 ] );
		for ( int i = 0; i < neighboring.length; i++ )
			System.out.println( "[i]: " + neighboring[ i ] );

		// creates the callable for the chunk in the given position
		// if one of the neighbors chunks exists, creates it
		for ( int d = 0; d < offset.length; ++d )
		{
			offset[ d ] += 1;
			LOGGER.trace( "New offset: {}, {}, {}", offset[ 0 ], offset[ 1 ], offset[ 2 ] );
			System.out.println( "New offset +: " + offset[ 0 ] + " " + offset[ 1 ] + " " + offset[ 2 ] );
			if ( partitioner.isGridOffsetContained( offset[ d ], d ) && !partitioner.isChunkPresent( p ) && neighboring[ d * 2 ] )
				createChunk( p );
			offset[ d ] -= 2;
			System.out.println( "New offset -: " + offset[ 0 ] + " " + offset[ 1 ] + " " + offset[ 2 ] );
			LOGGER.trace( "New offset: {}, {}, {}", offset[ 0 ], offset[ 1 ], offset[ 2 ] );
			if ( partitioner.isGridOffsetContained( offset[ d ], d ) && !partitioner.isChunkPresent( p ) && neighboring[ d * 2 + 1 ] )
				createChunk( p );
			offset[ d ] += 1;
		}

		LOGGER.trace( "There is/are {} threads to calculate chunk mesh", resultMeshMap.size() );
	}

	private void createChunk( final Localizable offset )
	{
		final Pair< Chunk< T >, Boolean > chunk = partitioner.getChunk( offset );
		if ( chunk.getB() )
			createCallable( chunk.getA() );
	}

	private void createCallable( final Chunk< T > chunk )
	{
		final Callable< float[] > callable = () -> new MarchingCubes<>( volumeLabels, chunk.interval(), cubeSize ).generateMesh( isForeground );
		final Future< float[] > result = executor.submit( callable );
		resultMeshMap.put( result, chunk );
	}

	/**
	 * this method convert the viewer mesh into the scenery mesh
	 *
	 * @param vertices
	 *            mesh information to be converted in a mesh for scenery
	 * @param sceneryMesh
	 *            scenery mesh that will receive the information
	 */
	public void updateMesh( final float[] vertices, final Mesh sceneryMesh )
	{

		final float maxX = interval.dimension( 0 ) - 1;
		final float maxY = interval.dimension( 1 ) - 1;
		final float maxZ = interval.dimension( 2 ) - 1;

		final float maxAxisVal = Math.max( maxX, Math.max( maxY, maxZ ) );
		// omp parallel for
		for ( int i = 0; i < vertices.length; i++ )
			vertices[ i ] /= maxAxisVal;

		sceneryMesh.setVertices( FloatBuffer.wrap( vertices ) );
	}

	private void hasNeighboringData( Localizable location, boolean[] neighboring )
	{
		// for each dimension, verifies first in the +, then in the - direction
		// if the voxels in the boundary contain the foregroundvalue
		final Interval chunkInterval = partitioner.getChunk( location ).getA().interval();
		for ( int i = 0; i < chunkInterval.numDimensions(); i++ )
		{
			// initialize each direction with false
			neighboring[ i * 2 ] = false;
			neighboring[ i * 2 + 1 ] = false;

			checkData( i, chunkInterval, neighboring, "+" );
			checkData( i, chunkInterval, neighboring, "-" );
		}
	}

	private void checkData( int i, Interval chunkInterval, boolean[] neighboring, String direction )
	{
		final long[] begin = new long[ chunkInterval.numDimensions() ];
		final long[] end = new long[ chunkInterval.numDimensions() ];

		begin[ i ] = ( direction.compareTo( "+" ) == 0 ) ? chunkInterval.max( i ) : chunkInterval.min( i );
		end[ i ] = begin[ i ];

		for ( int j = 0; j < chunkInterval.numDimensions(); j++ )
		{
			if ( i == j )
				continue;

			begin[ j ] = chunkInterval.min( j );
			end[ j ] = chunkInterval.max( j );
		}

		RandomAccessibleInterval< T > slice = Views.interval( volumeLabels, new FinalInterval( begin, end ) );
		Cursor< T > cursor = Views.flatIterable( slice ).cursor();

		System.out.println( "Checking dataset from: " + begin[ 0 ] + " " + begin[ 1 ] + " " + begin[ 2 ] + " to " + end[ 0 ] + " " + end[ 1 ] + " " + end[ 2 ] );

		while ( cursor.hasNext() )
		{
			cursor.next();
			if ( isForeground.test( cursor.get() ) == 1 )
			{
				int index = ( direction.compareTo( "+" ) == 0 ) ? i * 2 : i * 2 + 1;
				neighboring[ index ] = true;
				break;
			}
		}
		int index = ( direction.compareTo( "+" ) == 0 ) ? i * 2 : i * 2 + 1;
		System.out.println( "this dataset is: " + neighboring[ index ] );
	}
}
