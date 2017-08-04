package bdv.bigcat.viewer;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.FloatBuffer;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.javafx.application.PlatformImpl;

import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.labels.labelset.LabelMultisetType;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import cleargl.GLVector;
import graphics.scenery.Box;
import graphics.scenery.Camera;
import graphics.scenery.DetachedHeadCamera;
import graphics.scenery.Material;
import graphics.scenery.Mesh;
import graphics.scenery.PointLight;
import graphics.scenery.Scene;
import graphics.scenery.SceneryDefaultApplication;
import graphics.scenery.SceneryElement;
import graphics.scenery.backends.Renderer;
import graphics.scenery.utils.SceneryPanel;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import net.imglib2.RandomAccessibleInterval;

/**
 * Unit test for marching cubes
 *
 * @author vleite
 */
public class JavaFXMarchingCubesExample
{
	/** logger */
	static final Logger logger = LoggerFactory.getLogger( JavaFXMarchingCubesExample.class );

	private static RandomAccessibleInterval< LabelMultisetType > volumeLabels = null;

	Timestamp begin = new Timestamp( System.currentTimeMillis() );

	Timestamp end = new Timestamp( System.currentTimeMillis() );

	static float[] verticesArray = new float[ 0 ];

	/** small hdf5 for test - subset from sample B */
	static String path = "data/sample_B_20160708_frags_46_50.hdf";

	static int foregroundValue = 7;

	static int[] volDim = { 500, 500, 5 };

	static String path_label = "/volumes/labels/neuron_ids";

	static int[] cubeSize = { 8, 8, 1 };

	float smallx = 0.0f;

	float smally = 0.0f;

	float smallz = 0.0f;

	float bigx = 0.0f;

	float bigy = 0.0f;

	float bigz = 0.0f;

	static PrintWriter writer = null;

	PrintWriter writer2 = null;

	static float maxAxisVal = 0;

	private static MarchingCubes.ForegroundCriterion criterion = MarchingCubes.ForegroundCriterion.EQUAL;

	/**
	 * This method loads the hdf file
	 */
	public static void loadData()
	{
		logger.info( "Opening labels from " + path );
		final IHDF5Reader reader = HDF5Factory.openForReading( path );

		/** loaded segments */
		ArrayList< H5LabelMultisetSetupImageLoader > labels = null;

		/* labels */
		if ( reader.exists( path_label ) )
			try
		{
				labels = HDF5Reader.readLabels( reader, path_label );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
		else
			logger.error( "no label dataset '" + path_label + "' found" );

		volumeLabels = labels.get( 0 ).getImage( 0 );
	}

	public static void main( final String[] args ) throws Exception
	{
		// Set the log level
		System.setProperty( org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "INFO" );
		final MarchingCubeApplication viewer = new MarchingCubeApplication( "Marching cube", 800, 600 );
		viewer.main();
	}

	private static class MarchingCubeApplication extends SceneryDefaultApplication
	{
		public MarchingCubeApplication( final String applicationName, final int windowWidth, final int windowHeight )
		{
			super( applicationName, windowWidth, windowHeight, false );
		}

		@Override
		public void init()
		{
			final CountDownLatch latch = new CountDownLatch( 1 );
			final SceneryPanel[] imagePanel = { null };

			PlatformImpl.startup( () -> {} );

			Platform.runLater( () -> {

				final Stage stage = new Stage();
				stage.setTitle( getApplicationName() );

				final StackPane pane = new StackPane();
				imagePanel[ 0 ] = new SceneryPanel( getWindowWidth(), getWindowHeight() );
				pane.getChildren().addAll( imagePanel[ 0 ] );

				final javafx.scene.Scene scene = new javafx.scene.Scene( pane );
				stage.setScene( scene );
				stage.setOnCloseRequest( event -> {
					getRenderer().setShouldClose( true );

					Platform.runLater( Platform::exit );
				} );
				stage.show();

				latch.countDown();
			} );

			try
			{
				latch.await();
			}
			catch ( final InterruptedException e1 )
			{
				e1.printStackTrace();
			}

			loadData();

			try
			{
				writer = new PrintWriter( "vertices_.txt", "UTF-8" );
			}
			catch ( final IOException e )
			{
				e.printStackTrace();
			}

			setRenderer( Renderer.Factory.createRenderer( getHub(), getApplicationName(), getScene(), getWindowWidth(),
					getWindowHeight(), imagePanel[ 0 ] ) );
			getHub().add( SceneryElement.Renderer, getRenderer() );

			final Box hull = new Box( new GLVector( 50.0f, 50.0f, 50.0f ), true );
			hull.getMaterial().setDiffuse( new GLVector( 0.5f, 0.5f, 0.5f ) );
			hull.getMaterial().setDoubleSided( true );
			getScene().addChild( hull );

			final Camera cam = new DetachedHeadCamera();

			cam.perspectiveCamera( 50f, getWindowHeight(), getWindowWidth(), 0.001f, 1000.0f );
			cam.setActive( true );
			cam.setPosition( new GLVector( 2f, 2f, 10 ) );
			getScene().addChild( cam );

			final PointLight[] lights = new PointLight[ 4 ];

			for ( int i = 0; i < lights.length; i++ )
			{
				lights[ i ] = new PointLight();
				lights[ i ].setEmissionColor( new GLVector( 1.0f, 1.0f, 1.0f ) );
				lights[ i ].setIntensity( 100.2f * 5 );
				lights[ i ].setLinear( 0.0f );
				lights[ i ].setQuadratic( 0.1f );
			}

			lights[ 0 ].setPosition( new GLVector( 1.0f, 0f, -1.0f / ( float ) Math.sqrt( 2.0 ) ) );
			lights[ 1 ].setPosition( new GLVector( -1.0f, 0f, -1.0f / ( float ) Math.sqrt( 2.0 ) ) );
			lights[ 2 ].setPosition( new GLVector( 0.0f, 1.0f, 1.0f / ( float ) Math.sqrt( 2.0 ) ) );
			lights[ 3 ].setPosition( new GLVector( 0.0f, -1.0f, 1.0f / ( float ) Math.sqrt( 2.0 ) ) );

			for ( int i = 0; i < lights.length; i++ )
				getScene().addChild( lights[ i ] );

			new Thread()
			{
				@Override
				public void run()
				{

					marchingCube( getScene() );
				}
			}.start();
		}
	}

	private static void marchingCube( final Scene scene )
	{
		int numberOfCellsX = ( int ) ( volumeLabels.max( 0 ) - volumeLabels.min( 0 ) + 1 ) / 32;
		int numberOfCellsY = ( int ) ( volumeLabels.max( 1 ) - volumeLabels.min( 1 ) + 1 ) / 32;
		int numberOfCellsZ = ( int ) ( volumeLabels.max( 2 ) - volumeLabels.min( 2 ) + 1 ) / 32;

		numberOfCellsX = numberOfCellsX >= 7 ? 7 * 32 : numberOfCellsX * 32;
		numberOfCellsY = numberOfCellsY >= 7 ? 7 * 32 : numberOfCellsY * 32;
		numberOfCellsZ = numberOfCellsZ >= 7 ? 7 * 32 : numberOfCellsZ * 32;

		numberOfCellsX = numberOfCellsX == 0 ? 1 : numberOfCellsX;
		numberOfCellsY = numberOfCellsY == 0 ? 1 : numberOfCellsY;
		numberOfCellsZ = numberOfCellsZ == 0 ? 1 : numberOfCellsZ;

		final int[] partitionSize = new int[] { numberOfCellsX, numberOfCellsY, numberOfCellsZ };

		List< Chunk > chunks = new ArrayList< >();

		CompletionService< SimpleMesh > executor = null;
		List< Future< SimpleMesh > > resultMeshList = null;
		for ( int voxSize = 32; voxSize > 0; voxSize /= 2 )
		{
			// clean the vertices, offsets and subvolumes
			verticesArray = new float[ 0 ];
			chunks.clear();

			final Mesh neuron = new Mesh();
			final Material material = new Material();
			material.setAmbient( new GLVector( 1f, 0.0f, 1f ) );
			material.setSpecular( new GLVector( 1f, 0.0f, 1f ) );

			if ( voxSize == 32 )
				material.setDiffuse( new GLVector( 1, 0, 0 ) );
			if ( voxSize == 16 )
				material.setDiffuse( new GLVector( 0, 1, 0 ) );
			if ( voxSize == 8 )
				material.setDiffuse( new GLVector( 0, 0, 1 ) );
			if ( voxSize == 4 )
				material.setDiffuse( new GLVector( 1, 0, 1 ) );
			if ( voxSize == 2 )
				material.setDiffuse( new GLVector( 0, 1, 1 ) );
			if ( voxSize == 1 )
				material.setDiffuse( new GLVector( 1, 1, 0 ) );

			neuron.setMaterial( material );
			neuron.setName( String.valueOf( foregroundValue + " " + voxSize ) );
			neuron.setPosition( new GLVector( 0.0f, 0.0f, 0.0f ) );
			neuron.setScale( new GLVector( 4.0f, 4.0f, 40.0f ) );
//			neuron.setGeometryType( GeometryType.POINTS );
			scene.addChild( neuron );
			cubeSize[ 0 ] = voxSize;
			cubeSize[ 1 ] = voxSize;
			cubeSize[ 2 ] = 1;

			final VolumePartitioner partitioner = new VolumePartitioner( volumeLabels, partitionSize, cubeSize );
			chunks = partitioner.dataPartitioning();

//			chunks.clear();
//			Chunk chunk = new Chunk();
//			chunk.setVolume( volumeLabels );
//			chunk.setOffset( new int[] { 0, 0, 0 } );
//			chunks.add( chunk );

			executor = new ExecutorCompletionService< >( Executors.newWorkStealingPool() );

			resultMeshList = new ArrayList<>();

			final float maxX = volDim[ 0 ] - 1;
			final float maxY = volDim[ 1 ] - 1;
			final float maxZ = volDim[ 2 ] - 1;

			maxAxisVal = Math.max( maxX, Math.max( maxY, maxZ ) );

			for ( int i = 0; i < chunks.size(); i++ )
			{
				final int[] subvolDim = new int[] { ( int ) chunks.get( i ).getVolume().dimension( 0 ), ( int ) chunks.get( i ).getVolume().dimension( 1 ),
						( int ) chunks.get( i ).getVolume().dimension( 2 ) };

				final MarchingCubesCallable callable = new MarchingCubesCallable( chunks.get( i ).getVolume(), subvolDim, chunks.get( i ).getOffset(), cubeSize, criterion, foregroundValue,
						true );

				final Future< SimpleMesh > result = executor.submit( callable );
				resultMeshList.add( result );
			}

			Future< SimpleMesh > completedFuture = null;

			while ( resultMeshList.size() > 0 )
			{
				// block until a task completes
				try
				{
					completedFuture = executor.take();
				}
				catch ( final InterruptedException e )
				{
					e.printStackTrace();
				}

				resultMeshList.remove( completedFuture );
				SimpleMesh m = new SimpleMesh();

				// get the mesh, if the task was able to create it
				try
				{
					m = completedFuture.get();
				}
				catch ( InterruptedException | ExecutionException e )
				{
					e.printStackTrace();
					break;
				}

				// a mesh was created, so update the existing mesh
				if ( m.getNumberOfVertices() > 0 )
				{
					updateMesh( m, neuron, false );
					neuron.setVertices( FloatBuffer.wrap( verticesArray ) );
					neuron.recalculateNormals();
					neuron.setDirty( true );
				}
			}

			writer.close();

			// Pause for 2 seconds
			try
			{
				Thread.sleep( 2000 );
			}
			catch ( final InterruptedException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if ( voxSize != 1 )
				scene.removeChild( neuron );
		}
	}

	/**
	 * this method update the mesh with new data
	 *
	 * @param m
	 *            mesh information to be converted in a mesh for scenery
	 * @param neuron
	 *            scenery mesh that will receive the information
	 * @param overwriteArray
	 *            if it is true, means that the data is processed all at once,
	 *            so, the verticesArray will be overwritten, if it is false,
	 *            means that the data is processed block-wise, this way, every
	 *            time this method is called it add more vertices to the already
	 *            existing array.
	 */
	public static void updateMesh( final SimpleMesh m, final Mesh neuron, final boolean overwriteArray )
	{
		/** max value int = 2,147,483,647 */
		final int vertexCount;
		// resize array to fit the new mesh
		if ( overwriteArray )
		{
			vertexCount = 0;
			verticesArray = new float[ m.getNumberOfVertices() * 3 ];
		}
		else
		{
			vertexCount = verticesArray.length;
			verticesArray = Arrays.copyOf( verticesArray, m.getNumberOfVertices() * 3 + vertexCount );
		}

		final float[][] vertices = m.getVertices();
		int v = 0;
		for ( int i = 0; i < m.getNumberOfVertices(); i++ )
		{
			verticesArray[ vertexCount + v++ ] = vertices[ i ][ 0 ];
			verticesArray[ vertexCount + v++ ] = vertices[ i ][ 1 ];
			verticesArray[ vertexCount + v++ ] = vertices[ i ][ 2 ];
		}

		// omp parallel for
		for ( int i = vertexCount; i < verticesArray.length; ++i )
		{
			verticesArray[ i ] /= maxAxisVal;
			writer.println( verticesArray[ i ] );
		}

		neuron.setVertices( FloatBuffer.wrap( verticesArray ) );
		neuron.recalculateNormals();
		neuron.setDirty( true );
	}
}
