package bdv.bigcat.viewer.viewer3d;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import bdv.bigcat.ui.ARGBStream;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.StateListener;
import bdv.bigcat.viewer.viewer3d.marchingCubes.ForegroundCheck;
import graphics.scenery.Scene;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;

/**
 *
 *
 * @author Vanessa Leite
 * @author Philipp Hanslovsky
 * @param <T>
 * @param <F>
 */
public class NeuronRenderer< T, F extends FragmentSegmentAssignmentState< F > > implements StateListener< F >
{

	private static final float ONE_OVER_255 = 1.0f / 255.0f;

	private final long selectedFragmentId;

	public NeuronRenderer(
			final long selectedFragmentId,
			final F fragmentSegmentAssignment,
			final ARGBStream stream,
			final Localizable initialLocationInImageCoordinates,
			final RandomAccessible< T > data,
			final Interval interval,
			final Function< T, ForegroundCheck< T > > getForegroundCheck,
			final Scene scene,
			final ExecutorService es,
			final AffineTransform3D toWorldCoordinates,
			final int[] blockSize,
			final int[] cubeSize )
	{
		super();
		this.selectedFragmentId = selectedFragmentId;
		this.selectedSegmentId = fragmentSegmentAssignment.getSegment( selectedFragmentId );
		this.fragmentSegmentAssignment = fragmentSegmentAssignment;
		this.stream = stream;
		this.initialLocationInImageCoordinates = initialLocationInImageCoordinates;
		this.data = data;
		this.interval = interval;
		this.getForegroundCheck = getForegroundCheck;
		this.scene = scene;
		this.es = es;
		this.toWorldCoordinates = toWorldCoordinates;
		this.blockSize = blockSize;
		this.cubeSize = cubeSize;

		updateForegroundCheck();
		this.fragmentSegmentAssignment.addListener( this );

	}

	private long selectedSegmentId;

	private final F fragmentSegmentAssignment;

	private final ARGBStream stream;

	private final Localizable initialLocationInImageCoordinates;

	private final RandomAccessible< T > data;

	private final Interval interval;

	private final Function< T, ForegroundCheck< T > > getForegroundCheck;

	private ForegroundCheck< T > foregroundCheck;

	private final Scene scene;

	private final ExecutorService es;

	private final AffineTransform3D toWorldCoordinates;

	private final int[] blockSize;

	private final int[] cubeSize;

	private boolean updateOnStateChange = true;

	private boolean allowRendering = true;

	private final List< Neuron< T > > neurons = new ArrayList<>();

	private float[] completeBoundingBox;

	public synchronized void cancelRendering()
	{
		neurons.forEach( Neuron::cancel );
	}

	public synchronized void removeSelfFromScene()
	{
		cancelRendering();
		neurons.forEach( Neuron::removeSelf );
	}

	public synchronized void updateOnStateChange( final boolean updateOnStateChange )
	{
		this.updateOnStateChange = updateOnStateChange;
	}

	@Override
	public synchronized void stateChanged()
	{
		if ( updateOnStateChange )
		{
			this.selectedSegmentId = fragmentSegmentAssignment.getSegment( selectedFragmentId );
			updateForegroundCheck();
			render();
		}
	}

	public synchronized void render()
	{
		if ( allowRendering )
		{
			removeSelfFromScene();
			this.neurons.clear();
			final Neuron< T > neuron = new Neuron<>( this, interval, scene );
			this.neurons.add( neuron );
			final int color = stream.argb( selectedFragmentId );
			neuron.render( initialLocationInImageCoordinates, data, foregroundCheck, toWorldCoordinates, blockSize, cubeSize, color, es );

		}
	}

//	private void hasNeighboringData( Localizable location, boolean[] neighboring )
//	{
//		// for each dimension, verifies first in the +, then in the - direction
//		// if the voxels in the boundary contain the foregroundvalue
//		final Interval chunkInterval = partitioner.getChunk( location ).getA().interval();
//		for ( int i = 0; i < chunkInterval.numDimensions(); i++ )
//		{
//			// initialize each direction with false
//			neighboring[ i * 2 ] = false;
//			neighboring[ i * 2 + 1 ] = false;
//
//			checkData( i, chunkInterval, neighboring, "+" );
//			checkData( i, chunkInterval, neighboring, "-" );
//		}
//	}
//
//	private void checkData( int i, Interval chunkInterval, boolean[] neighboring, String direction )
//	{
//		final long[] begin = new long[ chunkInterval.numDimensions() ];
//		final long[] end = new long[ chunkInterval.numDimensions() ];
//
//		begin[ i ] = ( direction.compareTo( "+" ) == 0 ) ? chunkInterval.max( i ) : chunkInterval.min( i );
//		end[ i ] = begin[ i ];
//
//		for ( int j = 0; j < chunkInterval.numDimensions(); j++ )
//		{
//			if ( i == j )
//				continue;
//
//			begin[ j ] = chunkInterval.min( j );
//			end[ j ] = chunkInterval.max( j );
//		}
//
//		RandomAccessibleInterval< T > slice = Views.interval( volumeLabels, new FinalInterval( begin, end ) );
//		Cursor< T > cursor = Views.flatIterable( slice ).cursor();
//
//		System.out.println( "Checking dataset from: " + begin[ 0 ] + " " + begin[ 1 ] + " " + begin[ 2 ] + " to: " + end[ 0 ] + " " + end[ 1 ] + " " + end[ 2 ] );
//
//		while ( cursor.hasNext() )
//		{
//			cursor.next();
//			if ( foregroundCheck.test( cursor.get() ) == 1 )
//			{
//				int index = ( direction.compareTo( "+" ) == 0 ) ? i * 2 : i * 2 + 1;
//				neighboring[ index ] = true;
//				break;
//			}
//		}
//		int index = ( direction.compareTo( "+" ) == 0 ) ? i * 2 : i * 2 + 1;
//		System.out.println( "this dataset is: {}" + neighboring[ index ] );
//	}

	public long fragmentId()
	{
		return this.selectedFragmentId;
	}

	public synchronized long segmentId()
	{
		return this.selectedSegmentId;
	}

	private synchronized void updateForegroundCheck()
	{
		final RandomAccess< T > ra = data.randomAccess();
		ra.setPosition( initialLocationInImageCoordinates );
		this.foregroundCheck = getForegroundCheck.apply( ra.get() );
	}

	@Override
	public synchronized String toString()
	{
		return String.format( "%s: %d %d", getClass().getSimpleName(), fragmentId(), segmentId() );
	}

	public synchronized void stopListening()
	{
		this.fragmentSegmentAssignment.removeListener( this );
	}

	public synchronized void allowRendering()
	{
		allowRendering( true );
	}

	public synchronized void disallowRendering()
	{
		allowRendering( false );
	}

	public synchronized void allowRendering( final boolean allow )
	{
		this.allowRendering = allow;
	}

//	public void updateCompleteBoundingBox( Mesh mesh )
//	{
//		FloatBuffer vertices = mesh.getVertices();
//		float[] verticesArray = vertices.array();
//		float minX = ( float ) IntStream.range( 0, verticesArray.length ).mapToDouble( i -> verticesArray[ i ] ).filter( i -> ( i + 3 ) % 3 == 0 ).min().getAsDouble();
//		float minY = ( float ) IntStream.range( 0, verticesArray.length ).mapToDouble( i -> verticesArray[ i ] ).filter( i -> ( i + 2 ) % 3 == 0 ).min().getAsDouble();
//		float minZ = ( float ) IntStream.range( 0, verticesArray.length ).mapToDouble( i -> verticesArray[ i ] ).filter( i -> ( i + 1 ) % 3 == 0 ).min().getAsDouble();
//		float maxX = ( float ) IntStream.range( 0, verticesArray.length ).mapToDouble( i -> verticesArray[ i ] ).filter( i -> ( i + 3 ) % 3 == 0 ).max().getAsDouble();
//		float maxY = ( float ) IntStream.range( 0, verticesArray.length ).mapToDouble( i -> verticesArray[ i ] ).filter( i -> ( i + 2 ) % 3 == 0 ).max().getAsDouble();
//		float maxZ = ( float ) IntStream.range( 0, verticesArray.length ).mapToDouble( i -> verticesArray[ i ] ).filter( i -> ( i + 1 ) % 3 == 0 ).max().getAsDouble();
//
//		System.out.println( "calculated bb: " + minX + "x" + minY + "x" + minZ + " " + maxX + "x" + maxY + "x" + maxZ );
//		float[] boundingBox = new float[] { minX, minY, minZ, maxX, maxY, maxZ };
//
////		assert ( completeBoundingBox.length == boundingBox.length );
////
//		if ( nodes.size() == 1 )
//			completeBoundingBox = boundingBox;
//
//		// TODO: the bb cames with min, max, min, max!
//		for ( int d = 0; d < completeBoundingBox.length; d++ )
//		{
//			if ( d < ( completeBoundingBox.length / 2 ) && completeBoundingBox[ d ] > boundingBox[ d ] )
//				completeBoundingBox[ d ] = boundingBox[ d ];
//			else if ( d >= ( completeBoundingBox.length / 2 ) && completeBoundingBox[ d ] < boundingBox[ d ] )
//				completeBoundingBox[ d ] = boundingBox[ d ];
//		}
//
//		System.out.println( "completeBB: " + completeBoundingBox[ 0 ] + "x" + completeBoundingBox[ 1 ] + "x" + completeBoundingBox[ 2 ] +
//				" " + completeBoundingBox[ 3 ] + "x" + completeBoundingBox[ 4 ] + "x" + completeBoundingBox[ 5 ] );
//
//	}
//
//	public GLVector getCameraPosition()
//	{
//		float[] cameraPosition = new float[ 3 ];
//		cameraPosition[ 0 ] = ( completeBoundingBox[ 0 ] );
//
//		for ( int i = 0; i < completeBoundingBox.length / 2; i++ )
//		{
//			cameraPosition[ i ] = ( completeBoundingBox[ i ] );// +
//																// completeBoundingBox[
//																// i + 3 ] ) /
//																// 2;
//		}
//
//		// z-axis
//		double distance = Math.abs( completeBoundingBox[ 5 ] - completeBoundingBox[ 2 ] ) / 2 / Math.tan( scene.getActiveObserver().getFov() / 2 );
//		cameraPosition[ 2 ] = ( float ) ( completeBoundingBox[ 2 ] + distance );
//		
////		System.out.println( "camera position: " + cameraPosition[ 0 ] + " " + cameraPosition[ 1 ] + " " + cameraPosition[ 2 ] );
//
//		return new GLVector( cameraPosition[ 0 ], cameraPosition[ 1 ], cameraPosition[ 2 ] );
//	}
}
