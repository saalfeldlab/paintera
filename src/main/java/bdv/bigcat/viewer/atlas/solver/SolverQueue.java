package bdv.bigcat.viewer.atlas.solver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import bdv.bigcat.viewer.atlas.solver.action.Action;
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
			final Supplier< Iterable< Action > > actionReceiver,
			final Runnable actionReceiptConfirmation,
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
				System.out.println( "Waiting for action in queue!" );
				final Iterable< Action > actions = actionReceiver.get();
				System.out.println( "Got action in queue! " + actions );
//				System.out.println( "Sent confirmation" );
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
				final boolean sentRequest;
				synchronized ( queue )
				{
					final long currentTime = System.currentTimeMillis();
					final long timeDiff = currentTime - timeOfLastAction.get();
					if ( timeDiff >= minWaitTimeAfterLastAction && queue.size() > 0 )
					{
						sentRequest = true;
						solutionRequestToSolver.accept( queue );
						queue.clear();
					}
					else
						sentRequest = false;

//					System.out.println( "DID SEND REQUEST? " + sentRequest );

					if ( sentRequest )
					{
//						System.out.println( "WAITING FOR SOLUTION!" );
						final TLongLongHashMap solution = solutionReceiver.get();
//						System.out.println( "GOT RESOLUTION! " + solution );
						synchronized ( latestSolution )
						{
							this.latestSolution.clear();
							this.latestSolution.putAll( solution );
//							System.out.println( "DISTRIBUTING SOLUTION!" );
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
//					System.out.println( "SENDING LATEST SOLUTION!" );
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
