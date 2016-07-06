package bdv.util;

import java.nio.charset.Charset;
import java.util.stream.LongStream;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import com.google.gson.Gson;

import gnu.trove.list.array.TLongArrayList;

public class RemoteIdService implements IdService
{
	/* number of ids requested at once */
	private static int count = 10;

	static private class Request
	{
		final public int count;

		public Request( final int count )
		{
			this.count = count;
		}
	}

	static private class Response
	{
		final public long begin;
		final public long end;

		public Response( final long begin, final long end )
		{
			this.begin = begin;
			this.end = end;
		}
	}

	final private Gson gson;

	final private Socket socket;

	private long next = 0;
	private long end = 0;

	public RemoteIdService(
			final ZContext ctx,
			final String url )
	{
		gson = new Gson();

		/* connect */
		socket = ctx.createSocket( ZMQ.REQ );
		socket.connect( url );

		/* init ID */
		update();
	}

	private synchronized void update()
	{
		while ( !socket.send( gson.toJson( new Request( count ) ) ) )
			System.out.println( "Failed sending message." );
		final Response response = gson.fromJson(
				socket.recvStr( Charset.defaultCharset() ),
				Response.class );
		next = response.begin;
		end = response.end;
		System.out.println( "begin " + next + ", end " + end );
	}

	@Override
	public synchronized void invalidate( final long id )
	{
		while ( next < id )
			update();
	}

	@Override
	public synchronized long next()
	{
		while ( next >= end )
			update();

		return next++;
	}

	/**
	 * TODO may be request n - something new ids instead of looping...
	 */
	@Override
	public synchronized long[] next( final int n )
	{
		int m = 0;
		TLongArrayList ids = new TLongArrayList();
		do
		{
			final long end1 = Math.min( end, next + n - ids.size() );
			final long[] someIds =
					LongStream.range(
							next,
							end1 ).toArray();
			ids.add( someIds );
			if ( end1 < end )
				next = end1;
			else
				update();
		}
		while ( ids.size() < n );

		return ids.toArray();
	}
}
