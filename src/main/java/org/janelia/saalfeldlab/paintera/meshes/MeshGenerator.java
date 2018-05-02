package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
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
import javafx.scene.shape.MeshView;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Pair;

/**
 *
 * @author Philipp Hanslovsky
 *
 */
public class MeshGenerator
{
	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static class ShapeKey
	{
		private final long shapeId;

		private final int scaleIndex;

		private final int simplificationIterations;

		private final double smoothingLambda;

		private final int smoothingIterations;

		private final long[] min;

		private final long[] max;

		public ShapeKey(
				final long shapeId,
				final int scaleIndex,
				final int simplificationIterations,
				final double smoothingLambda,
				final int smoothingIterations,
				final long[] min,
				final long[] max )
		{
			this.shapeId = shapeId;
			this.scaleIndex = scaleIndex;
			this.simplificationIterations = simplificationIterations;
			this.smoothingLambda = smoothingLambda;
			this.smoothingIterations = smoothingIterations;
			this.min = min;
			this.max = max;
		}

		@Override
		public String toString()
		{
			return String.format(
					"{shapeId=%d, scaleIndex=%d, simplifications=%d, smoothingLambda=%f, smoothings=%d, min=%s, max=%s}",
					shapeId,
					scaleIndex,
					simplificationIterations,
					smoothingLambda,
					smoothingIterations,
					Arrays.toString( min ), Arrays.toString( max ) );
		}

		@Override
		public int hashCode()
		{
			int result = scaleIndex;
			result = 31 * result + ( int ) ( shapeId ^ shapeId >>> 32 );
			result = 31 * result + simplificationIterations;
			result = 31 * result + Double.hashCode( smoothingLambda );
			result = 31 * result + smoothingIterations;
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
						otherShapeKey.simplificationIterations == this.simplificationIterations &&
						otherShapeKey.smoothingLambda == this.smoothingLambda &&
						otherShapeKey.smoothingIterations == this.smoothingIterations &&
						Arrays.equals( otherShapeKey.min, min ) &&
						Arrays.equals( otherShapeKey.max, max );
			}
			return false;
		}

		public long shapeId()
		{
			return shapeId;
		}

		public int scaleIndex()
		{
			return scaleIndex;
		}

		public int simplificationIterations()
		{
			return simplificationIterations;
		}

		public double smoothingLambda()
		{
			return smoothingLambda;
		}

		public int smoothingIterations()
		{
			return smoothingIterations;
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

	private final InterruptibleFunction< Long, Interval[] >[] blockListCache;

	private final InterruptibleFunction< ShapeKey, Pair< float[], float[] > >[] meshCache;

	private final BooleanProperty isVisible = new SimpleBooleanProperty( true );

	private final ObservableMap< ShapeKey, MeshView > meshes = FXCollections.observableHashMap();

	private final IntegerProperty scaleIndex = new SimpleIntegerProperty( 0 );

	private final IntegerProperty meshSimplificationIterations = new SimpleIntegerProperty( 0 );

	private final BooleanProperty changed = new SimpleBooleanProperty( false );

	private final ObservableValue< Color > color;

	private final ObjectProperty< Group > root = new SimpleObjectProperty<>();

	private final BooleanProperty isReady = new SimpleBooleanProperty( true );

	private final ExecutorService managers;

	private final ExecutorService workers;

	private final ObjectProperty< Future< Void > > activeTask = new SimpleObjectProperty<>();

	private final IntegerProperty submittedTasks = new SimpleIntegerProperty( 0 );

	private final IntegerProperty completedTasks = new SimpleIntegerProperty( 0 );

	private final IntegerProperty successfulTasks = new SimpleIntegerProperty( 0 );

	private final MeshGeneratorJobManager manager;

	private final DoubleProperty smoothingLambda = new SimpleDoubleProperty( 0.5 );

	private final IntegerProperty smoothingIterations = new SimpleIntegerProperty( 5 );

	//
	public MeshGenerator(
			final long segmentId,
			final InterruptibleFunction< Long, Interval[] >[] blockListCache,
			final InterruptibleFunction< ShapeKey, Pair< float[], float[] > >[] meshCache,
			final ObservableIntegerValue color,
			final int scaleIndex,
			final int meshSimplificationIterations,
			final double smoothingLambda,
			final int smoothingIterations,
			final ExecutorService managers,
			final ExecutorService workers )
	{
		super();
		this.id = segmentId;
		this.blockListCache = blockListCache;
		this.meshCache = meshCache;
		this.color = Bindings.createObjectBinding( () -> fromInt( color.get() ), color );
		this.managers = managers;
		this.workers = workers;
		this.manager = new MeshGeneratorJobManager( this.meshes, managers, workers );

		this.changed.addListener( ( obs, oldv, newv ) -> new Thread( () -> this.updateMeshes( newv ) ).start() );
		this.changed.addListener( ( obs, oldv, newv ) -> changed.set( false ) );

		this.scaleIndex.set( scaleIndex );
		this.scaleIndex.addListener( ( obs, oldv, newv ) -> changed.set( true ) );

		this.meshSimplificationIterations.set( meshSimplificationIterations );
		this.meshSimplificationIterations.addListener( ( obs, oldv, newv ) -> changed.set( true ) );

		this.smoothingLambda.set( smoothingLambda );
		this.smoothingLambda.addListener( ( obs, oldv, newv ) -> changed.set( true ) );

		this.smoothingIterations.set( smoothingIterations );
		this.smoothingIterations.addListener( ( obs, oldv, newv ) -> changed.set( true ) );

		this.root.addListener( ( obs, oldv, newv ) -> {
			InvokeOnJavaFXApplicationThread.invoke( () -> {
				synchronized ( this.meshes )
				{
					Optional.ofNullable( oldv ).ifPresent( g -> this.meshes.forEach( ( id, mesh ) -> g.getChildren().remove( mesh ) ) );
					Optional.ofNullable( newv ).ifPresent( g -> this.meshes.forEach( ( id, mesh ) -> g.getChildren().add( mesh ) ) );
				}
				if ( newv == null )
				{
					synchronized ( this.activeTask )
					{
						Optional.ofNullable( this.activeTask.get() ).ifPresent( f -> f.cancel( true ) );
						this.activeTask.set( null );
					}
				}
			} );
		} );

		this.meshes.addListener( ( MapChangeListener< ShapeKey, MeshView > ) change -> {
			if ( change.wasRemoved() )
			{
				( ( PhongMaterial ) change.getValueRemoved().getMaterial() ).diffuseColorProperty().unbind();
				change.getValueRemoved().visibleProperty().unbind();
			}
			else
			{
				( ( PhongMaterial ) change.getValueAdded().getMaterial() ).diffuseColorProperty().bind( this.color );
				change.getValueAdded().visibleProperty().bind( this.isVisible );
			}

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

		synchronized ( this.activeTask )
		{
			LOG.warn( "Canceling task: {}", this.activeTask );
			Optional.ofNullable( activeTask.get() ).ifPresent( f -> f.cancel( true ) );
			activeTask.set( null );
			final int scaleIndex = this.scaleIndex.get();
			final Runnable onFinish = () -> {
				synchronized ( activeTask )
				{
					activeTask.set( null );
				}
			};
			final Future< Void > task = manager.submit(
					id,
					scaleIndex,
					meshSimplificationIterations.intValue(),
					smoothingLambda.doubleValue(),
					smoothingIterations.intValue(),
					blockListCache[ scaleIndex ],
					meshCache[ scaleIndex ],
					submittedTasks::set,
					completedTasks::set,
					onFinish );
			LOG.warn( "Submitting new task {}", task );
			this.activeTask.set( task );
		}
	}

	private static final Color fromInt( final int argb )
	{
		return Color.rgb( ARGBType.red( argb ), ARGBType.green( argb ), ARGBType.blue( argb ), 1.0 );
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

	public IntegerProperty smoothingIterationsProperty()
	{
		return smoothingIterations;
	}

	public DoubleProperty smoothingLambdaProperty()
	{
		return smoothingLambda;
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
