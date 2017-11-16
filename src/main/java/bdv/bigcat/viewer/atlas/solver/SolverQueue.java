package bdv.bigcat.viewer.atlas.solver;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.atlas.solver.action.Action;
import gnu.trove.map.hash.TLongLongHashMap;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class SolverQueue
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static class Solution
	{}

	private final HashMap< String, ArrayList< Action > > queue = new HashMap<>();

	private final AtomicLong timeOfLastAction = new AtomicLong( 0 );

	private final Thread actionReceiverThread;

	private final Thread solutionHandlerThread;

	private final AtomicBoolean interrupt = new AtomicBoolean( false );

	private final TLongLongHashMap latestSolution;

	public SolverQueue(
			final Supplier< Pair< String, Iterable< Action > > > actionReceiver,
			final Runnable actionReceiptConfirmation,
			final Consumer< Pair< String, Collection< Action > > > solutionRequestToSolver,
			final Supplier< TLongLongHashMap > solutionReceiver,
			final Consumer< TLongLongHashMap > solutionDistributor,
			final Supplier< TLongLongHashMap > initialSolution,
			final Supplier< Void > currentSolutionRequest,
			final Consumer< TLongLongHashMap > currentSolutionResponse,
			final long minWaitTimeAfterLastAction )
	{

		this.latestSolution = initialSolution.get();

		actionReceiverThread = new Thread( () -> {
			while ( !interrupt.get() )
			{
				LOG.debug( "Waiting for action in queue!" );
				final Pair< String, Iterable< Action > > versionedActions = actionReceiver.get();
				final String version = versionedActions.getA();
				final Iterable< Action > actions = versionedActions.getB();
				if ( !this.queue.containsKey( version ) )
					this.queue.put( version, new ArrayList<>() );
				final ArrayList< Action > queue = this.queue.get( version );
				LOG.debug( "Got action in queue! " + actions );
				if ( actions != null )
					synchronized ( queue )
					{
						timeOfLastAction.set( System.currentTimeMillis() );
						actions.forEach( queue::add );
						actionReceiptConfirmation.run();
					}
			}
		} );
		actionReceiverThread.start();

		solutionHandlerThread = new Thread( () -> {
			while ( !interrupt.get() )
			{
				boolean sentRequest = false;
				synchronized ( this.queue )
				{
					final long currentTime = System.currentTimeMillis();
					final long timeDiff = currentTime - timeOfLastAction.get();

					if ( timeDiff >= minWaitTimeAfterLastAction )
						for ( final Entry< String, ArrayList< Action > > entry : this.queue.entrySet() )
						{
							final ArrayList< Action > queue = entry.getValue();
							if ( queue.size() > 0 )
							{
								final String version = entry.getKey();
								solutionRequestToSolver.accept( new ValuePair<>( version, queue ) );
								sentRequest |= true;
								queue.clear();
							}
						}

					if ( sentRequest )
					{
						final TLongLongHashMap solution = solutionReceiver.get();
						synchronized ( latestSolution )
						{
							this.latestSolution.clear();
							this.latestSolution.putAll( solution );
							solutionDistributor.accept( latestSolution );
						}
					}
				}
				if ( !sentRequest )
					try
					{
						Thread.sleep( 10 );
					}
					catch ( final InterruptedException e )
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
			}
		} );
		solutionHandlerThread.start();

		final Thread currentSolutionThread = new Thread( () -> {
			while ( !interrupt.get() )
			{
				final Void empty = currentSolutionRequest.get();
				synchronized ( this.latestSolution )
				{
					currentSolutionResponse.accept( this.latestSolution );
				}
			}
		} );
		currentSolutionThread.start();

	}

	public void interrupt()
	{
		this.interrupt.set( true );
	}

}
