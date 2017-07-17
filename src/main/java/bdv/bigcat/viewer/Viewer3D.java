package bdv.bigcat.viewer;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.labels.labelset.LabelMultisetType;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import cleargl.GLVector;
import graphics.scenery.Box;
import graphics.scenery.Camera;
import graphics.scenery.DetachedHeadCamera;
import graphics.scenery.Hub;
import graphics.scenery.Material;
import graphics.scenery.Mesh;
import graphics.scenery.PointLight;
import graphics.scenery.Scene;
import graphics.scenery.SceneryElement;
import graphics.scenery.Settings;
import graphics.scenery.backends.Renderer;
import graphics.scenery.utils.SceneryPanel;
import net.imglib2.RandomAccessibleInterval;

public class Viewer3D
{
	/** logger */
	static final Logger logger = LoggerFactory.getLogger( Viewer3D.class );
	
	/** small hdf5 for test - subset from sample B */
	static String path = "data/sample_B_20160708_frags_46_50.hdf";
	static int isoLevel = 7;
	static int[] volDim = { 500, 500, 5 };
	static String path_label = "/volumes/labels/neuron_ids";
	static float[] voxDim = { 1f, 1f, 1f };
	static float maxAxisVal = 0;
	static float[] verticesArray = new float[ 0 ];
	private boolean isReady = false;

	private static RandomAccessibleInterval< LabelMultisetType > volumeLabels = null;
	
	private SceneryPanel scPanel[] = {null};

	public Viewer3D()
	{
		
	}

	public void createViewer3D()
	{
		// this.infoPane = new Label( "info box" );
		loadData();
		
		Settings settings = new Settings();
		Hub hub = new Hub();
		graphics.scenery.Scene scene = new graphics.scenery.Scene();
		hub.add( SceneryElement.Settings, settings );
		
		Renderer renderer = Renderer.Factory.createRenderer( hub, "BigCAT", scene, 250, 250, scPanel[0] );
		hub.add( SceneryElement.Renderer, renderer );

		final Box hull = new Box( new GLVector( 50.0f, 50.0f, 50.0f ), true );
		hull.getMaterial().setDiffuse( new GLVector( 0.5f, 0.5f, 0.5f ) );
		hull.getMaterial().setDoubleSided( true );
		scene.addChild( hull );

		final Material material = new Material();
		material.setAmbient( new GLVector( 0.1f * ( 1 ), 1.0f, 1.0f ) );
		material.setDiffuse( new GLVector( 0.1f * ( 1 ), 0.0f, 1.0f ) );
		material.setSpecular( new GLVector( 0.1f * ( 1 ), 0f, 0f ) );

		final Camera cam = new DetachedHeadCamera();

		cam.perspectiveCamera( 50f, renderer.getWindow().getHeight(), renderer.getWindow().getWidth(), 0.1f, 1000.0f );
		cam.setActive( true );
		cam.setPosition( new GLVector( 0.5f, 0.5f, 5 ) );
		scene.addChild( cam );

		PointLight[] lights = new PointLight[ 4 ];

		for ( int i = 0; i < lights.length; i++ )
		{
			lights[ i ] = new PointLight();
			lights[ i ].setEmissionColor( new GLVector( 1.0f, 1.0f, 1.0f ) );
			lights[ i ].setIntensity( 100.2f * 5 );
			lights[ i ].setLinear( 0.0f );
			lights[ i ].setQuadratic( 0.1f );
			// lights[ i ].showLightBox();
		}

		lights[ 0 ].setPosition( new GLVector( 1.0f, 0f, -1.0f / ( float ) Math.sqrt( 2.0 ) ) );
		lights[ 1 ].setPosition( new GLVector( -1.0f, 0f, -1.0f / ( float ) Math.sqrt( 2.0 ) ) );
		lights[ 2 ].setPosition( new GLVector( 0.0f, 1.0f, 1.0f / ( float ) Math.sqrt( 2.0 ) ) );
		lights[ 3 ].setPosition( new GLVector( 0.0f, -1.0f, 1.0f / ( float ) Math.sqrt( 2.0 ) ) );

		for ( int i = 0; i < lights.length; i++ )
			scene.addChild( lights[ i ] );

		Mesh neuron = new Mesh();
		neuron.setMaterial( material );
		neuron.setName("neuron");
		neuron.setPosition( new GLVector( 0.0f, 0.0f, 0.0f ) );
//		neuron.setScale( new GLVector( 1.0f, 1.0f, 100.0f ) );
		scene.addChild(neuron);

		new Thread() {
			public void run() {

			marchingCube(neuron, material, scene, cam );
			}
		}.start();
		
		isReady = true;
	}
	
	public void createPanel()
	{
		scPanel[0] = new SceneryPanel( 500, 500 );
	}
	
	public static void updateMesh( SimpleMesh m, Mesh neuron )
	{
		System.out.println( "previous size of vertices: " + verticesArray.length );
		int vertexCount = verticesArray.length;

		int numberOfTriangles = m.getNumberOfTriangles();
		System.out.println( "number of triangles: " + numberOfTriangles );

		// resize array to fit the new mesh
		verticesArray = Arrays.copyOf( verticesArray, ( numberOfTriangles * 3 * 3 + vertexCount ) );
		System.out.println( "size of verticesArray: " + ( numberOfTriangles * 3 * 3 + vertexCount ) );

		float[][] vertices = m.getVertices();
		int[] triangles = m.getTriangles();

		float[] point0 = new float[ 3 ];
		float[] point1 = new float[ 3 ];
		float[] point2 = new float[ 3 ];
		int v = 0;

		for ( int i = 0; i < numberOfTriangles; i++ )
		{
			long id0 = triangles[ i * 3 ];
			long id1 = triangles[ i * 3 + 1 ];
			long id2 = triangles[ i * 3 + 2 ];

			point0 = vertices[ ( int ) id0 ];
			point1 = vertices[ ( int ) id1 ];
			point2 = vertices[ ( int ) id2 ];

			verticesArray[ vertexCount + v++ ] = point0[ 0 ];
			verticesArray[ vertexCount + v++ ] = point0[ 1 ];
			verticesArray[ vertexCount + v++ ] = point0[ 2 ];

			verticesArray[ vertexCount + v++ ] = point1[ 0 ];
			verticesArray[ vertexCount + v++ ] = point1[ 1 ];
			verticesArray[ vertexCount + v++ ] = point1[ 2 ];

			verticesArray[ vertexCount + v++ ] = point2[ 0 ];
			verticesArray[ vertexCount + v++ ] = point2[ 1 ];
			verticesArray[ vertexCount + v++ ] = point2[ 2 ];
		}

		// TODO: to define this value in a global way
		maxAxisVal = 499;

		// omp parallel for
		System.out.println( "vsize: " + verticesArray.length );
		for ( int i = vertexCount; i < verticesArray.length; ++i )
		{
			verticesArray[ i ] /= maxAxisVal;
		}

		neuron.setVertices( FloatBuffer.wrap( verticesArray ) );
		neuron.recalculateNormals();
		neuron.setDirty( true );
	}
	
	public static void loadData()
	{
		System.out.println( "Opening labels from " + path );
		final IHDF5Reader reader = HDF5Factory.openForReading( path );

		/** loaded segments */
		ArrayList< H5LabelMultisetSetupImageLoader > labels = null;

		/* labels */
		if ( reader.exists( path_label ) )
		{
			try
			{
				labels = HDF5Reader.readLabels( reader, path_label );
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			System.out.println( "no label dataset '" + path_label + "' found" );
		}

		volumeLabels = labels.get( 0 ).getImage( 0 );
	}
	
	private static void marchingCube( Mesh neuron, Material material, Scene scene, Camera cam )
	{
		SimpleMesh m = new SimpleMesh();

		int x = ( int ) ( ( volumeLabels.max( 0 ) - volumeLabels.min( 0 ) ) + 1 ) / 32;
		int y = ( int ) ( ( volumeLabels.max( 1 ) - volumeLabels.min( 1 ) ) + 1 ) / 32;
		int z = ( int ) ( ( volumeLabels.max( 2 ) - volumeLabels.min( 2 ) ) + 1 ) / 32;

		logger.trace( "division: " + x + " " + y + " " + z );

		x = ( x >= 7 ) ? 7 * 32 : x * 32;
		y = ( y >= 7 ) ? 7 * 32 : y * 32;
		z = ( z >= 7 ) ? 7 * 32 : z * 32;

		logger.trace( "partition size 1: " + x + " " + y + " " + z );

		x = ( x == 0 ) ? 1 : x;
		y = ( y == 0 ) ? 1 : y;
		z = ( z == 0 ) ? 1 : z;

		logger.trace( "zero verification: " + x + " " + y + " " + z );

		int[] partitionSize = new int[] { x, y, z };
		logger.trace( " final partition size: " + x + " " + y + " " + z );

		List< int[] > offsets = new ArrayList<>();
		VolumePartitioner partitioner = new VolumePartitioner( volumeLabels, partitionSize );
		List< RandomAccessibleInterval< LabelMultisetType > > subvolumes = partitioner.dataPartitioning( offsets );

//		subvolumes.clear();
//		subvolumes.add( volumeLabels );
//		offsets.set( 0, new int[] { 0, 0, 0 } );

		logger.info( "starting executor..." );
		CompletionService< SimpleMesh > executor = new ExecutorCompletionService< SimpleMesh >(
				Executors.newWorkStealingPool() );

		List< Future< SimpleMesh > > resultMeshList = new ArrayList<>();

		float maxX = volDim[ 0 ] - 1;
		float maxY = volDim[ 1 ] - 1;
		float maxZ = volDim[ 2 ] - 1;

		maxAxisVal = Math.max( maxX, Math.max( maxY, maxZ ) );
		logger.trace( "maxX " + maxX + " maxY: " + maxY + " maxZ: " + maxZ + " maxAxisVal: " + maxAxisVal );

		logger.info( "creating callables for " + subvolumes.size() + " partitions..." );
//		for ( int voxSize = 32; voxSize > 0; voxSize /= 2 )
//		{
//			voxDim[0] = voxSize;
//			voxDim[1] = voxSize;
//			voxDim[2] = voxSize;

		for ( int i = 0; i < subvolumes.size(); i++ )
		{
			logger.info( "dimension: " + subvolumes.get( i ).dimension( 0 ) + "x" + subvolumes.get( i ).dimension( 1 )
					+ "x" + subvolumes.get( i ).dimension( 2 ) );
			volDim = new int[] { ( int ) subvolumes.get( i ).dimension( 0 ), ( int ) subvolumes.get( i ).dimension( 1 ),
					( int ) subvolumes.get( i ).dimension( 2 ) };
			MarchingCubesCallable callable = new MarchingCubesCallable( subvolumes.get( i ), volDim, offsets.get( i ), voxDim, true, isoLevel,
					false );
			logger.trace( "callable: " + callable );
			logger.trace( "input " + subvolumes.get( i ) );
			Future< SimpleMesh > result = executor.submit( callable );
			resultMeshList.add( result );
		}
//		}

		Future< SimpleMesh > completedFuture = null;
		logger.info( "waiting results..." );

		while ( resultMeshList.size() > 0 )
		{
			// block until a task completes
			try
			{
				completedFuture = executor.take();
				logger.trace( "task " + completedFuture + " is ready: " + completedFuture.isDone() );
			}
			catch ( InterruptedException e )
			{
				// TODO Auto-generated catch block
				logger.error( " task interrupted: " + e.getCause() );
				e.printStackTrace();
			}

			resultMeshList.remove( completedFuture );

			// get the mesh, if the task was able to create it
			try
			{
				m = completedFuture.get();
				logger.info( "getting mesh" );
			}
			catch ( InterruptedException | ExecutionException e )
			{
				Throwable cause = e.getCause();
				logger.error( "Mesh creation failed: " + cause );
				e.printStackTrace();
				break;
			}

			// a mesh was created, so update the existing mesh
			if ( m.getNumberOfTriangles() > 0 )
			{
				logger.info( "updating mesh..." );
				updateMesh( m, neuron );
				neuron.setVertices( FloatBuffer.wrap( verticesArray ) );
				neuron.recalculateNormals();
				neuron.setDirty( true );
			}
		}

		logger.debug( "size of mesh " + verticesArray.length );
		logger.info( "all results generated!" );
	}
	
	public SceneryPanel getPanel()
	{
		if (scPanel[0] == null)
			createPanel();
		
		return scPanel[0];
		
	}
	
	public boolean isReady()
	{
		return isReady;
	}
	
	public Renderer getRenderer()
	{
		return getRenderer();
	}
}
