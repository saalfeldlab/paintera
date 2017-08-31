package bdv.bigcat.viewer.atlas.solver;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.zeromq.ZMQ;

import bdv.bigcat.viewer.atlas.solver.action.Action;
import gnu.trove.map.hash.TLongLongHashMap;

public class SolverQueueServerZMQ implements Closeable
{

	private final ZMQ.Context ctx;

	private final ZMQ.Socket actionReceiverSocket;

	private final ZMQ.Socket solutionRequestResponseSocket;

	private final ZMQ.Socket solutionDistributionSocket;

	private final ZMQ.Socket latestSolutionRequestSocket;

	private final SolverQueue queue;

	public SolverQueueServerZMQ(
			final String actionReceiverAddress,
			final String solutionRequestResponseAddress,
			final String solutionDistributionAddress,
			final Supplier< TLongLongHashMap > initialSolution,
			final String latestSolutionRequestAddress,
			final int ioThreads,
			final long minWaitTimeAfterLastAction )
	{
		super();
		this.ctx = ZMQ.context( ioThreads );

		this.actionReceiverSocket = ctx.socket( ZMQ.REP );
		this.actionReceiverSocket.bind( actionReceiverAddress );
		final Supplier< Iterable< Action > > actionReceiver = () -> {
//			System.out.println( "ACT " + 1 );
//			final byte[] message = this.actionReceiverSocket.recv();
			System.out.println( "WAITING FOR MESSAGE IN ACTION RECEIVER at address! " + actionReceiverAddress );
			final String message = this.actionReceiverSocket.recvStr( 0, Charset.defaultCharset() );
			System.out.println( "RECEIVED THE FOLLOWING MESSAGE: " + message );

			if ( message == null )
				return new ArrayList<>();

			final List< Action > actions = Action.fromJson( message );

			return actions;

		};

		final Runnable actionReceiptConfirmation = () -> this.actionReceiverSocket.send( new byte[] { ( byte ) 0 }, 0 );

		this.solutionRequestResponseSocket = ctx.socket( ZMQ.REQ );
		this.solutionRequestResponseSocket.connect( solutionRequestResponseAddress );

		final Consumer< Collection< Action > > solutionRequester = actions -> this.solutionRequestResponseSocket.send( Action.toJson( actions ).toString() );

		final Supplier< TLongLongHashMap > solutionReceiver = () -> {
			final byte[] data = this.solutionRequestResponseSocket.recv();
			final int numEntries = data.length / ( Long.BYTES * 2 );
			final long[] keys = new long[ numEntries ];
			final long[] values = new long[ numEntries ];
			final ByteBuffer bb = ByteBuffer.wrap( data );
			for ( int i = 0; i < numEntries; ++i )
			{
				keys[ i ] = bb.getLong();
				values[ i ] = bb.getLong();
			}
			return new TLongLongHashMap( keys, values );
		};

		this.solutionDistributionSocket = ctx.socket( ZMQ.PUB );
		this.solutionDistributionSocket.bind( solutionDistributionAddress );
//		System.out.println( "ADDR! " + solutionDistributionAddress );

		final Consumer< TLongLongHashMap > solutionDistributor = solution -> {
			final long[] keys = solution.keys();
			final long[] values = solution.values();
			final byte[] data = new byte[ Long.BYTES * 2 * keys.length ];
			final ByteBuffer bb = ByteBuffer.wrap( data );
			for ( int i = 0; i < keys.length; ++i )
			{
				bb.putLong( keys[ i ] );
				bb.putLong( values[ i ] );
			}
//			System.out.println( "PUBLISHING SOLUTION " + solution + " " + Arrays.toString( data ) );
			this.solutionDistributionSocket.send( data, 0 );
		};

		this.latestSolutionRequestSocket = ctx.socket( ZMQ.REP );
		this.latestSolutionRequestSocket.bind( latestSolutionRequestAddress );

		final Supplier< Void > currentSolutionRequest = () -> {
//			System.out.println( "Received solution request! 0" );
			this.latestSolutionRequestSocket.recv( 0 );
//			System.out.println( "Received solution request! 1" );
			return null;
		};

		final Consumer< TLongLongHashMap > currentSolutionResponse = solution -> {
			final long[] keys = solution.keys();
			final long[] values = solution.values();
			final byte[] data = new byte[ Long.BYTES * 2 * keys.length ];
			final ByteBuffer bb = ByteBuffer.wrap( data );
			for ( int i = 0; i < keys.length; ++i )
			{
				bb.putLong( keys[ i ] );
				bb.putLong( values[ i ] );
			}
//			System.out.println( "Sending solution response! " + Arrays.toString( data ) );
			this.latestSolutionRequestSocket.send( data, 0 );
		};

		this.queue = new SolverQueue(
				actionReceiver,
				actionReceiptConfirmation,
				solutionRequester,
				solutionReceiver,
				solutionDistributor,
				initialSolution,
				currentSolutionRequest,
				currentSolutionResponse,
				minWaitTimeAfterLastAction );
	}

	@Override
	public void close() throws IOException
	{
		queue.interrupt();
		actionReceiverSocket.close();
		solutionRequestResponseSocket.close();
		solutionDistributionSocket.close();
		ctx.close();

	}

}
