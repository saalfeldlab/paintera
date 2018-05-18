package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class MeshManagerSimple implements MeshManager< Long >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final InterruptibleFunction< Long, Interval[] >[] blockListCache;

	private final InterruptibleFunction< ShapeKey< Long >, Pair< float[], float[] > >[] meshCache;

	private final Map< Long, MeshGenerator< Long > > neurons = Collections.synchronizedMap( new HashMap<>() );

	private final Group root;

	private final IntegerProperty meshSimplificationIterations = new SimpleIntegerProperty();

	private final DoubleProperty smoothingLambda = new SimpleDoubleProperty();

	private final IntegerProperty smoothingIterations = new SimpleIntegerProperty();

	private final IntegerProperty scaleLevel = new SimpleIntegerProperty();

	private final ExecutorService managers;

	private final ExecutorService workers;

	public MeshManagerSimple(
			final InterruptibleFunction< Long, Interval[] >[] blockListCache,
			final InterruptibleFunction< ShapeKey< Long >, Pair< float[], float[] > >[] meshCache,
			final Group root,
			final ObservableIntegerValue meshSimplificationIterations,
			final ObservableDoubleValue smoothingLambda,
			final ObservableIntegerValue smoothingIterations,
			final ExecutorService managers,
			final ExecutorService workers )
	{
		super();
		this.blockListCache = blockListCache;
		this.meshCache = meshCache;
		this.root = root;

		this.meshSimplificationIterations.set( Math.max( meshSimplificationIterations.get(), 0 ) );
		meshSimplificationIterations.addListener( ( obs, oldv, newv ) -> {
			System.out.println( "ADDED MESH SIMPLIFICATION ITERATIONS" );
			this.meshSimplificationIterations.set( Math.max( newv.intValue(), 0 ) );
		} );

		this.smoothingLambda.set( Math.min( Math.max( smoothingLambda.get(), 0 ), 1.0 ) );
		smoothingLambda.addListener( ( obs, oldv, newv ) -> {
			System.out.println( "ADDED SMOOTHING LAMBDA" );
			this.smoothingLambda.set( Math.min( Math.max( newv.doubleValue(), 0 ), 1.0 ) );
		} );

		this.smoothingIterations.set( Math.max( smoothingIterations.get(), 0 ) );
		smoothingIterations.addListener( ( obs, oldv, newv ) -> {
			System.out.println( "ADDED SMOOTHING ITERATIONS" );
			this.smoothingIterations.set( Math.max( newv.intValue(), 0 ) );
		} );

		this.scaleLevel.set( 0 );

		this.managers = managers;
		this.workers = workers;
	}

	@Override
	public void generateMesh( final long id )
	{
		final IntegerProperty color = new SimpleIntegerProperty( 0xffffffff );

		for ( final MeshGenerator< Long > neuron : neurons.values() )
		{
			if ( neuron.getId() == id ) {
				return;
			}
		}

		LOG.debug( "Adding mesh for segment {}.", id );
		final MeshGenerator< Long > nfx = new MeshGenerator<>(
				id,
				blockListCache,
				meshCache,
				color,
				scaleLevel.get(),
				meshSimplificationIterations.get(),
				smoothingLambda.get(),
				smoothingIterations.get(),
				managers,
				workers,
				val -> new long[] { val } );
		nfx.rootProperty().set( this.root );

		neurons.put( id, nfx );

	}

	@Override
	public void removeMesh( final long id )
	{
		Optional.ofNullable( unmodifiableMeshMap().get( id ) ).ifPresent( this::removeMesh );
	}

	private void removeMesh( final MeshGenerator< Long > mesh )
	{
		mesh.rootProperty().set( null );
		this.neurons.remove( mesh.getId() );
	}

	@Override
	public Map< Long, MeshGenerator< Long > > unmodifiableMeshMap()
	{
		return Collections.unmodifiableMap( neurons );
	}

	@Override
	public IntegerProperty scaleLevelProperty()
	{
		return this.scaleLevel;
	}

	@Override
	public IntegerProperty meshSimplificationIterationsProperty()
	{
		return this.meshSimplificationIterations;
	}

	@Override
	public DoubleProperty smoothingLambdaProperty()
	{
		return this.smoothingLambda;
	}

	@Override
	public IntegerProperty smoothingIterationsProperty()
	{
		return this.smoothingIterations;
	}

	@Override
	public void removeAllMeshes()
	{
		final ArrayList< MeshGenerator > generatorsCopy = new ArrayList<>( unmodifiableMeshMap().values() );
		generatorsCopy.forEach( this::removeMesh );
	}

	@Override
	public InterruptibleFunction< Long, Interval[] >[] blockListCache()
	{
		return blockListCache;
	}

	@Override
	public InterruptibleFunction< ShapeKey< Long >, Pair< float[], float[] > >[] meshCache()
	{
		return meshCache;
	}

}
