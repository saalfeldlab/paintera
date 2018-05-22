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

import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
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
public class MeshManagerWithAssignmentForSegments implements MeshManager< TLongHashSet >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final DataSource< ?, ? > source;

	private final InterruptibleFunction< Long, Interval[] >[] blockListCache;

	private final InterruptibleFunction< ShapeKey< TLongHashSet >, Pair< float[], float[] > >[] meshCache;

	private final FragmentSegmentAssignmentState assignment;

	private final AbstractHighlightingARGBStream stream;

	private final Map< Long, MeshGenerator< TLongHashSet > > neurons = Collections.synchronizedMap( new HashMap<>() );

	private final Group root;

	private final SelectedSegments selectedSegments;

	private final IntegerProperty meshSimplificationIterations = new SimpleIntegerProperty();

	private final DoubleProperty smoothingLambda = new SimpleDoubleProperty();

	private final IntegerProperty smoothingIterations = new SimpleIntegerProperty();

	private final IntegerProperty scaleLevel = new SimpleIntegerProperty();

	private final ExecutorService managers;

	private final ExecutorService workers;

	public MeshManagerWithAssignmentForSegments(
			final DataSource< ?, ? > source,
			final InterruptibleFunction< Long, Interval[] >[] blockListCache,
			final InterruptibleFunction< ShapeKey< TLongHashSet >, Pair< float[], float[] > >[] meshCache,
			final Group root,
			final FragmentSegmentAssignmentState assignment,
			final SelectedSegments selectedSegments,
			final AbstractHighlightingARGBStream stream,
			final ObservableIntegerValue meshSimplificationIterations,
			final ObservableDoubleValue smoothingLambda,
			final ObservableIntegerValue smooothingIterations,
			final ExecutorService managers,
			final ExecutorService workers )
	{
		super();
		this.source = source;
		this.blockListCache = blockListCache;
		this.meshCache = meshCache;
		this.root = root;
		this.assignment = assignment;
		this.selectedSegments = selectedSegments;
		this.stream = stream;

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

		this.assignment.addListener( obs -> this.update() );
		this.selectedSegments.addListener( obs -> this.update() );

	}

	private void update()
	{
		synchronized ( neurons )
		{
			final long[] selectedSegments = this.selectedSegments.getSelectedSegments();
			final TLongHashSet selectedSegmentsSet = new TLongHashSet( selectedSegments );
			final TLongHashSet currentlyShowing = new TLongHashSet();
			final List< Entry< Long, MeshGenerator< TLongHashSet > > > toBeRemoved = new ArrayList<>();
			neurons.keySet().forEach( currentlyShowing::add );
			for ( final Entry< Long, MeshGenerator< TLongHashSet > > neuron : neurons.entrySet() )
			{
				final TLongHashSet fragmentsInSegment = assignment.getFragments( neuron.getKey() );
				final boolean isSelected = selectedSegmentsSet.contains( neuron.getKey() );
				final boolean isConsistent = neuron.getValue().getId().equals( fragmentsInSegment );
				LOG.warn( "Segment {} is selected? {}  Is consistent? {}", neuron.getKey(), isSelected, isConsistent );
				if ( !isSelected || !isConsistent )
				{
					currentlyShowing.remove( neuron.getKey() );
					toBeRemoved.add( neuron );
				}

			}
			toBeRemoved.stream().map( e -> e.getValue() ).forEach( this::removeMesh );
			LOG.warn( "Currently showing {} ", currentlyShowing );
			LOG.warn( "Selection {}", selectedSegments );
			LOG.warn( "To be removed {}", toBeRemoved );
			Arrays.stream( selectedSegments ).filter( id -> !currentlyShowing.contains( id ) ).forEach( segment -> generateMesh( source, segment ) );
		}
	}

	private void generateMesh( final DataSource< ?, ? > source, final long id )
	{
		final IntegerProperty color = new SimpleIntegerProperty( stream.argb( id ) );
		stream.addListener( obs -> color.set( stream.argb( id ) ) );
		assignment.addListener( obs -> color.set( stream.argb( id ) ) );
		final TLongHashSet fragments = assignment.getFragments( id );


		for ( final Entry< Long, MeshGenerator< TLongHashSet > > neuron : neurons.entrySet() )
		{
			if ( neuron.getKey() == id && neuron.getValue().getId().equals( fragments ) ) {
				return;
			}
		}

		LOG.debug( "Adding mesh for segment {}.", id );
		final MeshGenerator< TLongHashSet > nfx = new MeshGenerator<>(
				fragments,
				blockListCache,
				meshCache,
				color,
				scaleLevel.get(),
				meshSimplificationIterations.get(),
				smoothingLambda.get(),
				smoothingIterations.get(),
				managers,
				workers,
				val -> val.toArray() );
		nfx.rootProperty().set( this.root );

		neurons.put( id, nfx );

	}

	@Override
	public void removeMesh( final long id )
	{
		Optional.ofNullable( unmodifiableMeshMap().get( id ) ).ifPresent( this::removeMesh );
	}

	private void removeMesh( final MeshGenerator< TLongHashSet > mesh )
	{
		mesh.rootProperty().set( null );
		final List< Long > toRemove = this.neurons
				.entrySet()
				.stream()
				.filter( e -> e.getValue().getId().equals( mesh.getId() ) )
				.map( Entry::getKey )
				.collect( Collectors.toList() );
		toRemove.forEach( this.neurons::remove );
	}

	@Override
	public Map< Long, MeshGenerator< TLongHashSet > > unmodifiableMeshMap()
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
		final ArrayList< MeshGenerator< TLongHashSet > > generatorsCopy = new ArrayList<>( unmodifiableMeshMap().values() );
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

	@Override
	public InterruptibleFunction< Long, Interval[] >[] blockListCache()
	{
		return this.blockListCache;
	}

	@Override
	public InterruptibleFunction< ShapeKey< TLongHashSet >, Pair< float[], float[] > >[] meshCache()
	{
		return this.meshCache;
	}

}
