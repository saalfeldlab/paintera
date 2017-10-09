package bdv.bigcat.viewer.viewer3d;

import java.util.Optional;

import bdv.bigcat.viewer.viewer3d.marchingCubes.ForegroundCheck;
import bdv.bigcat.viewer.viewer3d.util.MeshExtractor;
import cleargl.GLVector;
import gnu.trove.list.array.TFloatArrayList;
import graphics.scenery.Material;
import graphics.scenery.Mesh;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;

/**
 * Main class for the Marching Cubes
 *
 * @author vleite
 */
public class Viewer3DController
{
	private final Viewer3D viewer3D;

	private final ViewerMode mode;

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
	public Viewer3DController( final Viewer3D viewer, final ViewerMode mode )
	{
		this.viewer3D = viewer;
		this.mode = mode;
	}

	/**
	 *
	 * @param labelVolumes
	 * @param transforms
	 * @param location
	 * @param label
	 */
	public < T extends Type< T > > void renderAtSelection(
			final RandomAccessible< T >[] labelVolumes,
			final Interval[] intervals,
			final AffineTransform3D[] transforms,
			final RealLocalizable location,
			final ForegroundCheck< T > isForeground,
			final int[] partitionSize,
			final int[] cubeSize )
	{
		if ( mode == ViewerMode.ONLY_ONE_NEURON_VISIBLE )
			viewer3D.removeAllNeurons();

		viewer3D.setCameraPosition( location );

		for ( int i = 0; i < labelVolumes.length; ++i )
		{
			// parameters for each resolution
			final RandomAccessible< T > labelVolume = labelVolumes[ i ];
			final AffineTransform3D transform = transforms[ i ];
			final RandomAccess< T > access = labelVolume.randomAccess();
			final RealPoint p = new RealPoint( labelVolume.numDimensions() );
			transform.applyInverse( p, location );
			for ( int d = 0; d < p.numDimensions(); ++d )
				access.setPosition( ( long ) p.getDoublePosition( d ), d );
			System.out.println( "Starting at " + new RealPoint( location ) + " " + p );

			// same label for all resolutions
			final MeshExtractor< T > meshExtractor = new MeshExtractor<>(
					labelVolume,
					intervals[ i ],
					transforms[ i ],
					partitionSize,
					cubeSize,
					access,
					isForeground );

			final Material material = new Material();
			material.setAmbient( new GLVector( 1f, 0.0f, 1f ) );
			material.setSpecular( new GLVector( 1f, 0.0f, 1f ) );

//			TODO: Get the color of the neuron in the segmentation
//			LabelMultisetARGBConverter converter = new LabelMultisetARGBConverter();
//			ARGBType argb = new ARGBType();
//			converter.convert( volumeLabels.randomAccess().get(), argb );
//			material.setDiffuse( new GLVector( ARGBType.red( foregroundValue ), ARGBType.green( foregroundValue ), ARGBType.blue( foregroundValue ) ) );

			material.setDiffuse( new GLVector( 1f, 1f, 0f ) );
			material.setOpacity( 0.5f );

			final Mesh completeNeuron = new Mesh();
			completeNeuron.setMaterial( material );
			completeNeuron.setPosition( new GLVector( 0.0f, 0.0f, 0.0f ) );
			viewer3D.addChild( completeNeuron );

			// TODO: remove mesh in lower resolution to add the mesh in higher
			// resolution

//			float[] completeNeuronVertices = new float[ 0 ];
//			int completeMeshSize = 0;
			final TFloatArrayList completeNeuronVertices = new TFloatArrayList();
			final TFloatArrayList completeNeuronNormals = new TFloatArrayList();
			System.out.println( "GENERATING MESH! " + meshExtractor.hasNext() );

			while ( meshExtractor.hasNext() )
			{
				final Optional< Mesh > neuron = meshExtractor.next();

				System.out.println( "GETTING NEURON AT " + neuron.isPresent() );
				if ( neuron.isPresent() )
				{

//					if ( completeNeuron.getVertices().hasArray() )
//					{
//						completeNeuronVertices = completeNeuron.getVertices().array();
//						completeMeshSize = completeNeuronVertices.length;
//					}
					viewer3D.addChild( neuron.get() );
					neuron.get().setDirty( true );
					material.setDiffuse( new GLVector( 1f, 1f, 0f ) );
					material.setOpacity( 0.5f );

					neuron.get().setMaterial( material );
					neuron.get().setPosition( new GLVector( 0.0f, 0.0f, 0.0f ) );

//					final Mesh mesh = neuron.get();
//					final float[] neuronVertices = mesh.getVertices().array();
//					final int meshSize = neuronVertices.length;
//					if ( meshSize > 0 )
//					{
//						completeNeuronVertices.addAll( neuronVertices );
//						final FloatBuffer normals = mesh.getNormals();
//						while ( normals.hasRemaining() )
//							completeNeuronNormals.add( normals.get() );
//						completeNeuron.setVertices( FloatBuffer.wrap( completeNeuronVertices.toArray() ) );
//						completeNeuron.setNormals( FloatBuffer.wrap( completeNeuronNormals.toArray() ) );
//						completeNeuron.setDirty( true );
//					}
				}
			}
		}
	}

	/**
	 *
	 * @param volumeLabels
	 * @param location
	 */
	public < T extends Type< T > > void generateMesh(
			final RandomAccessible< T > volumeLabels,
			final Interval interval,
			final AffineTransform3D transform,
			final RealLocalizable worldLocation,
			final int[] partitionSize,
			final int[] cubeSize,
			final ForegroundCheck< T > isForeground )
	{
		if ( mode == ViewerMode.ONLY_ONE_NEURON_VISIBLE )
			viewer3D.removeAllNeurons();

		viewer3D.setCameraPosition( worldLocation );

		final RealPoint imageLocation = new RealPoint( worldLocation.numDimensions() );
		transform.applyInverse( imageLocation, worldLocation );
		final Point location = new Point( imageLocation.numDimensions() );
		for ( int d = 0; d < location.numDimensions(); ++d )
			location.setPosition( ( long ) imageLocation.getDoublePosition( d ), d );
		System.out.println( "LOCATION " + location + " " + new RealPoint( worldLocation ) );

		final MeshExtractor< T > meshExtractor = new MeshExtractor<>(
				volumeLabels,
				interval,
				transform,
				partitionSize,
				cubeSize,
				location,
				isForeground );

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

		final Mesh completeNeuron = new Mesh();
		completeNeuron.setMaterial( material );
		completeNeuron.setPosition( new GLVector( 0.0f, 0.0f, 0.0f ) );
		viewer3D.addChild( completeNeuron );

//		float[] completeNeuronVertices = new float[ 0 ];
//		int completeMeshSize = 0;
		final TFloatArrayList completeNeuronVertices = new TFloatArrayList();
		final TFloatArrayList completeNeuronNormals = new TFloatArrayList();
		System.out.println( "GENERATING MESH! " + meshExtractor.hasNext() );

		while ( meshExtractor.hasNext() )
		{
			final Optional< Mesh > neuron = meshExtractor.next();

			System.out.println( "GETTING NEURON AT " + neuron.isPresent() );
			if ( neuron.isPresent() )
			{

//				if ( completeNeuron.getVertices().hasArray() )
//				{
//					completeNeuronVertices = completeNeuron.getVertices().array();
//					completeMeshSize = completeNeuronVertices.length;
//				}
				viewer3D.addChild( neuron.get() );
				neuron.get().setDirty( true );
				material.setDiffuse( new GLVector( 1f, 1f, 0f ) );
				material.setOpacity( 0.5f );

				neuron.get().setMaterial( material );
				neuron.get().setPosition( new GLVector( 0.0f, 0.0f, 0.0f ) );

//				final Mesh mesh = neuron.get();
//				final float[] neuronVertices = mesh.getVertices().array();
//				final int meshSize = neuronVertices.length;
//				if ( meshSize > 0 )
//				{
//					completeNeuronVertices.addAll( neuronVertices );
//					final FloatBuffer normals = mesh.getNormals();
//					while ( normals.hasRemaining() )
//						completeNeuronNormals.add( normals.get() );
//					completeNeuron.setVertices( FloatBuffer.wrap( completeNeuronVertices.toArray() ) );
//					completeNeuron.setNormals( FloatBuffer.wrap( completeNeuronNormals.toArray() ) );
//					completeNeuron.setDirty( true );
//				}
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
	public static float[] applyTransformation( final float[] source, final AffineTransform3D transform )
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
