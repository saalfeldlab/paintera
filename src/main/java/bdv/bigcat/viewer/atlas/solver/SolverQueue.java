package bdv.bigcat.viewer.atlas.solver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import bdv.bigcat.viewer.state.FragmentSegmentAssignmentWithHistory.Action;
import gnu.trove.map.hash.TLongLongHashMap;

public class SolverQueue
{

	public static class Solution
	{}

	private final ArrayList< Action > queue = new ArrayList<>();

	private final AtomicLong timeOfLastAction = new AtomicLong( 0 );

	private final Thread actionReceiverThread;

	private final Thread solutionHandlerThread;

	private final AtomicBoolean interrupt = new AtomicBoolean( false );

	private final TLongLongHashMap latestSolution;

	public SolverQueue(
			final Supplier< Action > actionReceiver,
			final Consumer< Collection< Action > > solutionRequestToSolver,
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
				final Action action = actionReceiver.get();
				if ( action != null )
					synchronized ( queue )
					{
						timeOfLastAction.set( System.currentTimeMillis() );
						queue.add( action );
					}
			}
		} );
		actionReceiverThread.start();

		solutionHandlerThread = new Thread( () -> {
			while ( !interrupt.get() )
			{
				final boolean sentRequest;
				synchronized ( queue )
				{
					final long currentTime = System.currentTimeMillis();
					final long timeDiff = currentTime - timeOfLastAction.get();
					if ( timeDiff >= minWaitTimeAfterLastAction )
					{
						sentRequest = true;
						solutionRequestToSolver.accept( queue );
						queue.clear();
					}
					else
						sentRequest = false;

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
//					else
//						Thread.sleep( 10 );
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
