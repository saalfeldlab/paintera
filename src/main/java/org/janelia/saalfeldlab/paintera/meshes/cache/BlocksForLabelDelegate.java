package org.janelia.saalfeldlab.paintera.meshes.cache;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.MoreExecutors;

import net.imglib2.Interval;

public class BlocksForLabelDelegate< T, U > implements InterruptibleFunction< T, Interval[] >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final InterruptibleFunction< U, Interval[] > delegate;

	private final Function< T, U[] > keyMapping;

	private final ExecutorService workers;

	public BlocksForLabelDelegate(
			final InterruptibleFunction< U, Interval[] > delegate,
			final Function< T, U[] > keyMapping,
			final ExecutorService workers )
	{
		super();
		this.delegate = delegate;
		this.keyMapping = keyMapping;
		this.workers = workers;
	}

	@Override
	public Interval[] apply( final T t )
	{
		final Set< HashWrapper< Interval > > intervals = new HashSet<>();

		final U[] mappedKeys = this.keyMapping.apply( t );
		LOG.debug( "Mapped keys from {} to {}", t, mappedKeys );
		final List< Future< Interval[] > > intervalFutures = new ArrayList<>();
		final ExecutorService workers = MoreExecutors.newDirectExecutorService();
		for ( final U mappedKey : mappedKeys )
		{
			LOG.trace( "Requesting intervals from delegate for key {}", mappedKey );
			intervalFutures.add( workers.submit( () -> delegate.apply( mappedKey ) ) );
		}

		for ( final Future< Interval[] > future : intervalFutures )
		{
			try
			{
				Arrays
						.stream( future.get() )
						.map( HashWrapper::interval )
						.forEach( intervals::add );
			}
			catch ( InterruptedException | ExecutionException e )
			{
				throw new RuntimeException( e );
			}
		}

		LOG.debug( "Got intervals: {}", intervals );

		return intervals.stream().map( HashWrapper::getData ).toArray( Interval[]::new );
	}

	@Override
	public void interruptFor( final T t )
	{
		LOG.warn( "Interrupting for {}", t );
		Arrays.stream( keyMapping.apply( t ) ).forEach( delegate::interruptFor );
	}

	public static < T, U > BlocksForLabelDelegate< T, U >[] delegate(
			final InterruptibleFunction< U, Interval[] >[] delegates,
			final Function< T, U[] > keyMapping )
	{
		return delegate( delegates, keyMapping, MoreExecutors.newDirectExecutorService() );
	}

	@SuppressWarnings( "unchecked" )
	public static < T, U > BlocksForLabelDelegate< T, U >[] delegate(
			final InterruptibleFunction< U, Interval[] >[] delegates,
			final Function< T, U[] > keyMapping,
			final ExecutorService workers )
	{
		return Arrays
				.stream( delegates )
				.map( d -> new BlocksForLabelDelegate<>( d, keyMapping, workers ) )
				.toArray( BlocksForLabelDelegate[]::new );
	}

}
