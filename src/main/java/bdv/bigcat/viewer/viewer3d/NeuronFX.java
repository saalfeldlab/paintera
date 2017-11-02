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
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.ObservableFaceArray;
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
	private static float ONE_OVER_255 = 1.0f / 255.0f;

	private final NeuronRendererFX< T, ? > generator;

	private final Interval interval;

	private final Group root;

	public NeuronFX( final NeuronRendererFX< T, ? > generator, final Interval interval, final Group root )
	{
		super();
		this.generator = generator;
		this.interval = interval;
		this.root = root;
	}

	private final List< MeshView > nodes = new ArrayList<>();

	private final List< Future< ? > > futures = new ArrayList<>();

	private final List< Box > chunks = new ArrayList<>();

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
			this.chunks.forEach( chunk -> root.getChildren().remove( chunk ) );
		}
	}

	public void removeSelf()
	{
		cancel();
		InvokeOnJavaFXApplicationThread.invoke( () -> {
			root.getChildren().removeAll( this.nodes );
			root.getChildren().removeAll( this.chunks );
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
					this.chunks.add( chunk );

					final MarchingCubes< T > mc = new MarchingCubes<>( data, interval, toWorldCoordinates, cubeSize );
					final float[] vertices = mc.generateMesh( foregroundCheck );

					if ( vertices.length > 0 )
					{
						final float[] normals = new float[ vertices.length ];
//						MarchingCubes.surfaceNormals( vertices, normals );
						MarchingCubes.averagedSurfaceNormals( vertices, normals );
						final TriangleMesh mesh = new TriangleMesh();
						final PhongMaterial material = new PhongMaterial();
						material.setDiffuseColor( surfaceColor );
						material.setSpecularColor( Color.WHITE );

						final MeshView mv = new MeshView( mesh );
						mv.setOpacity( 0.5 );
//						mv.setCullFace( CullFace.NONE );
						mv.setMaterial( material );
						mesh.getPoints().addAll( vertices );
						mesh.getNormals().addAll( normals );
						mesh.getTexCoords().addAll( 0, 0 );
						mesh.setVertexFormat( VertexFormat.POINT_NORMAL_TEXCOORD );
						final ObservableFaceArray faces = mesh.getFaces();
						final int[] arr = new int[ 3 ];
						for ( int i = 0, k = 0; i < vertices.length; i += 3, ++k )
						{
							arr[ 0 ] = k;
							arr[ 1 ] = k;
							arr[ 2 ] = 0;
							faces.addAll( arr );
						}

						synchronized ( futures )
						{
							if ( !isCanceled )
							{
								this.chunks.remove( chunk );
								InvokeOnJavaFXApplicationThread.invoke( () -> root.getChildren().remove( chunk ) );
								addNode( mv );
//								generator.updateCompleteBoundingBox( mesh.generateBoundingBox() );
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
					else
					{
						this.chunks.remove( chunk );
						InvokeOnJavaFXApplicationThread.invoke( () -> root.getChildren().remove( chunk ) );
					}
				} ) );
		}
	}

	private void addNode( final MeshView node )
	{
		synchronized ( this.nodes )
		{
			this.nodes.add( node );
		}
		InvokeOnJavaFXApplicationThread.invoke( () -> this.root.getChildren().add( node ) );
	}
}
