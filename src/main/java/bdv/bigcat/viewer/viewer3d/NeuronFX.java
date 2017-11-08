package bdv.bigcat.viewer.viewer3d;

import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import bdv.bigcat.viewer.viewer3d.marchingCubes.ForegroundCheck;
import bdv.bigcat.viewer.viewer3d.marchingCubes.MarchingCubes;
import bdv.bigcat.viewer.viewer3d.util.HashWrapper;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableFloatArray;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
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

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final Interval interval;

	private final Group root;

	private final ObservableList< Future< ? > > futures = FXCollections.observableArrayList();

	private final Set< HashWrapper< long[] > > offsets = new HashSet<>();

	private boolean isCanceled = false;

	private final ObjectProperty< Group > meshes = new SimpleObjectProperty<>();

	private final SimpleBooleanProperty isReady = new SimpleBooleanProperty( false );

	public NeuronFX( final Interval interval, final Group root )
	{
		super();
		this.interval = interval;
		this.root = root;
		meshes.addListener( ( obsv, oldv, newv ) -> {
			InvokeOnJavaFXApplicationThread.invoke( () -> root.getChildren().remove( oldv ) );
			if ( newv != null )
				InvokeOnJavaFXApplicationThread.invoke( () -> root.getChildren().add( newv ) );
		} );
		futures.addListener( ( ListChangeListener< Future< ? > > ) c -> {
			synchronized ( futures )
			{
				if ( futures.size() == 0 && !isCanceled )
					isReady.set( true );
			}
		} );
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
		this.isReady.set( false );
		this.meshes.set( new Group() );
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
			this.futures.clear();
		}
	}

	public void removeSelf() throws InterruptedException
	{
		cancel();
		InvokeOnJavaFXApplicationThread.invokeAndWait( () -> {
			this.meshes.set( null );
		} );
	}

	public void removeSelfUnchecked()
	{
		try
		{
			removeSelf();
		}
		catch ( final InterruptedException e )
		{
			LOG.debug( "Was interrupted when removing neuron!", e );
		}
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
//		final TriangleMesh mesh = ( TriangleMesh ) this.node.getMesh();

		synchronized ( offsets )
		{
			if ( isCanceled || offsets.contains( offset ) || !Intervals.contains( this.interval, new Point( coordinates ) ) )
				return;
			offsets.add( offset );
		}
		final Group meshes = this.meshes.get();
		if ( !isCanceled && meshes != null )
		{
			final Future< ? > future = es.submit( () -> {
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
				InvokeOnJavaFXApplicationThread.invoke( () -> meshes.getChildren().add( chunk ) );

				final MarchingCubes< T > mc = new MarchingCubes<>( data, interval, toWorldCoordinates, cubeSize );
				final float[] vertices = mc.generateMesh( foregroundCheck );

				InvokeOnJavaFXApplicationThread.invoke( () -> meshes.getChildren().remove( chunk ) );

				if ( vertices.length > 0 )
				{
					{

						final MeshView mv = initializeMeshView( color, vertices );

						if ( !isCanceled )
							InvokeOnJavaFXApplicationThread.invoke( () -> meshes.getChildren().add( mv ) );

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
			} );
			synchronized ( futures )
			{
				futures.add( future );
			}
			es.execute( () -> {
				try
				{
					synchronized ( futures )
					{
						if ( !isCanceled )
						{
							future.get();
							futures.remove( future );
						}
					}
				}
				catch ( InterruptedException | ExecutionException e )
				{
//					e.printStackTrace();
				}
			} );
		}
	}

	private static MeshView initializeMeshView( final int color, final float[] vertices )
	{
		final TriangleMesh mesh = new TriangleMesh();
		mesh.setVertexFormat( VertexFormat.POINT_NORMAL_TEXCOORD );

		final MeshView meshView = new MeshView();
		meshView.setCullFace( CullFace.NONE );

		final PhongMaterial material = new PhongMaterial();
		final Color surfaceColor = Color.rgb( color >>> 16 & 0xff, color >>> 8 & 0xff, color >>> 0 & 0xff, 1.0 );
		meshView.setOpacity( 1.0 );
		material.setDiffuseColor( surfaceColor );
		meshView.setMaterial( material );

		final float[] normals = new float[ vertices.length ];
		MarchingCubes.averagedSurfaceNormals( vertices, normals );
		for ( int i = 0; i < normals.length; ++i )
			normals[ i ] *= -1;

		final ObservableFloatArray meshVertices = mesh.getPoints();
		final ObservableFloatArray meshNormals = mesh.getNormals();
		final ObservableFaceArray faces = mesh.getFaces();
		final int[] faceIndices = new int[ vertices.length ];
		for ( int i = 0, k = 0; i < vertices.length; i += 3, ++k )
		{
			faceIndices[ i + 0 ] = k;
			faceIndices[ i + 1 ] = k;
			faceIndices[ i + 2 ] = 0;
		}

		mesh.getTexCoords().addAll( 0, 0 );
		meshVertices.addAll( vertices );
		meshNormals.addAll( normals );
		faces.addAll( faceIndices );

		meshView.setMesh( mesh );

		return meshView;

	}

	public Group meshes()
	{
		return this.meshes.get();
	}

	public BooleanProperty isReadyProperty()
	{
		return isReady;
	}

//	private void hasNeighboringData( Localizable location, boolean[] neighboring )
//	{
//		// for each dimension, verifies (first in the +, then in the -
//		// direction)
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
//	private void checkData( int i, Interval chunkInterval, RandomAccessible< T > data, final ForegroundCheck< T > foregroundCheck, boolean[] neighboring, String direction )
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
//		Cursor< T > cursor = Views.flatIterable( data ).cursor();
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
}
