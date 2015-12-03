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
public class ZmqTestClient
{
	/**
	 * @param args
	 */
	public static void main( String[] args )
	{
		final ZContext ctx = new ZContext();
		final Socket client = ctx.createSocket( ZMQ.PAIR );
		//client.connect( "ipc:///tmp/zmqtest" );
		client.connect( "tcp://localhost:5556" );
		
		String msg = "";
		for ( int i = 0; i < 10; ++i )
		{
			client.send( "Hello " + i );
		}
		
		client.send( "exit" );
		
		ctx.destroy();
	}
}
