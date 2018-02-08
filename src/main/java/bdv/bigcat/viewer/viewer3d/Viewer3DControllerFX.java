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

import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.bigcat.viewer.stream.ARGBStream;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccessible;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.logic.BoolType;

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

	// TODO pass executor from outside?
	private final ExecutorService es = Executors.newFixedThreadPool( Math.min( Math.max( ( Runtime.getRuntime().availableProcessors() - 1 ) / 3, 1 ), 3 ) );

	private final HashSet< NeuronRendererFX< ?, ? > > renderers = new HashSet<>();

	/**
	 * Default constructor
	 */
	public Viewer3DControllerFX( final Viewer3DFX viewer )
	{
		this.viewer3D = viewer;
	}

	public void init()
	{}

	/**
	 *
	 * @param volumeLabels
	 * @param interval
	 * @param transform
	 * @param worldLocation
	 * @param partitionSize
	 * @param cubeSize
	 * @param createMaskConverterForType
	 * @param fragmentId
	 * @param fragmentSegmentAssignment
	 * @param stream
	 * @param append
	 * @param selectedIds
	 * @param transformManager
	 */
	public synchronized < T extends Type< T >, F extends FragmentSegmentAssignmentState< F > > void generateMesh(
			final RandomAccessible< T > volumeLabels,
			final Interval interval,
			final AffineTransform3D transform,
			final RealLocalizable worldLocation,
			final int[] partitionSize,
			final int[] cubeSize,
			final Function< T, Converter< T, BoolType > > createMaskConverterForType,
			final long fragmentId,
			final F fragmentSegmentAssignment,
			final ARGBStream stream,
			final boolean append,
			final SelectedIds selectedIds,
			final GlobalTransformManager transformManager )
	{
		LOG.debug( "Rendering neuron: {} {}", fragmentId, fragmentSegmentAssignment.getSegment( fragmentId ) );

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
			}

			final List< NeuronRendererFX< ?, ? > > filteredNrs = renderers.stream()
					.filter( nr -> nr.fragmentId() == fragmentId || nr.segmentId() == fragmentSegmentAssignment.getSegment( fragmentId ) )
					.collect( Collectors.toList() );
			LOG.debug( "Removing renderers: {}", filteredNrs );

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
					createMaskConverterForType,
					viewer3D.meshesGroup(),
					es,
					transform,
					partitionSize,
					cubeSize,
					selectedIds,
					transformManager );
			nr.render();
			this.renderers.add( nr );
		}
	}

	public synchronized void removeMesh( final long fragmentId )
	{
		final List< NeuronRendererFX< ?, ? > > matchingRenderers = renderers.stream().filter( nr -> nr.fragmentId() == fragmentId ).collect( Collectors.toList() );
		this.renderers.removeAll( matchingRenderers );
		matchingRenderers.forEach( NeuronRendererFX::removeSelfFromScene );
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
