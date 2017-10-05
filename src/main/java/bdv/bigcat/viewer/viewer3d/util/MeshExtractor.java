package bdv.bigcat.viewer.viewer3d.util;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.viewer3d.marchingCubes.MarchingCubes;
import bdv.bigcat.viewer.viewer3d.marchingCubes.MarchingCubesCallable;
import graphics.scenery.Mesh;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;

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

	private final RandomAccessibleInterval< T > volumeLabels;

	private final int[] partitionSize;

	private final int[] cubeSize;

	private final int foregroundValue;

	private final MarchingCubes.ForegroundCriterion criterion;

	private final Map< Future< SimpleMesh >, Chunk< T > > resultMeshMap;

	private final CompletionService< SimpleMesh > executor;

	private final VolumePartitioner< T > partitioner;

	public MeshExtractor(
			final RandomAccessibleInterval< T > volumeLabels,
			final int[] cubeSize,
			final int foregroundValue,
			final MarchingCubes.ForegroundCriterion criterion,
			final Localizable startingPoint )
	{
		this.volumeLabels = volumeLabels;
		this.partitionSize = new int[] { 1, 1, 1 };
		this.cubeSize = cubeSize;
		this.foregroundValue = foregroundValue;
		this.criterion = criterion;

		executor = new ExecutorCompletionService<>( Executors.newWorkStealingPool() );

		resultMeshMap = new HashMap<>();

		generatePartitionSize();

		partitioner = new VolumePartitioner<>( this.volumeLabels, partitionSize, this.cubeSize );

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
		Optional< SimpleMesh > m;
		try
		{
			m = Optional.of( completedFuture.get() );
			LOGGER.info( "getting mesh" );
		}
		catch ( InterruptedException | ExecutionException e )
		{
			LOGGER.error( "Mesh creation failed: " + e.getCause() );
			m = Optional.empty();
		}

		final Optional< Mesh > sceneryMesh;
		if ( m.isPresent() )
		{
			final Mesh mesh = new Mesh();
			updateMesh( m.get(), mesh );
			chunk.addMesh( mesh );
			sceneryMesh = Optional.of( mesh );
		}
		else
			sceneryMesh = Optional.empty();

		long index = chunk.getIndex();
		final long xWidth = volumeLabels.dimension( 0 );
		final long xyWidth = volumeLabels.dimension( 1 ) * xWidth;
		final long z = index / xyWidth;
		index -= z * xyWidth;
		final long y = index / xWidth;
		index -= y * xWidth;
		final long x = index;
		final long[] newPosition = new long[] { x * ( partitionSize[ 0 ] + 1 ), y * ( partitionSize[ 1 ] + 1 ), z * ( partitionSize[ 2 ] + 1 ) };
//		final long[] offset = new long[ 3 ];
//		partitioner.indexToGridOffset( index, offset );
//		final long[] shiftedOffset = Arrays.stream( offset ).map( o -> o + 1 ).toArray();

		LOGGER.debug( "chunk {} ", chunk );

		final Localizable newLocation = new Point( newPosition );
		createNeighboringChunks( newLocation );

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
		System.out.println( "CREATING CHUNKS AT " + location );
		if ( location == null )
		{
			LOGGER.info( "The initial position to create the chunk is null" );
			return;
		}

		final long[] offset = new long[ location.numDimensions() ];
		location.localize( offset );
//		partitioner.getVolumeOffset( offset );
		LOGGER.trace( "offset: {}, {}, {}", offset[ 0 ], offset[ 1 ], offset[ 2 ] );
		if ( !partitioner.isGridOffsetContained( offset ) )
		{
			LOGGER.debug( "The offset does not fit in the data" );
			return;
		}

		final Point p = Point.wrap( offset );

		LOGGER.trace( "Initial position is: {}, {}, {}", offset[ 0 ], offset[ 1 ], offset[ 2 ] );

		// creates the callable for the chunk in the given position
		createChunk( p );

		// if one of the neighbors chunks exist, creates it
		// newOffsetposition = x + partitionSizeX, y, z
		for ( int d = 0; d < offset.length; ++d )
		{

			offset[ d ] += 1;
			LOGGER.trace( "New offset: {}, {}, {}", offset[ 0 ], offset[ 1 ], offset[ 2 ] );
			if ( partitioner.isGridOffsetContained( offset[ d ], d ) )
				createChunk( p );
			offset[ d ] -= 2;
			LOGGER.trace( "New offset: {}, {}, {}", offset[ 0 ], offset[ 1 ], offset[ 2 ] );
			if ( partitioner.isGridOffsetContained( offset[ d ], d ) )
				createChunk( p );
			offset[ d ] += 1;

		}

		LOGGER.trace( "There is/are {} threads to calculate chunk mesh", resultMeshMap.size() );
	}

	private void createChunk( final Localizable offset )
	{
		final Chunk< T > chunk = partitioner.getChunk( offset );
		createCallable( chunk );
	}

	private void createCallable( final Chunk< T > chunk )
	{
		final int[] volumeDimension = new int[] { ( int ) chunk.getVolume().dimension( 0 ), ( int ) chunk.getVolume().dimension( 1 ),
				( int ) chunk.getVolume().dimension( 2 ) };

		final MarchingCubesCallable< T > callable = new MarchingCubesCallable<>( chunk.getVolume(), volumeDimension, chunk.getOffset(),
				cubeSize, criterion, foregroundValue, true );
		final Future< SimpleMesh > result = executor.submit( callable );

		resultMeshMap.put( result, chunk );
	}

	private void generatePartitionSize()
	{
		for ( int i = 0; i < partitionSize.length; i++ )
			partitionSize[ i ] = ( int ) ( volumeLabels.max( i ) - volumeLabels.min( i ) + 1 ) / MAX_CUBE_SIZE;

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

		final float maxX = volumeLabels.dimension( 0 ) - 1;
		final float maxY = volumeLabels.dimension( 1 ) - 1;
		final float maxZ = volumeLabels.dimension( 2 ) - 1;

		final float maxAxisVal = Math.max( maxX, Math.max( maxY, maxZ ) );
		// omp parallel for
		for ( int i = 0; i < verticesArray.length; i++ )
			verticesArray[ i ] /= maxAxisVal;

		sceneryMesh.setVertices( FloatBuffer.wrap( verticesArray ) );
	}
}
