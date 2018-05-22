package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableIntegerValue;
import javafx.beans.value.ObservableValue;

public class MeshInfo< T >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final IntegerProperty scaleLevel = new SimpleIntegerProperty();

	private final IntegerProperty simplificationIterations = new SimpleIntegerProperty();

	private final DoubleProperty smoothingLambda = new SimpleDoubleProperty();

	private final IntegerProperty smoothingIterations = new SimpleIntegerProperty();

	private final long segmentId;

	private final FragmentSegmentAssignment assignment;

	private final MeshManager< T > meshManager;

	private final int numScaleLevels;

	private final IntegerProperty submittedTasks = new SimpleIntegerProperty( 0 );

	private final IntegerProperty completedTasks = new SimpleIntegerProperty( 0 );

	private final IntegerProperty successfulTasks = new SimpleIntegerProperty( 0 );

	public MeshInfo(
			final long segmentId,
			final FragmentSegmentAssignment assignment,
			final MeshManager< T > meshManager,
			final int numScaleLevels )
	{
		super();
		this.segmentId = segmentId;
		this.assignment = assignment;
		this.meshManager = meshManager;

		scaleLevel.set( meshManager.scaleLevelProperty().get() );
		scaleLevel.addListener( new PropagateChanges<>( ( mesh, newv ) -> mesh.scaleIndexProperty().set( newv.intValue() ) ) );
		scaleLevel.addListener( (obs, oldv, newv  ) -> LOG.warn( "Changing scale level from {} to {}", oldv, newv ) );

		simplificationIterations.set( meshManager.meshSimplificationIterationsProperty().get() );
		simplificationIterations.addListener( new PropagateChanges<>( ( mesh, newv ) -> mesh.meshSimplificationIterationsProperty().set( newv.intValue() ) ) );

		smoothingLambda.set( meshManager.smoothingLambdaProperty().get() );
		smoothingLambda.addListener( new PropagateChanges<>( ( mesh, newv ) -> mesh.smoothingLambdaProperty().set( newv.doubleValue() ) ) );

		smoothingIterations.set( meshManager.smoothingIterationsProperty().get() );
		smoothingIterations.addListener( new PropagateChanges<>( ( mesh, newv ) -> mesh.smoothingIterationsProperty().set( newv.intValue() ) ) );

		this.numScaleLevels = numScaleLevels;

		updateTasksCountBindings();
		if ( assignment instanceof FragmentSegmentAssignmentState )
		{
			( ( FragmentSegmentAssignmentState ) assignment ).addListener( obs -> updateTasksCountBindings() );
		}

	}

	private void updateTasksCountBindings()
	{
		LOG.debug( "Updating task count bindings." );
		final long[] fragments = assignment.getFragments( segmentId ).toArray();
		final Map< Long, MeshGenerator< T > > meshes = new HashMap<>( meshManager.unmodifiableMeshMap() );
		final ObservableIntegerValue[] submittedTasks = Arrays.stream( fragments ).mapToObj( meshes::get ).filter( mesh -> mesh != null ).map( MeshGenerator::submittedTasksProperty ).toArray( ObservableIntegerValue[]::new );
		final ObservableIntegerValue[] completedTasks = Arrays.stream( fragments ).mapToObj( meshes::get ).filter( mesh -> mesh != null ).map( MeshGenerator::completedTasksProperty ).toArray( ObservableIntegerValue[]::new );
		final ObservableIntegerValue[] successfulTasks = Arrays.stream( fragments ).mapToObj( meshes::get ).filter( mesh -> mesh != null ).map( MeshGenerator::successfulTasksProperty ).toArray( ObservableIntegerValue[]::new );

		this.submittedTasks.bind( Bindings.createIntegerBinding( () -> Arrays.stream( submittedTasks ).mapToInt( ObservableIntegerValue::get ).sum(), submittedTasks ) );
		this.completedTasks.bind( Bindings.createIntegerBinding( () -> Arrays.stream( completedTasks ).mapToInt( ObservableIntegerValue::get ).sum(), completedTasks ) );
		this.successfulTasks.bind( Bindings.createIntegerBinding( () -> Arrays.stream( submittedTasks ).mapToInt( ObservableIntegerValue::get ).sum(), successfulTasks ) );

	}

	public long segmentId()
	{
		return this.segmentId;
	}

	public IntegerProperty scaleLevelProperty()
	{
		return this.scaleLevel;
	}

	public IntegerProperty simplificationIterationsProperty()
	{
		return this.simplificationIterations;
	}

	public DoubleProperty smoothingLambdaProperty()
	{
		return this.smoothingLambda;
	}

	public IntegerProperty smoothingIterationsProperty()
	{
		return this.smoothingIterations;
	}

	public FragmentSegmentAssignment assignment()
	{
		return this.assignment;
	}

	public int numScaleLevels()
	{
		return this.numScaleLevels;
	}

	private class PropagateChanges< U > implements ChangeListener< U >
	{

		final BiConsumer< MeshGenerator< T >, U > apply;

		public PropagateChanges( final BiConsumer< MeshGenerator< T >, U > apply )
		{
			super();
			this.apply = apply;
		}

		@Override
		public void changed( final ObservableValue< ? extends U > observable, final U oldValue, final U newValue )
		{
			final long[] fragments = assignment.getFragments( segmentId ).toArray();
			LOG.debug( "Propagating changes {} {}", segmentId, fragments );
			final Map< Long, MeshGenerator< T > > meshes = meshManager.unmodifiableMeshMap();
			Arrays.stream( fragments ).mapToObj( meshes::get ).filter( m -> m != null ).forEach( n -> apply.accept( n, newValue ) );
		}

	}

	@Override
	public int hashCode()
	{
		return Long.hashCode( segmentId );
	}

	@Override
	public boolean equals( final Object o )
	{
		return o instanceof MeshInfo< ? > && ( ( MeshInfo< ? > ) o ).segmentId == segmentId;
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

	public MeshManager< T > meshManager()
	{
		return this.meshManager;
	}

}
