package bdv.bigcat.viewer.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableIntegerValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.cache.Cache;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;

/**
 *
 * @author Philipp Hanslovsky
 *
 * @param <T>
 */
public class MeshGenerator< T >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static class ShapeKey
	{
		private final long shapeId;

		private final int scaleIndex;

		private final int meshSimplificationIterations;

		private final long[] min;

		private final long[] max;

		public ShapeKey(
				final long shapeId,
				final int scaleIndex,
				final int meshSimplificationIterations,
				final long[] min,
				final long[] max )
		{
			this.shapeId = shapeId;
			this.scaleIndex = scaleIndex;
			this.meshSimplificationIterations = meshSimplificationIterations;
			this.min = min;
			this.max = max;
		}

		@Override
		public String toString()
		{
			return String.format( "{shapeId=%d, scaleIndex=%d, simplifications=%d, min=%s, max=%s}", shapeId, scaleIndex, meshSimplificationIterations, Arrays.toString( min ), Arrays.toString( max ) );
		}

		@Override
		public int hashCode()
		{
			int result = scaleIndex;
			result = 31 * result + ( int ) ( shapeId ^ shapeId >>> 32 );
			result = 31 * result + meshSimplificationIterations;
			result = 31 * result + Arrays.hashCode( this.min );
			result = 31 * result + Arrays.hashCode( this.max );
			return result;
		}

		@Override
		public boolean equals( final Object other )
		{
			if ( other instanceof ShapeKey )
			{
				final ShapeKey otherShapeKey = ( ShapeKey ) other;
				return shapeId == otherShapeKey.shapeId &&
						otherShapeKey.scaleIndex == scaleIndex &&
						otherShapeKey.meshSimplificationIterations == this.meshSimplificationIterations &&
						Arrays.equals( otherShapeKey.min, min ) &&
						Arrays.equals( otherShapeKey.max, max );
			}
			return false;
		}

		public long shapeId()
		{
			return this.shapeId;
		}

		public int scaleIndex()
		{
			return this.scaleIndex;
		}

		public int meshSimplificationIterations()
		{
			return this.meshSimplificationIterations;
		}

		public long[] min()
		{
			return min.clone();
		}

		public long[] max()
		{
			return max.clone();
		}

		public void min( final long[] min )
		{
			System.arraycopy( this.min, 0, min, 0, min.length );
		}

		public void max( final long[] max )
		{
			System.arraycopy( this.max, 0, max, 0, max.length );
		}

		public Interval interval()
		{
			return new FinalInterval( min, max );
		}

	}

	public static class BlockListKey
	{

		private final long id;

		private final int scaleIndex;

		public BlockListKey( final long id, final int scaleIndex )
		{
			super();
			this.id = id;
			this.scaleIndex = scaleIndex;
		}

		public long id()
		{
			return this.id;
		}

		public int scaleIndex()
		{
			return this.scaleIndex;
		}

		@Override
		public int hashCode()
		{
			int result = scaleIndex;
			result = 31 * result + ( int ) ( id ^ id >> 32 );
			return result;
		}

		@Override
		public boolean equals( final Object other )
		{
			if ( other instanceof BlockListKey )
			{
				final BlockListKey otherKey = ( BlockListKey ) other;
				return id == otherKey.id && scaleIndex == otherKey.scaleIndex;
			}
			return false;
		}

	}

	private final long id;

	private final T source;

	private final Cache< Long, Interval[] >[] blockListCache;

	private final Cache< ShapeKey, Pair< float[], float[] > >[] meshCache;

	private final BooleanProperty isVisible = new SimpleBooleanProperty( true );

	private final ObservableMap< ShapeKey, MeshView > meshes = FXCollections.observableHashMap();

	private final IntegerProperty scaleIndex = new SimpleIntegerProperty( 0 );

	private final IntegerProperty meshSimplificationIterations = new SimpleIntegerProperty( 0 );

	private final BooleanProperty changed = new SimpleBooleanProperty( false );

	private final ObservableValue< Color > color;

	private final ObjectProperty< Group > root = new SimpleObjectProperty<>();

	private final BooleanProperty isReady = new SimpleBooleanProperty( true );

	private final ExecutorService es;

	private final List< Future< ? > > activeTasks = new ArrayList<>();

	private final IntegerProperty submittedTasks = new SimpleIntegerProperty( 0 );

	private final IntegerProperty completedTasks = new SimpleIntegerProperty( 0 );

	private final IntegerProperty successfulTasks = new SimpleIntegerProperty( 0 );

	//
	public MeshGenerator(
			final long segmentId,
			final T source,
			final Cache< Long, Interval[] >[] blockListCache,
			final Cache< ShapeKey, Pair< float[], float[] > >[] meshCache,
			final ObservableIntegerValue color,
			final int scaleIndex,
			final int meshSimplificationIterations,
			final ExecutorService es )
	{
		super();
		this.id = segmentId;
		this.source = source;
		this.blockListCache = blockListCache;
		this.meshCache = meshCache;
		this.color = Bindings.createObjectBinding( () -> fromInt( color.get() ), color );
		this.es = es;

		this.changed.addListener( ( obs, oldv, newv ) -> new Thread( () -> this.updateMeshes( newv ) ).start() );
		this.changed.addListener( ( obs, oldv, newv ) -> changed.set( false ) );
		this.scaleIndex.set( scaleIndex );
		this.meshSimplificationIterations.set( meshSimplificationIterations );

		this.meshSimplificationIterations.addListener( ( obs, oldv, newv ) -> changed.set( true ) );
		this.scaleIndex.addListener( ( obs, oldv, newv ) -> changed.set( true ) );

		this.root.addListener( ( obs, oldv, newv ) -> {
			InvokeOnJavaFXApplicationThread.invoke( () -> {
				synchronized ( this.meshes )
				{
					Optional.ofNullable( oldv ).ifPresent( g -> this.meshes.forEach( ( id, mesh ) -> g.getChildren().remove( mesh ) ) );
					Optional.ofNullable( newv ).ifPresent( g -> this.meshes.forEach( ( id, mesh ) -> g.getChildren().add( mesh ) ) );
				}
			} );
		} );

		this.meshes.addListener( ( MapChangeListener< ShapeKey, MeshView > ) change -> {
			Optional.ofNullable( this.root.get() ).ifPresent( group -> {
				if ( change.wasRemoved() )
					InvokeOnJavaFXApplicationThread.invoke( synchronize( () -> group.getChildren().remove( change.getValueRemoved() ), group ) );
				else if ( change.wasAdded() && !group.getChildren().contains( change.getValueAdded() ) )
					InvokeOnJavaFXApplicationThread.invoke( () -> {
						synchronized ( group )
						{
							final ObservableList< Node > children = group.getChildren();
							if ( !children.contains( change.getValueAdded() ) )
								children.add( change.getValueAdded() );
						}
					} );
			} );
		} );

		this.changed.set( true );

	}

	private void updateMeshes( final boolean doUpdate )
	{
		LOG.debug( "Updating mesh? {}", doUpdate );
		if ( !doUpdate )
			return;
		synchronized ( meshes )
		{
			this.meshes.clear();
		}

		synchronized ( activeTasks )
		{
			this.activeTasks.forEach( f -> f.cancel( true ) );
			this.activeTasks.clear();
		}

		final int scaleIndex = this.scaleIndex.get();
		final List< Interval > blockList = new ArrayList<>();
		try
		{
			blockList.addAll( Arrays.asList( blockListCache[ scaleIndex ].get( id ) ) );
		}
		catch ( final ExecutionException e )
		{
			LOG.warn( "Could not get mesh block list for id {}: {}", id, e.getMessage() );
			return;
		}

		LOG.debug( "Generating mesh with {} blocks for fragment {}.", blockList.size(), this.id );

		final List< ShapeKey > keys = new ArrayList<>();
		for ( final Interval block : blockList )
			keys.add( new ShapeKey( id, scaleIndex, meshSimplificationIterations.get(), Intervals.minAsLongArray( block ), Intervals.maxAsLongArray( block ) ) );
		final ArrayList< Callable< Void > > tasks = new ArrayList<>();
		final ArrayList< Future< Void > > futures = new ArrayList<>();
		for ( final ShapeKey key : keys )
			tasks.add( () -> {
				final String initialName = Thread.currentThread().getName();
				try
				{
					Thread.currentThread().setName( initialName + " -- generating mesh: " + key );
					LOG.trace( "Set name of current thread to {} ( was {})", Thread.currentThread().getName(), initialName );
					final Pair< float[], float[] > verticesAndNormals = meshCache[ scaleIndex ].get( key );
					final float[] vertices = verticesAndNormals.getA();
					final float[] normals = verticesAndNormals.getB();
					final TriangleMesh mesh = new TriangleMesh();
					mesh.getPoints().addAll( vertices );
					mesh.getNormals().addAll( normals );
					mesh.getTexCoords().addAll( 0, 0 );
					mesh.setVertexFormat( VertexFormat.POINT_NORMAL_TEXCOORD );
					final int[] faceIndices = new int[ vertices.length ];
					for ( int i = 0, k = 0; i < faceIndices.length; i += 3, ++k )
					{
						faceIndices[ i + 0 ] = k;
						faceIndices[ i + 1 ] = k;
						faceIndices[ i + 2 ] = 0;
					}
					mesh.getFaces().addAll( faceIndices );
					final PhongMaterial material = new PhongMaterial();
					material.setSpecularColor( new Color( 1, 1, 1, 1.0 ) );
					material.setSpecularPower( 50 );
					material.diffuseColorProperty().bind( color );
					final MeshView mv = new MeshView( mesh );
					mv.setOpacity( 1.0 );
					synchronized ( this.isVisible )
					{
						mv.visibleProperty().bind( this.isVisible );
					}
					mv.setCullFace( CullFace.NONE );
					mv.setMaterial( material );
					mv.setDrawMode( DrawMode.FILL );
					if ( !Thread.interrupted() )
						synchronized ( meshes )
						{
							meshes.put( key, mv );
						}
				}
				catch ( final ExecutionException e )
				{
					LOG.warn( "Was not able to retrieve mesh for {}: {}", key, e.getMessage() );
				}
				catch ( final RuntimeException e )
				{
					LOG.warn( "{} : {}", e.getClass(), e.getMessage() );
					e.printStackTrace();
					throw e;
				}
				finally
				{
					Thread.currentThread().setName( initialName );
					synchronized ( this.completedTasks )
					{
						this.successfulTasks.set( this.successfulTasks.get() + 1 );
					}
				}
				this.completedTasks.set( this.completedTasks.get() + 1 );
				return null;

			} );

		this.submittedTasks.set( tasks.size() );
		this.successfulTasks.set( 0 );
		this.completedTasks.set( 0 );
		synchronized ( this.activeTasks )
		{
			tasks.stream().map( this.es::submit ).forEach( futures::add );
			this.activeTasks.addAll( futures );
		}
		for ( final Future< Void > future : futures )
			try
			{
				future.get();
			}
			catch ( final Exception e )
			{
				LOG.warn( "{} in neuron mesh generation: {}", e.getClass(), e.getMessage() );
				e.printStackTrace();
			}

	}

	private static final Color fromInt( final int argb )
	{
		return Color.rgb( ARGBType.red( argb ), ARGBType.green( argb ), ARGBType.blue( argb ), 1.0 );
	}

	public T getSource()
	{
		return source;
	}

	public long getId()
	{
		return id;
	}

	public ObjectProperty< Group > rootProperty()
	{
		return this.root;
	}

	public Runnable synchronize( final Runnable r, final Object syncObject )
	{
		return () -> {
			synchronized ( syncObject )
			{
				r.run();
			}
		};
	}

	public IntegerProperty meshSimplificationIterationsProperty()
	{
		return this.meshSimplificationIterations;
	}

	public IntegerProperty scaleIndexProperty()
	{
		return this.scaleIndex;
	}

	public ObservableIntegerValue submittedTasksProperty()
	{
		return this.submittedTasks;
	}

	public ObservableIntegerValue completedTasksProperty()
	{
		return this.completedTasks;
	}

	public ObservableIntegerValue successfulTasksProperty()
	{
		return this.successfulTasks;
	}

}
