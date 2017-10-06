package bdv.bigcat.viewer.viewer3d;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.ToIntFunction;

import bdv.bigcat.viewer.viewer3d.util.MeshExtractor;
import cleargl.GLVector;
import graphics.scenery.Material;
import graphics.scenery.Mesh;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.view.Views;

/**
 * Main class for the Marching Cubes
 *
 * @author vleite
 */
public class Viewer3DController
{
	private final Viewer3D viewer3D;

	private final ViewerMode mode;

	private final double[] resolution;

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

	/**
	 * Default constructor
	 */
	public Viewer3DController( final Viewer3D viewer, final ViewerMode mode, final double[] resolution )
	{
		this.viewer3D = viewer;
		this.mode = mode;
		this.resolution = resolution;
	}

	/**
	 *
	 * @param labelVolumes
	 * @param transforms
	 * @param location
	 * @param label
	 */
	public < T extends Type< T > > void renderAtSelection(
			final RandomAccessibleInterval< T >[] labelVolumes,
			final AffineTransform3D[] transforms,
			final Localizable location,
			final ToIntFunction< T > isForeground,
			final T extension,
			final int[] partitionSize,
			final int[] cubeSize )
	{
		if ( mode == ViewerMode.ONLY_ONE_NEURON_VISIBLE )
			viewer3D.removeAllNeurons();

		Mesh previousNeuron = null;
		for ( int i = 0; i < labelVolumes.length; ++i )
		{
			float[] verticesArray = new float[ 0 ];
			// parameters for each resolution
			final RandomAccessibleInterval< T > labelVolume = labelVolumes[ i ];
			final AffineTransform3D transform = transforms[ i ];
			final RandomAccess< T > access = labelVolume.randomAccess();
			final RealPoint p = new RealPoint( labelVolume.numDimensions() );
			transform.applyInverse( p, location );
			for ( int d = 0; d < p.numDimensions(); ++d )
				access.setPosition( ( long ) p.getDoublePosition( d ), d );

			// same label for all resolutions
			final MeshExtractor< T > meshExtractor = new MeshExtractor<>(
					Views.extendValue( labelVolume, extension ),
					labelVolume,
					partitionSize,
					cubeSize,
					access,
					isForeground );

			// create an empty mesh
			final Mesh completeNeuron = new Mesh();
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
				viewer3D.removeChild( previousNeuron );

			// add the mesh (still empty) in the viewer
			viewer3D.addChild( completeNeuron );
			// use cube of size - resolution is given by the data itself
			// TODO: generate mesh starting at position defined by access

			float[] completeNeuronVertices = new float[ 0 ];
			int completeMeshSize = 0;
			while ( meshExtractor.hasNext() )
			{
				System.out.println( "GETTING NEXT MESH?" );
				final Optional< Mesh > neuron = meshExtractor.next();
				if ( neuron.isPresent() )
				{

					if ( completeNeuron.getVertices().hasArray() )
					{
						completeNeuronVertices = completeNeuron.getVertices().array();
						completeMeshSize = completeNeuronVertices.length;
					}

					final float[] neuronVertices = neuron.get().getVertices().array();
					final int meshSize = neuronVertices.length;
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
			previousNeuron = completeNeuron;
		}
	}

	/**
	 *
	 * @param volumeLabels
	 * @param location
	 */
	public < T extends Type< T > > void generateMesh(
			final RandomAccessibleInterval< T > volumeLabels,
			final Localizable location,
			final int[] partitionSize,
			final int[] cubeSize,
			final ToIntFunction< T > isForeground,
			final T extension )
	{
		if ( mode == ViewerMode.ONLY_ONE_NEURON_VISIBLE )
			viewer3D.removeAllNeurons();

		float[] verticesArray = new float[ 0 ];

		final MeshExtractor< T > meshExtractor = new MeshExtractor<>(
				Views.extendValue( volumeLabels, extension ),
				volumeLabels,
				partitionSize,
				cubeSize,
				location,
				isForeground );

		// use cube of size 1
		final Mesh completeNeuron = new Mesh();
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

		float[] completeNeuronVertices = new float[ 0 ];
		int completeMeshSize = 0;
		while ( meshExtractor.hasNext() )
		{
			final Optional< Mesh > neuron = meshExtractor.next();
			if ( neuron.isPresent() )
			{
				if ( completeNeuron.getVertices().hasArray() )
				{
					completeNeuronVertices = completeNeuron.getVertices().array();
					completeMeshSize = completeNeuronVertices.length;
				}

				final float[] neuronVertices = neuron.get().getVertices().array();
				final int meshSize = neuronVertices.length;
				verticesArray = Arrays.copyOf( completeNeuronVertices, completeMeshSize + meshSize );
				System.arraycopy( neuronVertices, 0, verticesArray, completeMeshSize, meshSize );

				completeNeuron.setVertices( FloatBuffer.wrap( verticesArray ) );
				completeNeuron.recalculateNormals();
				completeNeuron.setDirty( true );
			}
		}
	}

	/**
	 * transform mesh into real world coordinates applying affine
	 * transformations
	 *
	 * @param source
	 *            original vertices values
	 * @param transform
	 *            transformations to be applied
	 * @return vertices transformed
	 */
	private static float[] applyTransformation( final float[] source, final AffineTransform3D transform )
	{
		final RealPoint p = new RealPoint( 3 );
		final float[] target = new float[ source.length ];
		for ( int n = 0; n < source.length; n += 3 )
		{
			p.setPosition( source[ n + 0 ], 0 );
			p.setPosition( source[ n + 1 ], 1 );
			p.setPosition( source[ n + 2 ], 2 );
			transform.apply( p, p );
			target[ n + 0 ] = p.getFloatPosition( 0 );
			target[ n + 1 ] = p.getFloatPosition( 1 );
			target[ n + 2 ] = p.getFloatPosition( 2 );
		}

		return target;
	}
}
