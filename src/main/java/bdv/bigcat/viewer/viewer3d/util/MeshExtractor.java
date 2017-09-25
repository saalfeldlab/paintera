package bdv.bigcat.viewer.viewer3d.util;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.viewer3d.marchingCubes.MarchingCubes;
import bdv.bigcat.viewer.viewer3d.marchingCubes.MarchingCubesCallable;
import bdv.labels.labelset.LabelMultisetType;
import graphics.scenery.Mesh;
import net.imglib2.RandomAccessibleInterval;

/**
 * This class is responsible for generate region of interests (map of
 * boundaries).
 * 
 * Will call the VolumePartitioner must have a callNext method (use chunk index
 * based on x, y and z)
 * 
 * @author vleite
 *
 */
public class MeshExtractor
{
	/** logger */
	static final Logger LOGGER = LoggerFactory.getLogger( MeshExtractor.class );

	static final int MAX_CUBE_SIZE = 16;

	private RandomAccessibleInterval< LabelMultisetType > volumeLabels;

	int[] partitionSize;

	private int[] cubeSize;

	private int foregroundValue;

	private int nCellsX, nCellsY, nCellsZ;

	private MarchingCubes.ForegroundCriterion criterion;

	private static Map< Future< SimpleMesh >, Chunk > resultMeshMap = null;

	private static Map< Chunk, int[] > chunkResolutionMap = null;

	private static CompletionService< SimpleMesh > executor = null;

	private static VolumePartitioner partitioner;

	public MeshExtractor( RandomAccessibleInterval< LabelMultisetType > volumeLabels, final int[] cubeSize, final int foregroundValue, final MarchingCubes.ForegroundCriterion criterion )
	{
		this.volumeLabels = volumeLabels;
		this.partitionSize = new int[] { 1, 1, 1 };
		this.cubeSize = cubeSize;
		this.foregroundValue = foregroundValue;
		this.criterion = criterion;

		executor = new ExecutorCompletionService< SimpleMesh >(
				Executors.newWorkStealingPool() );

		resultMeshMap = new HashMap< Future< SimpleMesh >, Chunk >();

		chunkResolutionMap = new HashMap< Chunk, int[] >();

		generatePartitionSize();

		nCellsX = ( int ) Math.ceil( ( volumeLabels.dimension( 0 ) ) / partitionSize[ 0 ] );
		nCellsY = ( int ) Math.ceil( ( volumeLabels.dimension( 1 ) ) / partitionSize[ 1 ] );
		nCellsZ = ( int ) Math.ceil( ( volumeLabels.dimension( 2 ) ) / partitionSize[ 2 ] );

		if ( volumeLabels.dimension( 0 ) % partitionSize[ 0 ] == 0 )
			nCellsX--;
		if ( volumeLabels.dimension( 1 ) % partitionSize[ 1 ] == 0 )
			nCellsY--;
		if ( volumeLabels.dimension( 2 ) % partitionSize[ 2 ] == 0 )
			nCellsZ--;

		partitioner = new VolumePartitioner( this.volumeLabels, partitionSize, this.cubeSize );
	}

	public void setCubeSize( int[] cubeSize )
	{
		this.cubeSize = cubeSize;
		partitioner.setOverlapSize( cubeSize );
	}

	public boolean hasNext()
	{
		LOGGER.trace( "There is/are {} threads to calculate the chunk mesh", resultMeshMap.size() );
		return resultMeshMap.size() > 0;
	}

	public Mesh next()
	{
		// System.out.println( "next method" );
		Future< SimpleMesh > completedFuture = null;
		// block until any task completes
		try
		{
			completedFuture = executor.take();
			if ( LOGGER.isTraceEnabled() )
			{
				LOGGER.trace( "task " + completedFuture + " is ready: " + completedFuture.isDone() );
			}
		}
		catch ( InterruptedException e )
		{
			// TODO Auto-generated catch block
			LOGGER.error( " task interrupted: " + e.getCause() );
		}

		// System.out.println( "get completed future" );

		Chunk chunk = resultMeshMap.remove( completedFuture );
		SimpleMesh m = new SimpleMesh();

		// get the mesh, if the task was able to create it
		try
		{
			m = completedFuture.get();
			LOGGER.info( "getting mesh" );
		}
		catch ( InterruptedException | ExecutionException e )
		{
			LOGGER.error( "Mesh creation failed: " + e.getCause() );
		}

		// System.out.println( "get mesh" );

		Mesh sceneryMesh = null;
		if ( m.getNumberOfVertices() == 0 )
		{
			LOGGER.info( "empty mesh" );
			// System.out.println( "empty mesh" );
		}
		else
		{
			sceneryMesh = new Mesh();
			updateMesh( m, sceneryMesh );
			chunk.setMesh( sceneryMesh, cubeSize );
		}

		// System.out.println( "new positions" );
		int index = chunk.getIndex();
		int xWidth = ( int ) volumeLabels.dimension( 0 );
		int xyWidth = ( int ) ( volumeLabels.dimension( 1 ) * xWidth );
		int z = index / xyWidth;
		index -= ( z * xyWidth );
		int y = index / xWidth;
		index -= ( y * xWidth );
		int x = index;
		int[] newPosition = new int[] { x * ( partitionSize[ 0 ] + 1 ), y * ( partitionSize[ 1 ] + 1 ), z * ( partitionSize[ 2 ] + 1 ) };

		if ( LOGGER.isDebugEnabled() )
		{
			LOGGER.debug( "chunk " + chunk );
			LOGGER.debug( "chunk number of meshes resolutions: " + chunk.getNumberOfMeshResolutions() );
		}

		createChunks( newPosition );
		// System.out.println( "new chunks created" );

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
	public void createChunks( int[] position )
	{
		if ( position == null )
		{
			LOGGER.info( "Given position to create a chunk is null" );
			return;
		}

		long[] offset = partitioner.getVolumeOffset( position );
		LOGGER.trace( "offset: {}, {}, {}", offset[ 0 ], offset[ 1 ], offset[ 2 ] );

		int[] newPosition = null;
		if ( offset[ 0 ] >= 0 && offset[ 0 ] < nCellsX && offset[ 1 ] >= 0 && offset[ 1 ] < nCellsY && offset[ 2 ] >= 0 && offset[ 2 ] < nCellsZ )
			newPosition = new int[] { ( int ) ( offset[ 0 ] * ( partitionSize[ 0 ] + 1 ) ), ( int ) ( offset[ 1 ] * ( partitionSize[ 1 ] + 1 ) ), ( int ) ( offset[ 2 ] * ( partitionSize[ 2 ] + 1 ) ) };
		else
		{
			LOGGER.debug( "The offset does not fit in the data" );
			return;
		}

		LOGGER.trace( "Initial position is: {}, {}, {}", newPosition[ 0 ], newPosition[ 1 ], newPosition[ 2 ] );

		// creates the callable for the chunk in the given position
		createChunk( newPosition );

		// if one of the neighbors chunks exist, creates it
		// newOffsetposition = x + partitionSizeX, y, z
		int[] newOffset = new int[] { ( int ) ( offset[ 0 ] + 1 ), ( int ) offset[ 1 ], ( int ) offset[ 2 ] };
		LOGGER.trace( "New offset: {}, {}, {}", offset[ 0 ], offset[ 1 ], offset[ 2 ] );

		if ( newOffset[ 0 ] < nCellsX )
		{
			newPosition = new int[] {
					( int ) ( newOffset[ 0 ] * ( partitionSize[ 0 ] + 1 ) ),
					( int ) ( newOffset[ 1 ] * ( partitionSize[ 1 ] + 1 ) ),
					( int ) ( newOffset[ 2 ] * ( partitionSize[ 2 ] + 1 ) ) };
			createChunk( newPosition );
		}

		// position = x - partitionSizeX, y, z
		newOffset = new int[] { ( int ) ( offset[ 0 ] - 1 ), ( int ) offset[ 1 ], ( int ) offset[ 2 ] };
		LOGGER.trace( "New offset: {}, {}, {}", offset[ 0 ], offset[ 1 ], offset[ 2 ] );

		if ( newOffset[ 0 ] >= 0 )
		{
			newPosition = new int[] {
					( int ) ( newOffset[ 0 ] * ( partitionSize[ 0 ] + 1 ) ),
					( int ) ( newOffset[ 1 ] * ( partitionSize[ 1 ] + 1 ) ),
					( int ) ( newOffset[ 2 ] * ( partitionSize[ 2 ] + 1 ) ) };
			createChunk( newPosition );
		}

		// position = x, y + partitionSizeY, z
		newOffset = new int[] { ( int ) offset[ 0 ], ( int ) ( offset[ 1 ] + 1 ), ( int ) offset[ 2 ] };
		LOGGER.trace( "New offset: {}, {}, {}", offset[ 0 ], offset[ 1 ], offset[ 2 ] );

		if ( newOffset[ 1 ] < nCellsY )
		{
			newPosition = new int[] {
					( int ) ( newOffset[ 0 ] * ( partitionSize[ 0 ] + 1 ) ),
					( int ) ( newOffset[ 1 ] * ( partitionSize[ 1 ] + 1 ) ),
					( int ) ( newOffset[ 2 ] * ( partitionSize[ 2 ] + 1 ) ) };
			createChunk( newPosition );
		}

		// position = x, y - partitionSizeY, z
		newOffset = new int[] { ( int ) offset[ 0 ], ( int ) ( offset[ 1 ] - 1 ), ( int ) offset[ 2 ] };
		LOGGER.trace( "New offset: {}, {}, {}", offset[ 0 ], offset[ 1 ], offset[ 2 ] );

		if ( newOffset[ 1 ] >= 0 )
		{
			newPosition = new int[] {
					( int ) ( newOffset[ 0 ] * ( partitionSize[ 0 ] + 1 ) ),
					( int ) ( newOffset[ 1 ] * ( partitionSize[ 1 ] + 1 ) ),
					( int ) ( newOffset[ 2 ] * ( partitionSize[ 2 ] + 1 ) ) };
			createChunk( newPosition );
		}

		// position = x, y, z + partitionSizeZ
		newOffset = new int[] { ( int ) offset[ 0 ], ( int ) offset[ 1 ], ( int ) offset[ 2 ] + 1 };
		LOGGER.trace( "New offset: {}, {}, {}", offset[ 0 ], offset[ 1 ], offset[ 2 ] );

		if ( newOffset[ 2 ] < nCellsZ )
		{
			newPosition = new int[] {
					( int ) ( newOffset[ 0 ] * ( partitionSize[ 0 ] + 1 ) ),
					( int ) ( newOffset[ 1 ] * ( partitionSize[ 1 ] + 1 ) ),
					( int ) ( newOffset[ 2 ] * ( partitionSize[ 2 ] + 1 ) ) };
			createChunk( newPosition );
		}

		// position = x, y, z - partitionSizeZ
		newOffset = new int[] { ( int ) offset[ 0 ], ( int ) offset[ 1 ], ( int ) offset[ 2 ] - 1 };
		LOGGER.trace( "New offset: {}, {}, {}", offset[ 0 ], offset[ 1 ], offset[ 2 ] );

		if ( newOffset[ 2 ] >= 0 )
		{
			newPosition = new int[] {
					( int ) ( newOffset[ 0 ] * ( partitionSize[ 0 ] + 1 ) ),
					( int ) ( newOffset[ 1 ] * ( partitionSize[ 1 ] + 1 ) ),
					( int ) ( newOffset[ 2 ] * ( partitionSize[ 2 ] + 1 ) ) };
			createChunk( newPosition );
		}

		LOGGER.trace( "There is/are {} threads to calculate chunk mesh", resultMeshMap.size() );
	}

	private void createChunk( int[] position )
	{
		Chunk chunk = partitioner.getChunk( position );
		if ( LOGGER.isTraceEnabled() )
		{
			int[] chunkBb = chunk.getChunkBoundinBox();
			LOGGER.trace( "position: " + position[ 0 ] + " " + position[ 1 ] + " " + position[ 2 ] );
			LOGGER.trace( "adding in the set: " + chunkBb[ 0 ] + " " + chunkBb[ 1 ] + " " + chunkBb[ 2 ] );
			LOGGER.trace( " to " + chunkBb[ 3 ] + " " + chunkBb[ 4 ] + " " + chunkBb[ 5 ] );
		}

		// if the chunk was added to the callable for an specific cube size, do
		// not add it again
		if ( !chunkResolutionMap.isEmpty() && chunkResolutionMap.containsKey( chunk ) )
		{
			int[] resolution = chunkResolutionMap.get( chunk );

			if ( resolution[ 0 ] == cubeSize[ 0 ] && resolution[ 1 ] == cubeSize[ 1 ] && resolution[ 2 ] == cubeSize[ 2 ] )
			{
				LOGGER.debug( "chunk already processed" );
				return;
			}
		}

		createCallable( chunk );
	}

	private void createCallable( Chunk chunk )
	{
		int[] volumeDimension = new int[] { ( int ) chunk.getVolume().dimension( 0 ), ( int ) chunk.getVolume().dimension( 1 ),
				( int ) chunk.getVolume().dimension( 2 ) };

		MarchingCubesCallable callable = new MarchingCubesCallable( chunk.getVolume(), volumeDimension, chunk.getOffset(), cubeSize, criterion, foregroundValue,
				true );

		Future< SimpleMesh > result = executor.submit( callable );

		resultMeshMap.put( result, chunk );
		chunkResolutionMap.put( chunk, new int[] { cubeSize[ 0 ], cubeSize[ 1 ], cubeSize[ 2 ] } );
	}

	private void generatePartitionSize()
	{
		for ( int i = 0; i < partitionSize.length; i++ )
		{
			partitionSize[ i ] = ( int ) ( ( volumeLabels.max( i ) - volumeLabels.min( i ) ) + 1 ) / MAX_CUBE_SIZE;
		}

		LOGGER.trace( "division: {}, {}, {}", partitionSize[ 0 ], partitionSize[ 1 ], partitionSize[ 2 ] );

		for ( int i = 0; i < partitionSize.length; i++ )
		{
			partitionSize[ i ] = partitionSize[ i ] >= 7 ? 7 * MAX_CUBE_SIZE : partitionSize[ i ] * MAX_CUBE_SIZE;
		}

		LOGGER.trace( "partition size: {}, {}, {}", partitionSize[ 0 ], partitionSize[ 1 ], partitionSize[ 2 ] );

		for ( int i = 0; i < partitionSize.length; i++ )
		{
			partitionSize[ i ] = ( partitionSize[ i ] == 0 ) ? 1 : partitionSize[ i ];
		}

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
	public void updateMesh( SimpleMesh mesh, Mesh sceneryMesh )
	{
		float[] verticesArray = new float[ mesh.getNumberOfVertices() * 3 ];

		float[][] vertices = mesh.getVertices();
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

		float maxAxisVal = Math.max( maxX, Math.max( maxY, maxZ ) );
		// omp parallel for
		for ( int i = 0; i < verticesArray.length; i++ )
		{
			verticesArray[ i ] /= maxAxisVal;
		}

		sceneryMesh.setVertices( FloatBuffer.wrap( verticesArray ) );
	}

	private void initialize()
	{

	}
}
