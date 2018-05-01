package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.paintera.SourceState;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentsInSelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.meshes.MeshGenerator.ShapeKey;
import org.janelia.saalfeldlab.paintera.stream.ARGBStream;
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.beans.value.ObservableIntegerValue;
import javafx.scene.Group;
import net.imglib2.Interval;
import net.imglib2.util.Pair;

/**
 *
 *
 * @author Philipp Hanslovsky
 */
public class MeshManagerWithAssignment implements MeshManager
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final DataSource< ?, ? > source;

	private final SourceState< ?, ? > state;

	private final Map< Long, MeshGenerator > neurons = Collections.synchronizedMap( new HashMap<>() );

	private final Group root;

	private final FragmentsInSelectedSegments fragmentsInSelectedSegments;

	private final IntegerProperty meshSimplificationIterations = new SimpleIntegerProperty();

	private final DoubleProperty smoothingLambda = new SimpleDoubleProperty();

	private final IntegerProperty smoothingIterations = new SimpleIntegerProperty();

	private final IntegerProperty scaleLevel = new SimpleIntegerProperty();

	private final ExecutorService managers;

	private final ExecutorService workers;

	public MeshManagerWithAssignment(
			final DataSource< ?, ? > source,
			final SourceState< ?, ? > state,
			final Group root,
			final FragmentsInSelectedSegments fragmentsInSelectedSegments,
			final ObservableIntegerValue meshSimplificationIterations,
			final ObservableDoubleValue smoothingLambda,
			final ObservableIntegerValue smooothingIterations,
			final ExecutorService managers,
			final ExecutorService workers )
	{
		super();
		this.source = source;
		this.state = state;
		this.root = root;
		this.fragmentsInSelectedSegments = fragmentsInSelectedSegments;

		this.meshSimplificationIterations.set( Math.max( meshSimplificationIterations.get(), 0 ) );
		meshSimplificationIterations.addListener( ( obs, oldv, newv ) -> {
			LOG.debug( "Added mesh simplification iterations" );
			this.meshSimplificationIterations.set( Math.max( newv.intValue(), 0 ) );
		} );

		this.smoothingLambda.set( Math.min( Math.max( smoothingLambda.get(), 0 ), 1.0 ) );
		smoothingLambda.addListener( ( obs, oldv, newv ) -> {
			LOG.debug( "Added smoothing lambda" );
			this.smoothingLambda.set( Math.min( Math.max( newv.doubleValue(), 0 ), 1.0 ) );
		} );

		this.smoothingIterations.set( Math.max( smoothingIterations.get(), 0 ) );
		smoothingIterations.addListener( ( obs, oldv, newv ) -> {
			LOG.debug( "Added smoothing iterations" );
			this.smoothingIterations.set( Math.max( newv.intValue(), 0 ) );
		} );

		this.scaleLevel.set( this.source.getNumMipmapLevels() - 1 );
		this.smoothingLambda.set( Smooth.DEFAULT_LAMBDA );
		this.smoothingIterations.set( Smooth.DEFAULT_ITERATIONS );

		this.managers = managers;
		this.workers = workers;

		this.fragmentsInSelectedSegments.addListener( obs -> this.update() );

	}

	private void update()
	{
		synchronized ( neurons )
		{
			final TLongHashSet fragmentsInSelectedSegments = new TLongHashSet( this.fragmentsInSelectedSegments.getFragments() );
			final TLongHashSet currentlyShowing = new TLongHashSet();
			neurons.values().stream().mapToLong( MeshGenerator::getId ).forEach( currentlyShowing::add );
			final List< Entry< Long, MeshGenerator > > toBeRemoved = neurons.entrySet().stream().filter( n -> !fragmentsInSelectedSegments.contains( n.getValue().getId() ) ).collect( Collectors.toList() );
			toBeRemoved.stream().map( e -> e.getValue() ).forEach( this::removeMesh );
			Arrays.stream( fragmentsInSelectedSegments.toArray() ).filter( id -> !currentlyShowing.contains( id ) ).forEach( segment -> generateMesh( source, segment ) );
		}
	}

	private void generateMesh( final DataSource< ?, ? > source, final long id )
	{
		final FragmentSegmentAssignmentState assignment = state.assignmentProperty().get();
		if ( assignment == null )
			return;

		final ARGBStream streams = state.streamProperty().get();

		if ( streams == null || !( streams instanceof AbstractHighlightingARGBStream ) )
			return;

		final AbstractHighlightingARGBStream stream = ( AbstractHighlightingARGBStream ) streams;
		final IntegerProperty color = new SimpleIntegerProperty( stream.argb( id ) );
		stream.addListener( obs -> color.set( stream.argb( id ) ) );
		assignment.addListener( obs -> color.set( stream.argb( id ) ) );

		final InterruptibleFunction< Long, Interval[] >[] blockListCache = state.blocklistCacheProperty().get();
		final InterruptibleFunction< ShapeKey, Pair< float[], float[] > >[] meshCache = state.meshesCacheProperty().get();
		if ( meshCache == null || blockListCache == null )
			return;

		for ( final MeshGenerator neuron : neurons.values() )
			if ( neuron.getId() == id )
				return;

		LOG.debug( "Adding mesh for segment {}.", id );
		final MeshGenerator nfx = new MeshGenerator(
				id,
				blockListCache,
				meshCache,
				color,
				scaleLevel.get(),
				meshSimplificationIterations.get(),
				smoothingLambda.get(),
				smoothingIterations.get(),
				managers,
				workers );
		nfx.rootProperty().set( this.root );

		neurons.put( id, nfx );

	}

	@Override
	public void removeMesh( final long id )
	{
		Optional.ofNullable( unmodifiableMeshMap().get( id ) ).ifPresent( this::removeMesh );
	}

	private void removeMesh( final MeshGenerator mesh )
	{
		mesh.rootProperty().set( null );
		this.neurons.remove( mesh.getId() );
	}

	@Override
	public Map< Long, MeshGenerator > unmodifiableMeshMap()
	{
		return Collections.unmodifiableMap( neurons );
	}

	@Override
	public IntegerProperty scaleLevelProperty()
	{
		return scaleLevel;
	}

	@Override
	public IntegerProperty meshSimplificationIterationsProperty()
	{
		return meshSimplificationIterations;
	}

	@Override
	public void generateMesh( final long id )
	{
		generateMesh( this.source, id );
	}

	@Override
	public void removeAllMeshes()
	{
		final ArrayList< MeshGenerator > generatorsCopy = new ArrayList<>( unmodifiableMeshMap().values() );
		generatorsCopy.forEach( this::removeMesh );
	}

	@Override
	public DoubleProperty smoothingLambdaProperty()
	{

		return smoothingLambda;
	}

	@Override
	public IntegerProperty smoothingIterationsProperty()
	{

		return smoothingIterations;
	}

}
