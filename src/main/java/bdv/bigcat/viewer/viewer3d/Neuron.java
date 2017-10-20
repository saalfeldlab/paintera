package bdv.bigcat.viewer.viewer3d;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

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
import net.imglib2.RandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;

public class Neuron< T >
{

	private static float ONE_OVER_255 = 1.0f / 255.0f;

	private final NeuronRenderer< T, ? > generator;

	private final Interval interval;

	private final Scene scene;

	public Neuron( final NeuronRenderer< T, ? > generator, final Interval interval, final Scene scene )
	{
		super();
		this.generator = generator;
		this.interval = interval;
		this.scene = scene;
	}

	private final List< Node > nodes = new ArrayList<>();

	private final List< Future< ? > > futures = new ArrayList<>();

	private final Set< HashWrapper< long[] > > offsets = new HashSet<>();

	private boolean isCanceled = false;

	public void render(
			final Localizable initialLocationInImageCoordinates,
			final RandomAccessible< T > data,
			final ForegroundCheck< T > foregroundCheck,
			final AffineTransform3D toWorldCoordinates,
			final int[] blockSize,
			final int[] cubeSize,
			final int color,
			final ExecutorService es )
	{
		final long[] gridCoordinates = new long[ initialLocationInImageCoordinates.numDimensions() ];
		initialLocationInImageCoordinates.localize( gridCoordinates );
		for ( int i = 0; i < gridCoordinates.length; ++i )
			gridCoordinates[ i ] /= blockSize[ i ];
		submitForOffset( gridCoordinates, data, foregroundCheck, toWorldCoordinates, blockSize, cubeSize, color, es );
	}

	public void cancel()
	{
		synchronized ( futures )
		{
			this.isCanceled = true;
			this.futures.forEach( future -> future.cancel( true ) );
		}
	}

	public void removeSelf()
	{
		cancel();
		this.nodes.forEach( scene::removeChild );
	}

	private void submitForOffset(
			final long[] gridCoordinates,
			final RandomAccessible< T > data,
			final ForegroundCheck< T > foregroundCheck,
			final AffineTransform3D toWorldCoordinates,
			final int[] blockSize,
			final int[] cubeSize,
			final int color,
			final ExecutorService es )
	{
		final HashWrapper< long[] > offset = HashWrapper.longArray( gridCoordinates );
		final long[] coordinates = IntStream.range( 0, gridCoordinates.length ).mapToLong( d -> gridCoordinates[ d ] * blockSize[ d ] ).toArray();

		synchronized ( futures )
		{
			synchronized ( offsets )
			{
				if ( isCanceled || offsets.contains( offset ) || !Intervals.contains( this.interval, new Point( coordinates ) ) )
					return;
				offsets.add( offset );
			}
			if ( !isCanceled )
				this.futures.add( es.submit( () -> {
					final Interval interval = new FinalInterval(
							coordinates,
							IntStream.range( 0, coordinates.length ).mapToLong( d -> coordinates[ d ] + blockSize[ d ] ).toArray() );
					final MarchingCubes< T > mc = new MarchingCubes<>( data, interval, toWorldCoordinates, cubeSize );
					final float[] vertices = mc.generateMesh( foregroundCheck );

					if ( vertices.length > 0 )
					{

						final float[] normals = new float[ vertices.length ];
//						MarchingCubes.surfaceNormals( vertices, normals );
						MarchingCubes.averagedSurfaceNormals( vertices, normals );
						final Mesh mesh = new Mesh();
						final Material material = new Material();
						material.setOpacity( 1.0f );
						material.setDiffuse( new GLVector( ( color >>> 16 & 0xff ) * ONE_OVER_255, ( color >>> 8 & 0xff ) * ONE_OVER_255, ( color >>> 0 & 0xff ) * ONE_OVER_255 ) );
						material.setAmbient( new GLVector( ( color >>> 16 & 0xff ) * ONE_OVER_255, ( color >>> 8 & 0xff ) * ONE_OVER_255, ( color >>> 0 & 0xff ) * ONE_OVER_255 ) );
						material.setSpecular( new GLVector( 0.0f, 0.0f, 0.0f ) );
						mesh.setMaterial( material );
						mesh.setPosition( new GLVector( 0.0f, 0.0f, 0.0f ) );
						mesh.setVertices( FloatBuffer.wrap( vertices ) );
						mesh.setNormals( FloatBuffer.wrap( normals ) );
						synchronized ( futures )
						{
							if ( !isCanceled )
							{
								addNode( mesh );
								mesh.setDirty( true );
							}
						}

						for ( int d = 0; d < gridCoordinates.length; ++d )
						{
							final long[] otherGridCoordinates = gridCoordinates.clone();
							otherGridCoordinates[ d ] += 1;
							submitForOffset( otherGridCoordinates.clone(), data, foregroundCheck, toWorldCoordinates, blockSize, cubeSize, color, es );

							otherGridCoordinates[ d ] -= 2;
							submitForOffset( otherGridCoordinates.clone(), data, foregroundCheck, toWorldCoordinates, blockSize, cubeSize, color, es );
						}

					}
				} ) );
		}

	}

	private void addNode( final Node node )
	{
		synchronized ( this.nodes )
		{
			this.nodes.add( node );
		}
		this.scene.addChild( node );
	}

}
