package bdv.bigcat.viewer.atlas.solver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import bdv.bigcat.viewer.atlas.solver.action.Action;
import bdv.bigcat.viewer.atlas.solver.action.Detach;
import bdv.bigcat.viewer.atlas.solver.action.Merge;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TLongLongHashMap;

public class SolverQueueServerZMQTest
{

	private static final String ACTION_ADDRESS = "ipc://ACTION";

	private static final String SOLUTION_REQ_REP = "ipc://SOL_REQ_REP";

	private static final String SOLUTION_DIST = "ipc://SOL_DIST";

	private static final String LATEST_SOL_ADDR = "ipc://LATEST_SOL";

	private static final int MIN_WAIT_AFTER_LAST_ACTION = 100;

	private static final Supplier< TLongLongHashMap > INITIAL_SOLUTION = () -> new TLongLongHashMap( new long[] { 4 }, new long[] { 2 } );

	@Test
	public void test() throws InterruptedException, IOException
	{
		final TLongLongHashMap solution = INITIAL_SOLUTION.get();

		final Thread solutionHandlerThread = new Thread( () -> {
			final List< Action > actions = new ArrayList<>();

			final Context ctx = ZMQ.context( 1 );
			final Socket socket = ctx.socket( ZMQ.REP );
			socket.bind( SOLUTION_REQ_REP );

			while ( !Thread.interrupted() )
			{

				final String msg = socket.recvStr();
//				System.out.println( "RECEIVED MSG " + msg );
				final JsonObject jsonObject = new JsonParser().parse( msg ).getAsJsonObject();
				final List< Action > incomingActions = Action.fromJson( jsonObject.get( "actions" ).toString() );
//				System.out.println( "Got actions! " + incomingActions );

				actions.addAll( incomingActions );

				for ( final Action action : incomingActions )
					if ( action instanceof Merge )
					{
						final Merge merge = ( Merge ) action;
						final long[] ids = merge.ids();
						for ( int i = 0; i < ids.length; ++i )
							for ( int m = i + 1; m < ids.length; ++m )
							{
								final long f1 = ids[ i ];
								final long f2 = ids[ m ];

								if ( !solution.contains( f1 ) )
									solution.put( f1, f1 );

								if ( !solution.contains( f2 ) )
									solution.put( f2, f2 );

								final long s1 = solution.get( f1 );
								final long s2 = solution.get( f2 );
								final long s = Math.min( s1, s2 );

								final TLongArrayList fs = new TLongArrayList();
								solution.forEachEntry( ( k, v ) -> {
									if ( v == s1 || v == s2 )
										fs.add( k );
									return true;
								} );
								fs.forEach( id -> {
									solution.put( id, s );
									return true;
								} );
							}

					}
					else if ( action instanceof Detach )
					{
						final Detach detach = ( Detach ) action;
						final long id = detach.id();
						if ( solution.contains( id ) )
							solution.put( id, id );
					}

				final long maxLabel = Arrays.stream( solution.keys() ).reduce( Long::max ).getAsLong();
				final long[] mapping = new long[ ( int ) ( maxLabel + 1 ) ];
				final byte[] response = new byte[ mapping.length * Long.BYTES ];
				Arrays.fill( mapping, -1 );
				final ByteBuffer responseBuffer = ByteBuffer.wrap( response );
				solution.forEachEntry( ( k, v ) -> {
					mapping[ ( int ) k ] = v;
					return true;
				} );
				for ( final long m : mapping )
					responseBuffer.putLong( m );
				socket.send( response, 0 );

			}

			socket.close();
			ctx.close();

		} );
		solutionHandlerThread.start();

		final SolverQueueServerZMQ server = new SolverQueueServerZMQ(
				ACTION_ADDRESS,
				SOLUTION_REQ_REP,
				SOLUTION_DIST,
				INITIAL_SOLUTION,
				LATEST_SOL_ADDR,
				1,
				MIN_WAIT_AFTER_LAST_ACTION );

		final Context ctx = ZMQ.context( 1 );
		final Socket currentSolutionSocket = ctx.socket( ZMQ.REQ );
		currentSolutionSocket.connect( LATEST_SOL_ADDR );
		currentSolutionSocket.send( "" );
		final byte[] solutionResponse = currentSolutionSocket.recv();

		Assert.assertNotNull( solutionResponse );
		Assert.assertEquals( 0, solutionResponse.length % ( 2 * Long.BYTES ) );

		final TLongLongHashMap init = INITIAL_SOLUTION.get();

		final TLongLongHashMap responseHM = new TLongLongHashMap();

		final ByteBuffer solutionBuffer = ByteBuffer.wrap( solutionResponse );
		while ( solutionBuffer.hasRemaining() )
			responseHM.put( solutionBuffer.getLong(), solutionBuffer.getLong() );
		Assert.assertEquals( init.size() * 2 * Long.BYTES, solutionResponse.length );

		Assert.assertEquals( init, responseHM );

		currentSolutionSocket.close();

		final Socket actionSocket = ctx.socket( ZMQ.REQ );
		final Socket subscriptionSocket = ctx.socket( ZMQ.SUB );
		actionSocket.connect( ACTION_ADDRESS );
		subscriptionSocket.connect( SOLUTION_DIST );
		subscriptionSocket.subscribe( "".getBytes() );
		final TLongLongHashMap solutionSubscription = new TLongLongHashMap();
		final Thread solutionSubscriptionThread = new Thread( () -> {
			final byte[] msg = subscriptionSocket.recv();
			Assert.assertEquals( 0, msg.length % ( 2 * Long.BYTES ) );
			solutionSubscription.clear();
			final ByteBuffer bb = ByteBuffer.wrap( msg );
			while ( bb.hasRemaining() )
			{
				final long k = bb.getLong();
				final long v = bb.getLong();
				if ( v >= 0 )
					solutionSubscription.put( k, v );
			}
		} );
		solutionSubscriptionThread.start();

		final ArrayList< Action > testActions = new ArrayList<>();
		testActions.add( new Merge( 3, 1 ) );
		testActions.add( new Merge( 3, 2 ) );
		testActions.add( new Detach( 5 ) );
		testActions.add( new Detach( 3 ) );

		final JsonObject jsonObject = new JsonObject();
		jsonObject.add( "actions", Action.toJson( testActions ) );
		jsonObject.addProperty( "version", "1" );

		actionSocket.send( jsonObject.toString() );
		final byte[] response = actionSocket.recv();
		Assert.assertEquals( 1, response.length );
		Assert.assertEquals( 0, response[ 0 ] );
		Thread.sleep( 300 );

		solutionSubscriptionThread.join();

		actionSocket.close();

		subscriptionSocket.close();

		ctx.close();
		solutionHandlerThread.interrupt();

		final TLongLongHashMap solutionNormalized = new TLongLongHashMap();
		final TLongLongHashMap minimumLabelInSegmentMap = new TLongLongHashMap();
		solution.forEachEntry( ( k, v ) -> {

			if ( v >= 0 )
				if ( !minimumLabelInSegmentMap.contains( v ) )
					minimumLabelInSegmentMap.put( v, k );
				else
					minimumLabelInSegmentMap.put( v, Math.min( minimumLabelInSegmentMap.get( v ), k ) );
			return true;
		} );

		solution.forEachEntry( ( k, v ) -> {
			solutionNormalized.put( k, minimumLabelInSegmentMap.get( v ) );
			return true;
		} );
		final TLongLongHashMap solutionReference = new TLongLongHashMap(
				new long[] { 4, 3, 2, 1 },
				new long[] { 1, 3, 1, 1 } );
		Assert.assertEquals( solutionReference, solutionNormalized );
		Assert.assertEquals( solutionReference, solutionSubscription );

	}

}
