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
import java.util.function.ToIntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.viewer3d.marchingCubes.MarchingCubes;
import graphics.scenery.Mesh;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccessible;
import net.imglib2.util.Pair;

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

	private final ToIntFunction< T > isForeground;

	private final Map< Future< SimpleMesh >, Chunk< T > > resultMeshMap;

	private final CompletionService< SimpleMesh > executor;

	private final VolumePartitioner< T > partitioner;

	public MeshExtractor(
			final RandomAccessible< T > volumeLabels,
			final Interval interval,
			final int[] partitionSize,
			final int[] cubeSize,
			final Localizable startingPoint,
			final ToIntFunction< T > isForeground )
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
		Future< SimpleMesh > completedFuture = null;
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
		Optional< SimpleMesh > simpleMesh;
		try
		{
			simpleMesh = Optional.of( completedFuture.get() );
			LOGGER.info( "getting mesh" );
		}
		catch ( InterruptedException | ExecutionException e )
		{
			LOGGER.error( "Mesh creation failed: " + e.getCause() );
			return Optional.empty();
		}

		final Optional< Mesh > sceneryMesh;
		if ( simpleMesh.isPresent() )
		{
			final Mesh mesh = new Mesh();
			final SimpleMesh m = simpleMesh.get();
			final int numVertices = m.getNumberOfVertices();
			updateMesh( m, mesh );
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

		// creates the callable for the chunk in the given position

		// if one of the neighbors chunks exists, creates it
		for ( int d = 0; d < offset.length; ++d )
		{
			offset[ d ] += 1;
			LOGGER.trace( "New offset: {}, {}, {}", offset[ 0 ], offset[ 1 ], offset[ 2 ] );
			if ( partitioner.isGridOffsetContained( offset[ d ], d ) && !partitioner.isChunkPresent( p ) )
				createChunk( p );
			offset[ d ] -= 2;
			LOGGER.trace( "New offset: {}, {}, {}", offset[ 0 ], offset[ 1 ], offset[ 2 ] );
			if ( partitioner.isGridOffsetContained( offset[ d ], d ) && !partitioner.isChunkPresent( p ) )
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
		final Callable< SimpleMesh > callable = () -> new MarchingCubes<>( volumeLabels, chunk.interval(), cubeSize ).generateMesh( isForeground );
		final Future< SimpleMesh > result = executor.submit( callable );
		resultMeshMap.put( result, chunk );
	}

	private void generatePartitionSize()
	{
		for ( int i = 0; i < partitionSize.length; i++ )
			partitionSize[ i ] = ( int ) ( interval.max( i ) - interval.min( i ) + 1 ) / MAX_CUBE_SIZE;

		LOGGER.trace( "division: {}, {}, {}", partitionSize[ 0 ], partitionSize[ 1 ], partitionSize[ 2 ] );

		for ( int i = 0; i < partitionSize.length; i++ )
			partitionSize[ i ] = partitionSize[ i ] >= 7 ? 7 * MAX_CUBE_SIZE : partitionSize[ i ] * MAX_CUBE_SIZE;

		LOGGER.trace( "partition size: {}, {}, {}", partitionSize[ 0 ], partitionSize[ 1 ], partitionSize[ 2 ] );

		for ( int i = 0; i < partitionSize.length; i++ )
			partitionSize[ i ] = partitionSize[ i ] == 0 ? 1 : partitionSize[ i ];

		LOGGER.info( "final partition size: {}, {}, {}", partitionSize[ 0 ], partitionSize[ 1 ], partitionSize[ 2 ] );
	}

	/**
	 * this method convert the viewer mesh into the scenery mesh
	 *
	 * @param mesh
	 *            mesh information to be converted in a mesh for scenery
	 * @param sceneryMesh
	 *            scenery mesh that will receive the information
	 */
	public void updateMesh( final SimpleMesh mesh, final Mesh sceneryMesh )
	{
		final float[] verticesArray = new float[ mesh.getNumberOfVertices() * 3 ];

		final float[][] vertices = mesh.getVertices();
		int v = 0;
		for ( int i = 0; i < mesh.getNumberOfVertices(); i++ )
		{
			verticesArray[ v++ ] = vertices[ i ][ 0 ];
			verticesArray[ v++ ] = vertices[ i ][ 1 ];
			verticesArray[ v++ ] = vertices[ i ][ 2 ];
		}

		final float maxX = interval.dimension( 0 ) - 1;
		final float maxY = interval.dimension( 1 ) - 1;
		final float maxZ = interval.dimension( 2 ) - 1;

		final float maxAxisVal = Math.max( maxX, Math.max( maxY, maxZ ) );
		// omp parallel for
		for ( int i = 0; i < verticesArray.length; i++ )
			verticesArray[ i ] /= maxAxisVal;

		sceneryMesh.setVertices( FloatBuffer.wrap( verticesArray ) );
	}
}
