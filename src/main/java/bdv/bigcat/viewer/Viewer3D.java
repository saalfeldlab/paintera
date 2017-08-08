package bdv.bigcat.viewer;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;

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
import graphics.scenery.Material;
import graphics.scenery.Mesh;
import graphics.scenery.PointLight;
import graphics.scenery.Scene;
import graphics.scenery.SceneryDefaultApplication;
import graphics.scenery.SceneryElement;
import graphics.scenery.backends.Renderer;
import graphics.scenery.utils.SceneryPanel;
import net.imglib2.RandomAccessibleInterval;

public class Viewer3D extends SceneryDefaultApplication
{
	/** logger */
	static final Logger LOGGER = LoggerFactory.getLogger( Viewer3D.class );

	/** small hdf5 for test - subset from sample B */
	static String path = "data/sample_B_20160708_frags_46_50.hdf";

	static int foregroundValue = 7;

	static int[] volDim = { 500, 500, 5 };

	static String path_label = "/volumes/labels/neuron_ids";

	static int[] cubeSize = { 1, 1, 1 };

	static float maxAxisVal = 0;

	static float[] verticesArray = new float[ 0 ];

	private static RandomAccessibleInterval< LabelMultisetType > volumeLabels = null;

	private static MarchingCubes.ForegroundCriterion criterion = MarchingCubes.ForegroundCriterion.EQUAL;

	private final SceneryPanel scPanel;

	public Viewer3D( String applicationName, int windowWidth, int windowHeight, boolean wantREPL )
	{
		super( applicationName, windowWidth, windowHeight, wantREPL );

		scPanel = new SceneryPanel( 500, 500 );
	}

	public void init()
	{
		loadData();
		
		setRenderer(
				Renderer.Factory.createRenderer( getHub(), getApplicationName(), getScene(), getWindowWidth(), getWindowHeight(), scPanel ) );
		getHub().add( SceneryElement.Renderer, getRenderer() );

		final Box hull = new Box( new GLVector( 50.0f, 50.0f, 50.0f ), true );
		hull.getMaterial().setDiffuse( new GLVector( 0.5f, 0.5f, 0.5f ) );
		hull.getMaterial().setDoubleSided( true );
		getScene().addChild( hull );

		final Material material = new Material();
		material.setAmbient( new GLVector( 0.1f * 1, 1.0f, 1.0f ) );
		material.setDiffuse( new GLVector( 0.1f * 1, 0.0f, 1.0f ) );
		material.setSpecular( new GLVector( 0.1f * 1, 0f, 0f ) );

		final Camera cam = new DetachedHeadCamera();

		cam.perspectiveCamera( 50f, getWindowWidth(), getWindowHeight(), 0.1f, 1000.0f );
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

		final Mesh neuron = new Mesh();
		neuron.setMaterial( material );
		neuron.setName( "neuron" );
		neuron.setPosition( new GLVector( 0.0f, 0.0f, 0.0f ) );
		neuron.setScale( new GLVector( 4.0f, 4.0f, 40.0f ) );
		getScene().addChild( neuron );

		new Thread()
		{
			@Override
			public void run()
			{
				marchingCubes( getScene() );
			}
		}.start();

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
		if ( LOGGER.isDebugEnabled() )
			LOGGER.debug( "previous size of vertices: " + verticesArray.length );

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
			verticesArray[ i ] /= maxAxisVal;

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
			try
		{
				labels = HDF5Reader.readLabels( reader, path_label );
		}
			catch ( final IOException e )
		{
				e.printStackTrace();
		}
		else
			System.out.println( "no label dataset '" + path_label + "' found" );

		volumeLabels = labels.get( 0 ).getImage( 0 );
	}

	private static void marchingCubes( Scene scene )
	{
		for ( int voxelSize = 32; voxelSize > 0; voxelSize /= 2 )
		{
			Mesh completeNeuron = new Mesh();
			final Material material = new Material();
			material.setAmbient( new GLVector( 1f, 0.0f, 1f ) );
			material.setSpecular( new GLVector( 1f, 0.0f, 1f ) );

			if ( voxelSize == 32 )
			{
				material.setDiffuse( new GLVector( 1, 0, 0 ) );
			}
			if ( voxelSize == 16 )
			{
				material.setDiffuse( new GLVector( 0, 1, 0 ) );
			}
			if ( voxelSize == 8 )
			{
				material.setDiffuse( new GLVector( 0, 0, 1 ) );
			}
			if ( voxelSize == 4 )
			{
				material.setDiffuse( new GLVector( 1, 0, 1 ) );
			}
			if ( voxelSize == 2 )
			{
				material.setDiffuse( new GLVector( 0, 1, 1 ) );
			}
			if ( voxelSize == 1 )
			{
				material.setDiffuse( new GLVector( 1, 1, 0 ) );
			}

			completeNeuron.setMaterial( material );
			completeNeuron.setName( String.valueOf( foregroundValue + " " + voxelSize ) );
			completeNeuron.setPosition( new GLVector( 0.0f, 0.0f, 0.0f ) );
			completeNeuron.setScale( new GLVector( 4.0f, 4.0f, 40.0f ) );
			scene.addChild( completeNeuron );

			cubeSize[ 0 ] = voxelSize;
			cubeSize[ 1 ] = voxelSize;
			cubeSize[ 2 ] = 1;

			MeshExtractor meshExtractor = new MeshExtractor( volumeLabels, cubeSize, foregroundValue, criterion );
			int[] position = new int[] { 0, 0, 0 };
			meshExtractor.createChunks( position );

			float[] completeNeuronVertices = new float[ 0 ];
			int completeMeshSize = 0;
			while ( meshExtractor.hasNext() )
			{
				Mesh neuron = new Mesh();
				neuron = meshExtractor.next();

				if ( completeNeuron.getVertices().hasArray() )
				{
					completeNeuronVertices = completeNeuron.getVertices().array();
					completeMeshSize = completeNeuronVertices.length;
				}

				float[] neuronVertices = neuron.getVertices().array();
				int meshSize = neuronVertices.length;
				verticesArray = Arrays.copyOf( completeNeuronVertices, completeMeshSize + meshSize );
				System.arraycopy( neuronVertices, 0, verticesArray, completeMeshSize, meshSize );

				System.out.println( "number of elements complete mesh: " + verticesArray.length );
				completeNeuron.setVertices( FloatBuffer.wrap( verticesArray ) );
				completeNeuron.recalculateNormals();
				completeNeuron.setDirty( true );
			}

			LOGGER.info( "all results generated!" );

			// Pause for 2 seconds
			try
			{
				Thread.sleep( 2000 );
			}
			catch ( InterruptedException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if ( voxelSize != 1 )
				scene.removeChild( completeNeuron );
		}
	}

	public SceneryPanel getPanel()
	{
		return scPanel;
	}
}
