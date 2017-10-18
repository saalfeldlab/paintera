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
import graphics.scenery.Box;
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
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;

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

	private float[] completeBoundingBox;

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
			{
				this.futures.add( es.submit( () -> {
					final Interval interval = new FinalInterval(
							coordinates,
							IntStream.range( 0, coordinates.length ).mapToLong( d -> coordinates[ d ] + blockSize[ d ] + cubeSize[ d ] ).toArray() );

					final RealPoint p = new RealPoint( interval.numDimensions() );

					// TODO: get the resolution from the data
					p.setPosition( interval.dimension( 0 ) * 4, 0 );
					p.setPosition( interval.dimension( 1 ) * 4, 1 );
					p.setPosition( interval.dimension( 2 ) * 40, 2 );

					final Box chunk = new Box( new GLVector( p.getFloatPosition( 0 ), p.getFloatPosition( 1 ), p.getFloatPosition( 2 ) ), true );
					System.out.println( "box size: " + p.getFloatPosition( 0 ) + " " + p.getFloatPosition( 1 ) + " " + p.getFloatPosition( 2 ) );

					p.setPosition( coordinates[ 0 ], 0 );
					p.setPosition( coordinates[ 1 ], 1 );
					p.setPosition( coordinates[ 2 ], 2 );
					toWorldCoordinates.apply( p, p );
					System.out.println( "coordinates: " + p.getFloatPosition( 0 ) + " " + p.getFloatPosition( 1 ) + " " + p.getFloatPosition( 2 ) );
					chunk.setPosition( new GLVector( p.getFloatPosition( 0 ), p.getFloatPosition( 1 ), p.getFloatPosition( 2 ) ) );

					final int color = stream.argb( selectedSegmentId );
					final GLVector colorVector = new GLVector( ( color >>> 16 & 0xff ) * ONE_OVER_255, ( color >>> 8 & 0xff ) * ONE_OVER_255, ( color >>> 0 & 0xff ) * ONE_OVER_255 );
					chunk.getMaterial().setDiffuse( colorVector );

					// TODO: this is not working properly... the transparency
					// and the opacity are not combined
					chunk.getMaterial().setOpacity( 0.1f );
//					chunk.getMaterial().setTransparent( true );
					scene.addChild( chunk );

					final MarchingCubes< T > mc = new MarchingCubes<>( data, interval, toWorldCoordinates, cubeSize );
					final float[] vertices = mc.generateMesh( foregroundCheck );

					if ( vertices.length > 0 )
					{

						final float[] normals = new float[ vertices.length ];
//						MarchingCubes.surfaceNormals( vertices, normals );
						MarchingCubes.averagedSurfaceNormals( vertices, normals );
						final Mesh mesh = new Mesh();
						final Material material = new Material();
						material.setDiffuse( colorVector );
						material.setOpacity( 1.0f );
						material.setAmbient( new GLVector( 0.5f, 0.5f, 0.5f ) );
						material.setSpecular( new GLVector( 1f, 0.0f, 1f ) );
						mesh.setMaterial( material );
						mesh.setPosition( new GLVector( 0.0f, 0.0f, 0.0f ) );
						mesh.setVertices( FloatBuffer.wrap( vertices ) );
						mesh.setNormals( FloatBuffer.wrap( normals ) );
//						mesh.recalculateNormals();

						synchronized ( nodes )
						{
							if ( !isCanceled )
							{
								scene.removeChild( chunk );
								nodes.add( mesh );
								scene.addChild( mesh );
								mesh.setDirty( true );
//								mesh.generateBoundingBox();

//								final Box hull = new Box( new GLVector( completeBoundingBox[ 3 ] - completeBoundingBox[ 0 ], completeBoundingBox[ 4 ] - completeBoundingBox[ 1 ], completeBoundingBox[ 5 ] - completeBoundingBox[ 2 ] ), true );
//								hull.setPosition( new GLVector( completeBoundingBox[ 0 ], completeBoundingBox[ 1 ], completeBoundingBox[ 2 ] ) );
//								hull.setMaterial( material );

//								updateCompleteBoundingBox( mesh );
//								GLVector position = scene.getActiveObserver().getPosition();
//								position.set( 2, completeBoundingBox[ 2 ] );
//								scene.getActiveObserver().setPosition( position );
//								GLVector forward = scene.getActiveObserver().getForward();
//								System.out.println( "forward " + forward.get( 0 ) + forward.get( 1 ) + forward.get( 2 ) );
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
					else
						scene.removeChild( chunk );
				} ) );
			}
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

	public void updateCompleteBoundingBox( Mesh mesh )
	{
		FloatBuffer vertices = mesh.getVertices();
		float[] verticesArray = vertices.array();
		float minX = ( float ) IntStream.range( 0, verticesArray.length ).mapToDouble( i -> verticesArray[ i ] ).filter( i -> ( i + 3 ) % 3 == 0 ).min().getAsDouble();
		float minY = ( float ) IntStream.range( 0, verticesArray.length ).mapToDouble( i -> verticesArray[ i ] ).filter( i -> ( i + 2 ) % 3 == 0 ).min().getAsDouble();
		float minZ = ( float ) IntStream.range( 0, verticesArray.length ).mapToDouble( i -> verticesArray[ i ] ).filter( i -> ( i + 1 ) % 3 == 0 ).min().getAsDouble();
		float maxX = ( float ) IntStream.range( 0, verticesArray.length ).mapToDouble( i -> verticesArray[ i ] ).filter( i -> ( i + 3 ) % 3 == 0 ).max().getAsDouble();
		float maxY = ( float ) IntStream.range( 0, verticesArray.length ).mapToDouble( i -> verticesArray[ i ] ).filter( i -> ( i + 2 ) % 3 == 0 ).max().getAsDouble();
		float maxZ = ( float ) IntStream.range( 0, verticesArray.length ).mapToDouble( i -> verticesArray[ i ] ).filter( i -> ( i + 1 ) % 3 == 0 ).max().getAsDouble();

		System.out.println( "calculated bb: " + minX + "x" + minY + "x" + minZ + " " + maxX + "x" + maxY + "x" + maxZ );
		float[] boundingBox = new float[] { minX, minY, minZ, maxX, maxY, maxZ };

//		assert ( completeBoundingBox.length == boundingBox.length );
//
		if ( nodes.size() == 1 )
			completeBoundingBox = boundingBox;

		// TODO: the bb cames with min, max, min, max!
		for ( int d = 0; d < completeBoundingBox.length; d++ )
		{
			if ( d < ( completeBoundingBox.length / 2 ) && completeBoundingBox[ d ] > boundingBox[ d ] )
				completeBoundingBox[ d ] = boundingBox[ d ];
			else if ( d >= ( completeBoundingBox.length / 2 ) && completeBoundingBox[ d ] < boundingBox[ d ] )
				completeBoundingBox[ d ] = boundingBox[ d ];
		}

		System.out.println( "completeBB: " + completeBoundingBox[ 0 ] + "x" + completeBoundingBox[ 1 ] + "x" + completeBoundingBox[ 2 ] +
				" " + completeBoundingBox[ 3 ] + "x" + completeBoundingBox[ 4 ] + "x" + completeBoundingBox[ 5 ] );

	}

	public GLVector getCameraPosition()
	{
		float[] cameraPosition = new float[ 3 ];
		cameraPosition[ 0 ] = ( completeBoundingBox[ 0 ] );

		for ( int i = 0; i < completeBoundingBox.length / 2; i++ )
		{
			cameraPosition[ i ] = ( completeBoundingBox[ i ] );// +
																// completeBoundingBox[
																// i + 3 ] ) /
																// 2;
		}

		// z-axis
		double distance = Math.abs( completeBoundingBox[ 5 ] - completeBoundingBox[ 2 ] ) / 2 / Math.tan( scene.getActiveObserver().getFov() / 2 );
		cameraPosition[ 2 ] = ( float ) ( completeBoundingBox[ 2 ] + distance );
		
//		System.out.println( "camera position: " + cameraPosition[ 0 ] + " " + cameraPosition[ 1 ] + " " + cameraPosition[ 2 ] );

		return new GLVector( cameraPosition[ 0 ], cameraPosition[ 1 ], cameraPosition[ 2 ] );
	}
}
