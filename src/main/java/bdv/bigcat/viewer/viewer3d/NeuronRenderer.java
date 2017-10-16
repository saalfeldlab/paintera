package bdv.bigcat.viewer.viewer3d;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.IntStream;

import bdv.bigcat.ui.ARGBStream;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.StateListener;
import bdv.bigcat.viewer.viewer3d.marchingCubes.ForegroundCheck;
import bdv.bigcat.viewer.viewer3d.marchingCubes.MarchingCubes;
import bdv.bigcat.viewer.viewer3d.util.HashWrapper;
import cleargl.GLVector;
import graphics.scenery.Material;
import graphics.scenery.Mesh;
import graphics.scenery.Node;
import graphics.scenery.Scene;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;

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

	private final List< Node > nodes = new ArrayList<>();

	private final List< Future< ? > > futures = new ArrayList<>();

	private boolean updateOnStateChange = true;

	private boolean isCanceled = false;

	public synchronized void cancelRendering()
	{
		synchronized ( futures )
		{
			isCanceled = true;
			for ( final Future< ? > future : futures )
				future.cancel( true );
			futures.clear();
		}
	}

	public synchronized void removeSelfFromScene()
	{
		cancelRendering();
		nodes.forEach( scene::removeChild );
		nodes.clear();
	}

	public synchronized void updateOnStateChange( final boolean updateOnStateChange )
	{
		this.updateOnStateChange = updateOnStateChange;
	}

	@Override
	public void stateChanged()
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
		removeSelfFromScene();
		isCanceled = false;
		final Map< HashWrapper< long[] >, long[] > offsets = new HashMap<>();
		final long[] coordinates = new long[ initialLocationInImageCoordinates.numDimensions() ];
		initialLocationInImageCoordinates.localize( coordinates );
		final long[] gridCoordinates = toGridCoordinates( coordinates, blockSize );
		submitForOffset( gridCoordinates, offsets );
	}

	private void submitForOffset( final long[] gridCoordinates, final Map< HashWrapper< long[] >, long[] > offsets )
	{
		final HashWrapper< long[] > offset = HashWrapper.longArray( gridCoordinates );
		final long[] coordinates = IntStream.range( 0, gridCoordinates.length ).mapToLong( d -> gridCoordinates[ d ] * blockSize[ d ] ).toArray();
		synchronized ( offsets )
		{
			if ( isCanceled || offsets.containsKey( offset ) || !Intervals.contains( interval, new Point( coordinates ) ) )
				return;
			offsets.put( offset, coordinates );
		}
		synchronized ( futures )
		{
			if ( !isCanceled )
				this.futures.add( es.submit( () -> {
					final Interval interval = new FinalInterval(
							coordinates,
							IntStream.range( 0, coordinates.length ).mapToLong( d -> coordinates[ d ] + blockSize[ d ] ).toArray() );
					final MarchingCubes< T > mc = new MarchingCubes<>( data, interval, toWorldCoordinates, cubeSize );
					final Pair< float[], float[] > verticesAndNormals = mc.generateMesh( foregroundCheck );
					final float[] vertices = verticesAndNormals.getA();
					final float[] normals = verticesAndNormals.getB();

					assert vertices.length == normals.length;

					if ( vertices.length > 0 )
					{

						final Mesh mesh = new Mesh();
						final Material material = new Material();
						final int color = stream.argb( selectedSegmentId );
						material.setDiffuse( new GLVector( ( color >>> 16 & 0xff ) * ONE_OVER_255, ( color >>> 8 & 0xff ) * ONE_OVER_255, ( color >>> 0 & 0xff ) * ONE_OVER_255 ) );
						material.setOpacity( 1.0f );
						material.setAmbient( new GLVector( 1f, 0.0f, 1f ) );
						material.setSpecular( new GLVector( 1f, 0.0f, 1f ) );
						mesh.setMaterial( material );
						mesh.setPosition( new GLVector( 0.0f, 0.0f, 0.0f ) );
						mesh.setVertices( FloatBuffer.wrap( vertices ) );
						mesh.setNormals( FloatBuffer.wrap( normals ) );
						synchronized ( nodes )
						{
							if ( !isCanceled )
							{
								nodes.add( mesh );
								scene.addChild( mesh );
								mesh.setDirty( true );
							}
						}

						for ( int d = 0; d < gridCoordinates.length; ++d )
						{
							final long[] otherGridCoordinates = gridCoordinates.clone();
							otherGridCoordinates[ d ] += 1;
							submitForOffset( otherGridCoordinates.clone(), offsets );

							otherGridCoordinates[ d ] -= 2;
							submitForOffset( otherGridCoordinates.clone(), offsets );
						}

					}
				} ) );
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

	private synchronized void addNode( final Node node )
	{
		this.nodes.add( node );
		this.scene.addChild( node );
	}

	private static long[] toGridCoordinates( final long[] coordinates, final int[] blockSize )
	{
		final long[] gridCoordinates = new long[ coordinates.length ];
		toGridCoordinates( coordinates, blockSize, gridCoordinates );
		return gridCoordinates;
	}

	private static void toGridCoordinates( final long[] coordinates, final int[] blockSize, final long[] gridCoordinates )
	{
		for ( int i = 0; i < coordinates.length; ++i )
			gridCoordinates[ i ] = coordinates[ i ] / blockSize[ i ];
	}

	private void updateForegroundCheck()
	{

		final RandomAccess< T > ra = data.randomAccess();
		ra.setPosition( initialLocationInImageCoordinates );
		this.foregroundCheck = getForegroundCheck.apply( ra.get() );
	}

	@Override
	public String toString()
	{
		return String.format( "%s: %d %d", getClass().getSimpleName(), fragmentId(), segmentId() );
	}

}
