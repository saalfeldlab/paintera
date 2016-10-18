/**
 *
 */
package bdv.zmqtest;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

/**
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 *
 */
public class ZmqTestServer
{
	/**
	 * @param args
	 */
	public static void main( String[] args )
	{
		final ZContext ctx = new ZContext();
		final Socket server = ctx.createSocket( ZMQ.PAIR );
		server.bind( "ipc:///tmp/zmqtest" );
//		server.bind( "tcp://*:5556" );
		System.out.println( "bind complete" );

		String msg = "";
		while ( !msg.equals( "exit" ) )
		{
			msg = server.recvStr();
			System.out.println( msg );
		}

		ctx.destroy();
	}
}
