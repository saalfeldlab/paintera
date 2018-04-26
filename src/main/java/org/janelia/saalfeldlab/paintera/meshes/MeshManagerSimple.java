package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import org.janelia.saalfeldlab.paintera.meshes.MeshGenerator.ShapeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableIntegerValue;
import javafx.scene.Group;
import net.imglib2.Interval;
import net.imglib2.util.Pair;

/**
 *
 *
 * @author Philipp Hanslovsky
 */
public class MeshManagerSimple implements MeshManager
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final Function< Long, Interval[] >[] blockListCache;

	private final Function< ShapeKey, Pair< float[], float[] > >[] meshCache;

	private final Map< Long, MeshGenerator > neurons = Collections.synchronizedMap( new HashMap<>() );

	private final Group root;

	private final IntegerProperty meshSimplificationIterations = new SimpleIntegerProperty();

	private final IntegerProperty scaleLevel = new SimpleIntegerProperty();

	private final ExecutorService es;

	public MeshManagerSimple(
			final Function< Long, Interval[] >[] blockListCache,
			final Function< ShapeKey, Pair< float[], float[] > >[] meshCache,
			final Group root,
			final ObservableIntegerValue meshSimplificationIterations,
			final ExecutorService es )
	{
		super();
		this.blockListCache = blockListCache;
		this.meshCache = meshCache;
		this.root = root;
		this.meshSimplificationIterations.set( Math.max( meshSimplificationIterations.get(), 0 ) );
		this.scaleLevel.set( 0 );
		meshSimplificationIterations.addListener( ( obs, oldv, newv ) -> {
			System.out.println( "ADDED MESH SIMPLIFICATION ITERATIONS" );
			this.meshSimplificationIterations.set( Math.max( newv.intValue(), 0 ) );
		} );

		this.es = es;

	}

	@Override
	public void generateMesh( final long id )
	{
		final IntegerProperty color = new SimpleIntegerProperty( 0xffffffff );

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
				es );
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
		return this.scaleLevel;
	}

	@Override
	public IntegerProperty meshSimplificationIterationsProperty()
	{
		return this.meshSimplificationIterations;
	}

	@Override
	public void removeAllMeshes()
	{
		final ArrayList< MeshGenerator > generatorsCopy = new ArrayList<>( unmodifiableMeshMap().values() );
		generatorsCopy.forEach( this::removeMesh );
	}

}
