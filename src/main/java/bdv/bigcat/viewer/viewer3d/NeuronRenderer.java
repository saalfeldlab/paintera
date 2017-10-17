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

}
