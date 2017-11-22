package bdv.bigcat.viewer.viewer3d;

import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.util.DecorateRunnable;
import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import bdv.bigcat.viewer.viewer3d.marchingCubes.MarchingCubes;
import bdv.bigcat.viewer.viewer3d.util.HashWrapper;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableFloatArray;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.ObservableFaceArray;
import javafx.scene.shape.Shape3D;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccessible;
import net.imglib2.RealPoint;
import net.imglib2.cache.LoaderCache;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.BooleanType;
import net.imglib2.util.Intervals;

public class NeuronFX
{
	public static class ShapeKey
	{
		private final long shapeId;

		private final int scaleIndex;

		private final long x;
		private final long y;
		private final long z;

		public ShapeKey(
				final long shapeId,
				final int scaleIndex,
				final long x,
				final long y,
				final long z )
		{
			this.shapeId = shapeId;
			this.scaleIndex = scaleIndex;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override
		public int hashCode()
		{
			int result = scaleIndex;
			result = 31 * result + (int) (shapeId ^ (shapeId >>> 32));
			result = 31 * result + (int) (x ^ (x >>> 32));
	        result = 31 * result + (int) (y ^ (y >>> 32));
	        result = 31 * result + (int) (z ^ (z >>> 32));
	        return result;
		}

		@Override
		public boolean equals( final Object other )
		{
			if ( other instanceof ShapeKey )
			{
				final ShapeKey otherShapeKey = ( ShapeKey )other;
				return ( shapeId == otherShapeKey.shapeId ) || ( x == otherShapeKey.x ) || ( y == otherShapeKey.y ) || ( z == otherShapeKey.z );
			}
			return false;
		}
	}

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final Interval interval;

	private final Group root;

	private final ObservableList< Future< ? > > futures = FXCollections.observableArrayList();

	private final Set< HashWrapper< long[] > > offsets = new HashSet<>();

	private boolean isCanceled = false;

	private final ObjectProperty< Group > meshes = new SimpleObjectProperty<>();

	private final SimpleBooleanProperty isReady = new SimpleBooleanProperty( false );

	private final AtomicLong futuresStartedCount = new AtomicLong( 0 );

	private final AtomicLong futuresFinishedCount = new AtomicLong( 0 );

	private final ObjectProperty< Color > colorProperty = new SimpleObjectProperty<>();

	private final LoaderCache< ShapeKey, Shape3D > shapeCache;

	public NeuronFX( final Interval interval, final Group root, final LoaderCache< ShapeKey, Shape3D > shapeCache )
	{
		super();
		this.interval = interval;
		this.root = root;
		this.shapeCache = shapeCache;
		meshes.addListener( ( obsv, oldv, newv ) -> {
			InvokeOnJavaFXApplicationThread.invoke( () -> root.getChildren().remove( oldv ) );
			if ( newv != null )
				InvokeOnJavaFXApplicationThread.invoke( () -> root.getChildren().add( newv ) );
		} );
	}

	public < B extends BooleanType< B > > void render(
			final Localizable initialLocationInImageCoordinates,
			final RandomAccessible< B > data,
			final AffineTransform3D toWorldCoordinates,
			final int[] blockSize,
			final int[] cubeSize,
			final int color,
			final ExecutorService es )
	{
		cancel();
		this.futuresStartedCount.set( 0 );
		this.futuresFinishedCount.set( 0 );
		this.isCanceled = false;
		this.isReady.set( false );
		this.meshes.set( new Group() );
		setColor( color );
		final long[] gridCoordinates = new long[ initialLocationInImageCoordinates.numDimensions() ];
		initialLocationInImageCoordinates.localize( gridCoordinates );
		for ( int i = 0; i < gridCoordinates.length; ++i )
			gridCoordinates[ i ] /= blockSize[ i ];
		submitForOffset( gridCoordinates, data, toWorldCoordinates, blockSize, cubeSize, color, es );
	}

	public void cancel()
	{
		synchronized ( futures )
		{
			this.isCanceled = true;
			this.offsets.clear();
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

	public ObjectProperty< Color > colorProperty()
	{
		return this.colorProperty;
	}

	public void setColor( final int color )
	{
		colorProperty.set( Color.rgb( ( color & 0x00ff0000 ) >>> 16, ( color & 0x0000ff00 ) >>> 8, color & 0xff, 1.0 ) );
	}

	private < B extends BooleanType< B > > void submitForOffset(
			final long[] gridCoordinates,
			final RandomAccessible< B > data,
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
			final Future< ? > future = es.submit(
					DecorateRunnable.beforeAndAfter(
							() -> generateMesh( data, gridCoordinates, coordinates, blockSize, cubeSize, toWorldCoordinates, color, meshes, es ),
							futuresStartedCount::incrementAndGet,
							this::incrementFuturesFinishedCount ) );
			synchronized ( futures )
			{
				futures.add( future );
			}
		}
	}

	private static MeshView initializeMeshView( final float[] vertices, final Property< Color > color )
	{
		final TriangleMesh mesh = new TriangleMesh();
		mesh.setVertexFormat( VertexFormat.POINT_NORMAL_TEXCOORD );

		final MeshView meshView = new MeshView();
		meshView.setCullFace( CullFace.NONE );

		final PhongMaterial material = new PhongMaterial();
		meshView.setOpacity( 1.0 );
		material.setSpecularColor( new Color( 1, 1, 1, 1.0 ) );
		material.setSpecularPower( 50 );
		material.diffuseColorProperty().bind( color );
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

	private void incrementFuturesFinishedCount()
	{
		final long finishedCount = futuresFinishedCount.incrementAndGet();
		final long startedCount = futuresStartedCount.get();
		if ( !isCanceled && finishedCount == startedCount )
			isReady.set( true );
	}

	private < B extends BooleanType< B > > void generateMesh(
			final RandomAccessible< B > data,
			final long[] gridCoordinates,
			final long[] coordinates,
			final int[] blockSize,
			final int[] cubeSize,
			final AffineTransform3D toWorldCoordinates,
			final int color,
			final Group meshes,
			final ExecutorService es )
	{
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
		chunkMaterial.diffuseColorProperty().bind( this.colorProperty );
		chunk.setMaterial( chunkMaterial );
		chunk.setOpacity( 0.3 );

		InvokeOnJavaFXApplicationThread.invoke( () -> meshes.getChildren().add( chunk ) );

//		shapeCache.get( new ShapeKey( shapeId, scaleIndex, x, y, z ), loader );
		final MarchingCubes< B > mc = new MarchingCubes<>( data, interval, toWorldCoordinates, cubeSize );
		final float[] vertices = mc.generateMesh();

		InvokeOnJavaFXApplicationThread.invoke( () -> meshes.getChildren().remove( chunk ) );

		if ( vertices.length > 0 )
		{
			final MeshView mv = initializeMeshView( vertices, this.colorProperty );

			if ( !isCanceled )
			{
				InvokeOnJavaFXApplicationThread.invoke( () -> meshes.getChildren().add( mv ) );

				for ( int d = 0; d < gridCoordinates.length; ++d )
				{
					final long[] otherGridCoordinates = gridCoordinates.clone();
					otherGridCoordinates[ d ] += 1;
					submitForOffset( otherGridCoordinates.clone(), data, toWorldCoordinates, blockSize, cubeSize, color, es );

					otherGridCoordinates[ d ] -= 2;
					submitForOffset( otherGridCoordinates.clone(), data, toWorldCoordinates, blockSize, cubeSize, color, es );
				}
			}
		}
	}

}
