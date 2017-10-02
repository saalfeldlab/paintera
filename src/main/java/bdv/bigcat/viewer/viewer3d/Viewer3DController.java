package bdv.bigcat.viewer.viewer3d;

import java.nio.FloatBuffer;
import java.util.Arrays;

import bdv.bigcat.viewer.viewer3d.marchingCubes.MarchingCubes;
import bdv.bigcat.viewer.viewer3d.util.MeshExtractor;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset;
import cleargl.GLVector;
import graphics.scenery.Material;
import graphics.scenery.Mesh;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;

/**
 * Main class for the Marching Cubes
 * 
 * @author vleite
 */
public class Viewer3DController
{
	private static MarchingCubes.ForegroundCriterion criterion = MarchingCubes.ForegroundCriterion.EQUAL;

	private static int[] cubeSize = { 1, 1, 1 };

	private static float[] verticesArray = new float[ 0 ];

	private Viewer3D viewer3D;

	private ViewerMode mode;

	private double[] resolution;

	/**
	 * Enum of the viewer modes. There are two types: ONLY_ONE_NEURON_VISIBLE:
	 * Remove all previously rendered neurons and show only the most recent one.
	 * MANY_NEURONS_VISIBLE: Add a new neuron to the viewer and keep the ones
	 * that were already rendered.
	 */
	public enum ViewerMode
	{
		ONLY_ONE_NEURON_VISIBLE,
		MANY_NEURONS_VISIBLE
	}

	public Viewer3DController()
	{
		viewer3D = null;
	}

	public void setViewer3D( Viewer3D viewer3D )
	{
		this.viewer3D = viewer3D; 
	}

	public void setMode( ViewerMode mode )
	{
		this.mode = mode;
	}

	public void setResolution( double[] resolution )
	{
		this.resolution = resolution;
		viewer3D.setVolumeResolution( resolution );
	}

	public void generateMesh( RandomAccessibleInterval< LabelMultisetType > volumeLabels, Localizable location )
	{
		if ( mode == ViewerMode.ONLY_ONE_NEURON_VISIBLE )
		{
			viewer3D.removeAllNeurons();
		}

		int foregroundValue = getForegroundValue( volumeLabels, location );
		MeshExtractor meshExtractor = new MeshExtractor( volumeLabels, cubeSize, foregroundValue, criterion );

		// use cube of size 1
		Mesh completeNeuron = new Mesh();
		final Material material = new Material();
		material.setAmbient( new GLVector( 1f, 0.0f, 1f ) );
		material.setSpecular( new GLVector( 1f, 0.0f, 1f ) );

		material.setDiffuse( new GLVector( 1, 1, 0 ) );

		completeNeuron.setMaterial( material );
		completeNeuron.setPosition( new GLVector( 0.0f, 0.0f, 0.0f ) );
		completeNeuron.setScale( new GLVector( ( float ) resolution[ 0 ], ( float ) resolution[ 1 ], ( float ) resolution[ 2 ] ) );
		viewer3D.addChild( completeNeuron );

		meshExtractor.setCubeSize( cubeSize );
		meshExtractor.createChunks( location );

		float[] completeNeuronVertices = new float[ 0 ];
		int completeMeshSize = 0;
		while ( meshExtractor.hasNext() )
		{
			Mesh neuron = new Mesh();
			neuron = meshExtractor.next();
			if ( neuron == null )
			{
				continue;
			}

			if ( completeNeuron.getVertices().hasArray() )
			{
				completeNeuronVertices = completeNeuron.getVertices().array();
				completeMeshSize = completeNeuronVertices.length;
			}

			float[] neuronVertices = neuron.getVertices().array();
			int meshSize = neuronVertices.length;
			verticesArray = Arrays.copyOf( completeNeuronVertices, completeMeshSize + meshSize );
			System.arraycopy( neuronVertices, 0, verticesArray, completeMeshSize, meshSize );

			completeNeuron.setVertices( FloatBuffer.wrap( verticesArray ) );
			completeNeuron.recalculateNormals();
			completeNeuron.setDirty( true );
		}
	}

	private int getForegroundValue( RandomAccessibleInterval< LabelMultisetType > input, Localizable location )
	{
		final RandomAccess< LabelMultisetType > access = input.randomAccess();
		access.setPosition( location );

		System.out.println( " location: " + location.getIntPosition( 0 ) + "x" + location.getIntPosition( 1 ) + "x" + location.getIntPosition( 2 ) );

		int foregroundValue = -1;
		for ( final Multiset.Entry< Label > e : access.get().entrySet() )
		{
			foregroundValue = ( int ) e.getElement().id();
			System.out.println( "foregroundValue: " + foregroundValue );
		}

		return foregroundValue;
	}
}
