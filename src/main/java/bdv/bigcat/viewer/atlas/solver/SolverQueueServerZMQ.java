package bdv.bigcat.viewer.atlas.solver;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.zeromq.ZMQ;

import bdv.bigcat.viewer.state.FragmentSegmentAssignmentWithHistory.Action;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentWithHistory.Action.TYPE;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentWithHistory.Detach;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentWithHistory.Merge;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentWithHistory.NoAction;
import gnu.trove.map.hash.TLongLongHashMap;

public class SolverQueueServerZMQ implements Closeable
{

	private final ZMQ.Context ctx;

	private final ZMQ.Socket actionReceiverSocket;

	private final ZMQ.Socket solutionRequestResponseSocket;

	private final ZMQ.Socket solutionDistributionSocket;

	private final ZMQ.Socket latestSolutionRequestAddress;

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

		final TYPE[] actionTypes = Action.TYPE.values();

		this.actionReceiverSocket = ctx.socket( ZMQ.REP );
		this.actionReceiverSocket.connect( actionReceiverAddress );
		final Supplier< Action > actionReceiver = () -> {
			final byte[] message = this.actionReceiverSocket.recv();

			this.actionReceiverSocket.send( new byte[] { ( byte ) 0 } );

			if ( message == null )
				return new NoAction();

			final ByteBuffer bb = ByteBuffer.wrap( message );

			final int actionTypeIndex = bb.getInt();

			if ( actionTypeIndex < 0 || actionTypeIndex >= actionTypes.length )
				return new NoAction();

			final TYPE actionType = actionTypes[ actionTypeIndex ];

			switch ( actionType )
			{

			case NO_ACTION:
				return new NoAction();
			case MERGE:
				return new Merge( bb.getLong(), bb.getLong() );
			case DETACH:
				return new Detach( bb.getLong() );
			default:
				return new NoAction();
			}

		};

		this.solutionRequestResponseSocket = ctx.socket( ZMQ.REQ );
		this.solutionRequestResponseSocket.connect( solutionRequestResponseAddress );

		final Consumer< Collection< Action > > solutionRequester = actions -> {
			final int requiredSizeInBytes = actions.stream().filter( a -> !( a instanceof NoAction ) ).mapToInt( a -> Integer.BYTES + Long.BYTES * ( a instanceof Merge ? 2 : 1 ) ).sum();
			final byte[] data = new byte[ requiredSizeInBytes ];
			final ByteBuffer bb = ByteBuffer.wrap( data );
			actions.stream().filter( a -> !( a instanceof NoAction ) ).forEach( action -> {
				if ( action instanceof Merge )
				{
					final Merge merge = ( Merge ) action;
					bb.putInt( Action.TYPE.MERGE.ordinal() );
					bb.putLong( merge.ids()[ 0 ] );
					bb.putLong( merge.ids()[ 1 ] );
				}
				else if ( action instanceof Detach )
				{
					final Detach detach = ( Detach ) action;
					bb.putInt( Action.TYPE.DETACH.ordinal() );
					bb.putLong( detach.ids()[ 0 ] );
				}
			} );
		};

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
			this.solutionDistributionSocket.send( data );
		};

		this.latestSolutionRequestAddress = ctx.socket( ZMQ.REP );
		this.latestSolutionRequestAddress.bind( latestSolutionRequestAddress );

		final Supplier< Void > currentSolutionRequest = () -> {
			this.latestSolutionRequestAddress.recv();
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
			this.solutionDistributionSocket.send( data );
		};

		this.queue = new SolverQueue(
				actionReceiver,
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
