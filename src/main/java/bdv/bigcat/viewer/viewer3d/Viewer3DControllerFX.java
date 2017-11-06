package bdv.bigcat.viewer.viewer3d;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.ui.ARGBStream;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.viewer3d.marchingCubes.ForegroundCheck;
import javafx.application.Platform;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccessible;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;

/**
 * Class that controls the 3d scene
 *
 * @author Vanessa Leite
 * @author Philipp Hanslovsky
 */
public class Viewer3DControllerFX
{
	public static Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final Viewer3DFX viewer3D;

	private final ExecutorService es = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() - 1 );

	private final HashSet< NeuronRendererFX > renderers = new HashSet<>();

	private CameraModeFX camera;

	/**
	 * Default constructor
	 */
	public Viewer3DControllerFX( final Viewer3DFX viewer )
	{
		this.viewer3D = viewer;
	}

	public void init()
	{
		Platform.runLater( () -> {
			System.out.println( "starting controller" );
			camera = new CameraModeFX( viewer3D.meshesGroup() );
			camera.automatic();
//			camera.manual();
		} );
	}

	public synchronized < T extends Type< T >, F extends FragmentSegmentAssignmentState< F > > void generateMesh(
			final RandomAccessible< T > volumeLabels,
			final Interval interval,
			final AffineTransform3D transform,
			final RealLocalizable worldLocation,
			final int[] partitionSize,
			final int[] cubeSize,
			final Function< T, ForegroundCheck< T > > getForegroundCheck,
			final long fragmentId,
			final F fragmentSegmentAssignment,
			final ARGBStream stream,
			final boolean append )
	{
		System.out.println( "generating mesh" );
		LOG.info( "Rendering neuron: {} {}", fragmentId, fragmentSegmentAssignment.getSegment( fragmentId ) );

		if ( LOG.isWarnEnabled() )
			if ( IntStream.range( 0, cubeSize.length ).map( d -> partitionSize[ d ] % cubeSize[ d ] ).filter( mod -> mod != 0 ).count() > 0 )
				LOG.warn( "Partition size ({}) not integer multiple of cube size ({}) for at least one dimension. This may result in rendering issues in overlap areas.", Arrays.toString( partitionSize ), Arrays.toString( cubeSize ) );

		final RealPoint imageLocation = new RealPoint( worldLocation.numDimensions() );
		transform.applyInverse( imageLocation, worldLocation );
		final Point locationInImageCoordinates = new Point( imageLocation.numDimensions() );
		for ( int d = 0; d < locationInImageCoordinates.numDimensions(); ++d )
		{
			final long position = Math.round( imageLocation.getDoublePosition( d ) );
			locationInImageCoordinates.setPosition( position, d );
		}

		synchronized ( this.renderers )
		{
			if ( !append )
			{
				this.renderers.forEach( NeuronRendererFX::disallowRendering );
				this.renderers.forEach( NeuronRendererFX::removeSelfFromScene );
				this.renderers.forEach( NeuronRendererFX::stopListening );
				this.renderers.clear();

				final RealLocalizable cameraPosition = new RealPoint( worldLocation.getFloatPosition( 0 ), worldLocation.getFloatPosition( 1 ), worldLocation.getFloatPosition( 2 ) * 1.5 );
//				camera.setPosition( new float[] { cameraPosition.getFloatPosition( 0 ), cameraPosition.getFloatPosition( 1 ), cameraPosition.getFloatPosition( 2 ) } );
//				System.out.println( "initial camera position: " + cameraPosition.getFloatPosition( 0 ) + "x" + cameraPosition.getFloatPosition( 1 ) + "x" + cameraPosition.getFloatPosition( 2 ) );
			}

			final List< NeuronRendererFX > filteredNrs = renderers.stream()
					.filter( nr -> nr.fragmentId() == fragmentId || nr.segmentId() == fragmentSegmentAssignment.getSegment( fragmentId ) )
					.collect( Collectors.toList() );
			LOG.info( "Removing renderers: {}", filteredNrs );

			filteredNrs.forEach( NeuronRendererFX::disallowRendering );
			filteredNrs.forEach( NeuronRendererFX::removeSelfFromScene );
			filteredNrs.forEach( NeuronRendererFX::stopListening );
			filteredNrs.forEach( this.renderers::remove );

			final NeuronRendererFX< T, F > nr = new NeuronRendererFX<>(
					fragmentId,
					fragmentSegmentAssignment,
					stream,
					locationInImageCoordinates,
					volumeLabels,
					interval,
					getForegroundCheck,
					viewer3D.meshesGroup(),
					viewer3D.scene().getCamera(),
					es,
					transform,
					partitionSize,
					cubeSize );
			nr.render();
			this.renderers.add( nr );

//			if ( camera.getCameraMode() == CameraModeFX.Mode.AUTOMATIC )
//				nr.addListener( camera );

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
