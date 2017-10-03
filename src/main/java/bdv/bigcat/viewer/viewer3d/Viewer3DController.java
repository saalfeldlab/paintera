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
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.IntegerType;

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

	private static Viewer3D viewer3D;

	private static ViewerMode mode;

	private static double[] resolution;

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
		Viewer3DController.viewer3D = viewer3D;
	}

	public void setMode( ViewerMode mode )
	{
		Viewer3DController.mode = mode;
	}

	public void setResolution( double[] resolution )
	{
		Viewer3DController.resolution = resolution;
		viewer3D.setVolumeResolution( resolution );
	}

	public static void renderAtSelectionMultiset(
			final RandomAccessibleInterval< LabelMultisetType >[] labelVolumes,
			final AffineTransform3D[] transforms,
			final Localizable location,
			final long label )
	{
		if ( mode == ViewerMode.ONLY_ONE_NEURON_VISIBLE )
		{
			viewer3D.removeAllNeurons();
		}

		for ( int i = 0; i < labelVolumes.length; ++i )
		{
			// parameters for each resolution
			final RandomAccessibleInterval< LabelMultisetType > labelVolume = labelVolumes[ i ];
			final AffineTransform3D transform = transforms[ i ];
			final RandomAccess< LabelMultisetType > access = labelVolume.randomAccess();
			final RealPoint p = new RealPoint( labelVolume.numDimensions() );
			transform.applyInverse( p, location );
			for ( int d = 0; d < p.numDimensions(); ++d )
			{
				access.setPosition( ( long ) p.getDoublePosition( d ), d );
			}

			// same label for all resolutions
			int foregroundValue = ( int ) label;
			MeshExtractor meshExtractor = new MeshExtractor( labelVolume, cubeSize, foregroundValue, criterion );

			// create an empty mesh
			Mesh completeNeuron = new Mesh();
			final Material material = new Material();
			material.setAmbient( new GLVector( 1f, 0.0f, 1f ) );
			material.setSpecular( new GLVector( 1f, 0.0f, 1f ) );
			// TODO: Get the color of the neuron in the segmentation
			material.setDiffuse( new GLVector( 1f, 1f, 0f ) );
			material.setOpacity( 1f );

			// define scale, position and material of the mesh
			completeNeuron.setMaterial( material );
			completeNeuron.setScale( new GLVector( ( float ) resolution[ 0 ], ( float ) resolution[ 1 ], ( float ) resolution[ 2 ] ) );
			completeNeuron.setPosition( new GLVector( 0.0f, 0.0f, 0.0f ) );

			// if it is not the first resolution, remove the already created
			// resolution.
			// TODO: this must be done in a piece-wise way. I do not think
			// remove all the mesh and grown it again is the best way to do
			// this.
			if ( i != 0 )
			{
				viewer3D.removeChild( completeNeuron );
			}

			// add the mesh (still empty) in the viewer
			viewer3D.addChild( completeNeuron );
			// use cube of size - resolution is given by the data itself
			meshExtractor.setCubeSize( cubeSize );
			// TODO: generate mesh starting at position defined by access
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

				// transform mesh into real world coordinates using
				verticesArray = applyTransformation( verticesArray, transform );
				// update the mesh in the viewer
				completeNeuron.setVertices( FloatBuffer.wrap( verticesArray ) );
				completeNeuron.recalculateNormals();
				completeNeuron.setDirty( true );
			}
		}
	}

	public static < I extends IntegerType< I > > void renderAtSelection(
			final RandomAccessibleInterval< I >[] labelVolumes,
			final AffineTransform3D[] transforms,
			final Localizable location )
	{
		if ( mode == ViewerMode.ONLY_ONE_NEURON_VISIBLE )
		{
			viewer3D.removeAllNeurons();
		}

		for ( int i = 0; i < labelVolumes.length; ++i )
		{
			// parameters for each resolution
			final RandomAccessibleInterval< I > labelVolume = labelVolumes[ i ];
			final AffineTransform3D transform = transforms[ i ];
			final RandomAccess< I > access = labelVolume.randomAccess();
			final RealPoint p = new RealPoint( labelVolume.numDimensions() );
			transform.applyInverse( p, location );
			for ( int d = 0; d < p.numDimensions(); ++d )
				access.setPosition( ( long ) p.getDoublePosition( d ), d );
			final long label = access.get().getIntegerLong();

			// same label for all resolutions
			int foregroundValue = ( int ) label;
			MeshExtractor< I > meshExtractor = new MeshExtractor< I >( labelVolume, cubeSize, foregroundValue, criterion );

			// create an empty mesh
			Mesh completeNeuron = new Mesh();
			final Material material = new Material();
			material.setAmbient( new GLVector( 1f, 0.0f, 1f ) );
			material.setSpecular( new GLVector( 1f, 0.0f, 1f ) );
			// TODO: Get the color of the neuron in the segmentation
			material.setDiffuse( new GLVector( 1f, 1f, 0f ) );
			material.setOpacity( 1f );

			// define scale, position and material of the mesh
			completeNeuron.setMaterial( material );
			completeNeuron.setScale( new GLVector( ( float ) resolution[ 0 ], ( float ) resolution[ 1 ], ( float ) resolution[ 2 ] ) );
			completeNeuron.setPosition( new GLVector( 0.0f, 0.0f, 0.0f ) );

			// if it is not the first resolution, remove the already created
			// resolution.
			// TODO: this must be done in a piece-wise way. I do not think
			// remove all the mesh and grown it again is the best way to do
			// this.
			if ( i != 0 )
			{
				viewer3D.removeChild( completeNeuron );
			}

			// add the mesh (still empty) in the viewer
			viewer3D.addChild( completeNeuron );
			// use cube of size - resolution is given by the data itself
			meshExtractor.setCubeSize( cubeSize );
			// TODO: generate mesh starting at position defined by access
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

				// transform mesh into real world coordinates using
				verticesArray = applyTransformation( verticesArray, transform );
				// update the mesh in the viewer
				completeNeuron.setVertices( FloatBuffer.wrap( verticesArray ) );
				completeNeuron.recalculateNormals();
				completeNeuron.setDirty( true );
			}
		}
	}

	public static void generateMesh( RandomAccessibleInterval< LabelMultisetType > volumeLabels, Localizable location )
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

//		TODO: Get the color of the neuron in the segmentation
//		LabelMultisetARGBConverter converter = new LabelMultisetARGBConverter();
//		ARGBType argb = new ARGBType();
//		converter.convert( volumeLabels.randomAccess().get(), argb );
//		material.setDiffuse( new GLVector( ARGBType.red( foregroundValue ), ARGBType.green( foregroundValue ), ARGBType.blue( foregroundValue ) ) );

		material.setDiffuse( new GLVector( 1f, 1f, 0f ) );
		material.setOpacity( 0.5f );

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

	private static int getForegroundValue( RandomAccessibleInterval< LabelMultisetType > input, Localizable location )
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

	private static int getForegroundValue( RandomAccess< LabelMultisetType > access )
	{
		int foregroundValue = -1;
		for ( final Multiset.Entry< Label > e : access.get().entrySet() )
		{
			foregroundValue = ( int ) e.getElement().id();
			System.out.println( "foregroundValue: " + foregroundValue );
		}

		return foregroundValue;
	}

	/**
	 * transform mesh into real world coordinates applying affine
	 * transformations
	 * @param transform
	 */
	private static void applyTransformation( Mesh completeNeuron, AffineTransform3D transform )
	{
		float[] source = null;
		if ( completeNeuron.getVertices().hasArray() )
		{
			source = completeNeuron.getVertices().array();
		}

		float[] target = new float[ source.length ];

		transform.apply( source, target );

		completeNeuron.setVertices( FloatBuffer.wrap( target ) );
	}

	private static float[] applyTransformation( float[] source, AffineTransform3D transform )
	{
		float[] target = new float[ source.length ];
		transform.apply( source, target );

		return target;
	}
}
