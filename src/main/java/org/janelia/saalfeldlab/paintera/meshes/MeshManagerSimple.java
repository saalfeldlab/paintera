package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import org.janelia.saalfeldlab.util.Colors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.beans.value.ObservableIntegerValue;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import net.imglib2.Interval;
import net.imglib2.util.Pair;

/**
 *
 *
 * @author Philipp Hanslovsky
 */
public class MeshManagerSimple< T > implements MeshManager< T >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final InterruptibleFunction< T, Interval[] >[] blockListCache;

	private final InterruptibleFunction< ShapeKey< T >, Pair< float[], float[] > >[] meshCache;

	private final Map< T, MeshGenerator< T > > neurons = Collections.synchronizedMap( new HashMap<>() );

	private final Group root;

	private final IntegerProperty meshSimplificationIterations = new SimpleIntegerProperty();

	private final DoubleProperty smoothingLambda = new SimpleDoubleProperty();

	private final IntegerProperty smoothingIterations = new SimpleIntegerProperty();

	private final IntegerProperty scaleLevel = new SimpleIntegerProperty();

	private final ExecutorService managers;

	private final ExecutorService workers;

	private final ObjectProperty< Color > color = new SimpleObjectProperty<>( Color.WHITE );

	private final DoubleProperty opacity = new SimpleDoubleProperty( 1.0 );

	private final Function< T, long[] > getIds;

	public MeshManagerSimple(
			final InterruptibleFunction< T, Interval[] >[] blockListCache,
			final InterruptibleFunction< ShapeKey< T >, Pair< float[], float[] > >[] meshCache,
			final Group root,
			final ObservableIntegerValue meshSimplificationIterations,
			final ObservableDoubleValue smoothingLambda,
			final ObservableIntegerValue smoothingIterations,
			final ExecutorService managers,
			final ExecutorService workers,
			final Function< T, long[] > getIds )
	{
		super();
		this.blockListCache = blockListCache;
		this.meshCache = meshCache;
		this.root = root;
		this.getIds = getIds;

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
	public void generateMesh( final T id )
	{
		final IntegerBinding color = Bindings.createIntegerBinding( () -> Colors.toARGBType( this.color.get() ).get(), this.color );

		for ( final T neuron : neurons.keySet() )
		{
			if ( neuron.equals( id ) ) {
				return;
			}
		}

		LOG.debug( "Adding mesh for segment {} (composed of ids={}).", id, getIds.apply( id ) );
		final MeshGenerator< T > nfx = new MeshGenerator<>(
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
		nfx.opacityProperty().set( this.opacity.get() );
		nfx.rootProperty().set( this.root );
		nfx.scaleIndexProperty().bind( this.scaleLevel );

		neurons.put( id, nfx );

	}

	@Override
	public void removeMesh( final T id )
	{
		if ( this.unmodifiableMeshMap().get( id ) != null )
		{
			this.removeMesh( id );
		}
	}

	private void removeMesh( final MeshGenerator< T > mesh )
	{
		mesh.rootProperty().set( null );
		this.neurons.remove( mesh.getId() );
	}

	@Override
	public Map< T, MeshGenerator< T > > unmodifiableMeshMap()
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
		final ArrayList< MeshGenerator< T > > generatorsCopy = new ArrayList<>( unmodifiableMeshMap().values() );
		generatorsCopy.forEach( this::removeMesh );
	}

	@Override
	public InterruptibleFunction< T, Interval[] >[] blockListCache()
	{
		return blockListCache;
	}

	@Override
	public InterruptibleFunction< ShapeKey< T >, Pair< float[], float[] > >[] meshCache()
	{
		return meshCache;
	}

	public ObjectProperty< Color > colorProperty()
	{
		return this.color;
	}

	@Override
	public DoubleProperty opacityProperty()
	{
		return this.opacity;
	}

	@Override
	public long[] containedFragments( final T id )
	{
		return getIds.apply( id );
	}

}
