package bdv.bigcat.viewer.atlas.solver;

import java.io.Closeable;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import bdv.bigcat.viewer.atlas.solver.action.Action;
import bdv.bigcat.viewer.atlas.solver.action.ConfirmGroupings;
import gnu.trove.map.hash.TLongLongHashMap;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class SolverQueueServerZMQ implements Closeable
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

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
		final Supplier< Pair< String, Iterable< Action > > > actionReceiver = () -> {
			LOG.debug( "WAITING FOR MESSAGE IN ACTION RECEIVER at address! {}", actionReceiverAddress );
			final String message = this.actionReceiverSocket.recvStr( 0, Charset.defaultCharset() );
			LOG.debug( "RECEIVED THE FOLLOWING MESSAGE: {}", message );

			if ( message == null )
				return new ValuePair<>( "", new ArrayList<>() );

			try
			{

				final JsonObject json = new JsonParser().parse( message ).getAsJsonObject();
				if ( !json.has( "version" ) )
					return new ValuePair<>( "", new ArrayList<>() );
				final String version = json.get( "version" ).getAsString();

				final List< Action > actions = Action.fromJson( json.get( "actions" ).toString() );
				LOG.debug( "RETURNING THESE ACTIONS: {}", actions );
				if ( actions.get( 0 ) instanceof ConfirmGroupings )
					LOG.debug( Arrays.toString( ( ( ConfirmGroupings ) actions.get( 0 ) ).fragmentsBySegment() ) );
				return new ValuePair<>( version, actions );
			}
			catch ( final JsonParseException e )
			{
				return new ValuePair<>( "", new ArrayList<>() );
			}

		};

		final Runnable actionReceiptConfirmation = () -> this.actionReceiverSocket.send( new byte[] { ( byte ) 0 }, 0 );

		this.solutionRequestResponseSocket = ctx.socket( ZMQ.REQ );
		this.solutionRequestResponseSocket.connect( solutionRequestResponseAddress );

		final Consumer< Pair< String, Collection< Action > > > solutionRequester = versionedActions -> {
			final String version = versionedActions.getA();
			final JsonObject json = new JsonObject();
			json.addProperty( "version", version );
			json.add( "actions", Action.toJson( versionedActions.getB() ) );
			this.solutionRequestResponseSocket.send( json.toString() );
		};

		final Supplier< TLongLongHashMap > solutionReceiver = () -> {
			final byte[] data = this.solutionRequestResponseSocket.recv();
			final int numEntries = data.length / Long.BYTES;
			final long[] keys = new long[ numEntries ];
			final long[] values = new long[ numEntries ];
			final ByteBuffer bb = ByteBuffer.wrap( data );
			for ( int i = 0; i < numEntries; ++i )
			{
				keys[ i ] = i;
				values[ i ] = bb.getLong();
			}
			return new TLongLongHashMap( keys, values );
//			final byte[] data = this.solutionRequestResponseSocket.recv();
//			final int numEntries = data.length / ( Long.BYTES * 2 );
//			final long[] keys = new long[ numEntries ];
//			final long[] values = new long[ numEntries ];
//			final ByteBuffer bb = ByteBuffer.wrap( data );
//			for ( int i = 0; i < numEntries; ++i )
//			{
//				keys[ i ] = bb.getLong();
//				values[ i ] = bb.getLong();
//			}
//			return new TLongLongHashMap( keys, values );
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
			this.solutionDistributionSocket.send( data, 0 );
		};

		this.latestSolutionRequestSocket = ctx.socket( ZMQ.REP );
		this.latestSolutionRequestSocket.bind( latestSolutionRequestAddress );

		final Supplier< Void > currentSolutionRequest = () -> {
			this.latestSolutionRequestSocket.recv( 0 );
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
