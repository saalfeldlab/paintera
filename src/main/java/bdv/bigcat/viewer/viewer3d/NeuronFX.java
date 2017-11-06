package bdv.bigcat.viewer.viewer3d;

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
import bdv.util.InvokeOnJavaFXApplicationThread;
import javafx.collections.ObservableFloatArray;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccessible;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;

public class NeuronFX< T >
{
	private final Interval interval;

	private final Group root;

	private final List< Future< ? > > futures = new ArrayList<>();

	private final Set< HashWrapper< long[] > > offsets = new HashSet<>();

	private boolean isCanceled = false;

	private final MeshView node;

	private final PhongMaterial material;

	private final Object meshModificationLock = new Object();

	public NeuronFX( final Interval interval, final Group root )
	{
		super();
		this.interval = interval;
		this.root = root;
		this.node = new MeshView();
		this.material = new PhongMaterial();
		this.node.setOpacity( 1.0 );
		this.node.setCullFace( CullFace.FRONT );
		this.node.setMaterial( material );
	}

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
		cancel();
		this.isCanceled = false;
		final long[] gridCoordinates = new long[ initialLocationInImageCoordinates.numDimensions() ];
		initialLocationInImageCoordinates.localize( gridCoordinates );
		for ( int i = 0; i < gridCoordinates.length; ++i )
			gridCoordinates[ i ] /= blockSize[ i ];
		try
		{
			initializeMeshView( color );
			submitForOffset( gridCoordinates, data, foregroundCheck, toWorldCoordinates, blockSize, cubeSize, color, es );
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}
	}

	public void cancel()
	{
		synchronized ( futures )
		{
			this.isCanceled = true;
			this.futures.forEach( future -> future.cancel( true ) );
		}
	}

	public void removeSelf() throws InterruptedException
	{
		cancel();
		InvokeOnJavaFXApplicationThread.invokeAndWait( () -> {
			root.getChildren().remove( node );
		} );
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
		final TriangleMesh mesh = ( TriangleMesh ) this.node.getMesh();

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

					final RealPoint begin = new RealPoint( interval.numDimensions() );
					final RealPoint end = new RealPoint( interval.numDimensions() );

					for ( int i = 0; i < interval.numDimensions(); i++ )
					{
						begin.setPosition( interval.min( i ), i );
						end.setPosition( interval.max( i ), i );
					}
					toWorldCoordinates.apply( begin, begin );
					toWorldCoordinates.apply( end, end );

					final float[] size = new float[ begin.numDimensions() ];
					final float[] center = new float[ begin.numDimensions() ];
					for ( int i = 0; i < begin.numDimensions(); i++ )
					{
						size[ i ] = end.getFloatPosition( i ) - begin.getFloatPosition( i );
						center[ i ] = begin.getFloatPosition( i ) + size[ i ] / 2;
					}
					final Box chunk = new Box( size[ 0 ], size[ 1 ], size[ 2 ] );
					chunk.setTranslateX( center[ 0 ] );
					chunk.setTranslateY( center[ 1 ] );
					chunk.setTranslateZ( center[ 2 ] );

					final PhongMaterial chunkMaterial = new PhongMaterial();
					final Color surfaceColor = Color.rgb( color >>> 16 & 0xff, color >>> 8 & 0xff, color >>> 0 & 0xff, 1.0 );
					chunkMaterial.setDiffuseColor( surfaceColor );
					chunk.setMaterial( chunkMaterial );
					chunk.setOpacity( 0.3 );
//
					InvokeOnJavaFXApplicationThread.invoke( () -> root.getChildren().add( chunk ) );

					final MarchingCubes< T > mc = new MarchingCubes<>( data, interval, toWorldCoordinates, cubeSize );
					final float[] vertices = mc.generateMesh( foregroundCheck );

					InvokeOnJavaFXApplicationThread.invoke( () -> root.getChildren().remove( chunk ) );

					if ( vertices.length > 0 )
					{
						final float[] normals = new float[ vertices.length ];
//						MarchingCubes.surfaceNormals( vertices, normals );
						MarchingCubes.averagedSurfaceNormals( vertices, normals );
						for ( int i = 0; i < normals.length; ++i )
							normals[ i ] *= -1;

						synchronized ( meshModificationLock )
						{
							final ObservableFloatArray meshVertices = mesh.getPoints();
							final ObservableFloatArray meshNormals = mesh.getNormals();
							final int numVerticesInMesh = meshVertices.size() / 3;
							final int[] faceIndices = new int[ vertices.length ];
							for ( int i = 0, k = numVerticesInMesh; i < vertices.length; i += 3, ++k )
							{
								faceIndices[ i + 0 ] = k;
								faceIndices[ i + 1 ] = k;
								faceIndices[ i + 2 ] = 0;
							}

							try
							{
								InvokeOnJavaFXApplicationThread.invokeAndWait( () -> {
									if ( !isCanceled )
									{
										meshVertices.addAll( vertices );
										meshNormals.addAll( normals );
										mesh.getFaces().addAll( faceIndices );
									}
									meshVertices.addAll( vertices );
									meshNormals.addAll( normals );
									mesh.getFaces().addAll( faceIndices );
								} );
							}
							catch ( final InterruptedException e )
							{
								e.printStackTrace();
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

	private void initializeMeshView( final int color ) throws InterruptedException
	{
		final TriangleMesh mesh = new TriangleMesh();
		mesh.getTexCoords().addAll( 0, 0 );
		mesh.setVertexFormat( VertexFormat.POINT_NORMAL_TEXCOORD );
		final Color surfaceColor = Color.rgb( color >>> 16 & 0xff, color >>> 8 & 0xff, color >>> 0 & 0xff, 1.0 );
		this.node.setMesh( mesh );
		this.material.setDiffuseColor( surfaceColor );
		this.node.setMaterial( this.material );
		InvokeOnJavaFXApplicationThread.invokeAndWait( () -> {
			this.root.getChildren().add( this.node );
		} );
	}
}
