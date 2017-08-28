package bdv.bigcat.viewer.atlas.solver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import bdv.bigcat.viewer.state.FragmentSegmentAssignmentWithHistory;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentWithHistory.Action;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentWithHistory.Action.TYPE;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentWithHistory.Detach;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentWithHistory.Merge;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentWithHistory.NoAction;
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
			final List< FragmentSegmentAssignmentWithHistory.Action > actions = new ArrayList<>();

			final Context ctx = ZMQ.context( 1 );
			final Socket socket = ctx.socket( ZMQ.REP );
			socket.bind( SOLUTION_REQ_REP );

			final TYPE[] actionTypes = FragmentSegmentAssignmentWithHistory.Action.TYPE.values();

			while ( !Thread.interrupted() )
			{

				final byte[] msg = socket.recv();
//				System.out.println( "RECEIVED MSG " + Arrays.toString( msg ) );
				final ByteBuffer bb = ByteBuffer.wrap( msg );
				final List< FragmentSegmentAssignmentWithHistory.Action > incomingActions = new ArrayList<>();
				while ( bb.hasRemaining() )
				{
					final TYPE actionType = actionTypes[ bb.getInt() ];
					switch ( actionType )
					{
					case NO_ACTION:
						break;
					case MERGE:
						incomingActions.add( new FragmentSegmentAssignmentWithHistory.Merge( bb.getLong(), bb.getLong() ) );
						break;
					case DETACH:
						incomingActions.add( new FragmentSegmentAssignmentWithHistory.Detach( bb.getLong() ) );
						break;
					default:
						break;
					}
				}

				actions.addAll( incomingActions );

				for ( final FragmentSegmentAssignmentWithHistory.Action action : incomingActions )
					if ( action instanceof FragmentSegmentAssignmentWithHistory.Merge )
					{
						final Merge merge = ( FragmentSegmentAssignmentWithHistory.Merge ) action;
						final long f1 = merge.ids()[ 0 ];
						final long f2 = merge.ids()[ 1 ];

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
					else if ( action instanceof FragmentSegmentAssignmentWithHistory.Detach )
					{
						final Detach detach = ( FragmentSegmentAssignmentWithHistory.Detach ) action;
						final long id = detach.ids()[ 0 ];
						if ( solution.contains( id ) )
							solution.put( id, id );
					}

				final byte[] response = new byte[ solution.size() * 2 * Long.BYTES ];
				final ByteBuffer responseBuffer = ByteBuffer.wrap( response );
				solution.forEachEntry( ( k, v ) -> {
					responseBuffer.putLong( k );
					responseBuffer.putLong( v );
					return true;
				} );
				socket.send( response );

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

//		final Thread clientThread = new Thread( () -> {

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
		subscriptionSocket.subscribe( "" );
//		System.out.println( "ADDR? " + SOLUTION_DIST );
		final TLongLongHashMap solutionSubscription = new TLongLongHashMap();
		final Thread solutionSubscriptionThread = new Thread( () -> {
//			System.out.println( "WAITING FOR SOLUTION WHY NOT ARRIVING?" );
			final byte[] msg = subscriptionSocket.recv();
//			System.out.println( "GOT SOLUTION" );
			Assert.assertEquals( 0, msg.length % ( 2 * Long.BYTES ) );
			solutionSubscription.clear();
			final ByteBuffer bb = ByteBuffer.wrap( msg );
			while ( bb.hasRemaining() )
				solutionSubscription.put( bb.getLong(), bb.getLong() );
		} );
		solutionSubscriptionThread.start();

		final ArrayList< Action > testActions = new ArrayList<>();
		testActions.add( new Merge( 3, 1 ) );
		testActions.add( new Merge( 3, 2 ) );
		testActions.add( new Detach( 5 ) );
		testActions.add( new Detach( 3 ) );

		testActions.forEach( action -> {
//			System.out.println( "action " + action );
			actionSocket.send( actionToByteArray( action ) );
//			System.out.println( "Receiving" );
			final byte[] response = actionSocket.recv();
			Assert.assertEquals( 1, response.length );
			Assert.assertEquals( 0, response[ 0 ] );
//			System.out.println( "RESPONSE " + Arrays.toString( response ) );
//			System.out.println( "current solution: " + solution );
		} );
		Thread.sleep( 300 );
//		System.out.println( "current solution: " + solution );

		solutionSubscriptionThread.join();

		actionSocket.close();

		subscriptionSocket.close();

		ctx.close();
		solutionHandlerThread.interrupt();
//		server.close();

		final TLongLongHashMap solutionNormalized = new TLongLongHashMap();
		final TLongLongHashMap minimumLabelInSegmentMap = new TLongLongHashMap();
		solution.forEachEntry( ( k, v ) -> {
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
//		System.out.println( solutionNormalized + " " + solutionSubscription );
		final TLongLongHashMap solutionReference = new TLongLongHashMap(
				new long[] { 4, 3, 2, 1 },
				new long[] { 1, 3, 1, 1 } );
		Assert.assertEquals( solutionReference, solutionNormalized );
		Assert.assertEquals( solutionReference, solutionSubscription );

	}

	public static byte[] actionToByteArray( final Action a )
	{
		if ( a instanceof Merge )
		{
			final byte[] data = new byte[ 2 * Long.BYTES + Integer.BYTES ];
			final ByteBuffer bb = ByteBuffer.wrap( data );
			bb.putInt( TYPE.MERGE.ordinal() );
			final Merge m = ( Merge ) a;
			bb.putLong( m.ids()[ 0 ] );
			bb.putLong( m.ids()[ 1 ] );
			return data;
		}
		if ( a instanceof Detach )
		{
			final byte[] data = new byte[ 1 * Long.BYTES + Integer.BYTES ];
			final ByteBuffer bb = ByteBuffer.wrap( data );
			bb.putInt( TYPE.DETACH.ordinal() );
			final Detach d = ( Detach ) a;
			bb.putLong( d.ids()[ 0 ] );
			return data;
		}
		else if ( a instanceof NoAction )
		{
			final byte[] data = new byte[ Integer.BYTES ];
			final ByteBuffer bb = ByteBuffer.wrap( data );
			bb.putInt( TYPE.NO_ACTION.ordinal() );
			return data;
		}
		return new byte[ 0 ];
	}

}
