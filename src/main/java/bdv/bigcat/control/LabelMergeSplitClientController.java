/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package bdv.bigcat.control;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.bigcat.label.FragmentSegmentAssignment.FragmentSegmentSerializer;
import bdv.bigcat.ui.AbstractSaturatedARGBStream;
import bdv.labels.labelset.Multiset.Entry;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.viewer.ViewerPanel;
import gnu.trove.map.hash.TLongLongHashMap;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;

/**
 * User input for fragment/ segment merge and split events that sends
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class LabelMergeSplitClientController implements MouseListener, KeyListener
{
	protected class SocketListener extends Thread
	{
		final void updateAssignment( String msg )
		{
//			System.out.println( "Received : " + msg );
			System.out.println( "Message received.");
			if ( msg.equals( "NEED MORE" ) )
				return;
			msg = msg.substring( 1, msg.length() - 2 );
			final TLongLongHashMap lut = new TLongLongHashMap();
			final String[] msgSplit = msg.split( " " );
			for ( int i = 0; i < msgSplit.length; ++i )
				lut.put( i, Long.parseLong( msgSplit[ i ] ) );

			assignment.initLut( lut );
			colorStream.clearCache();
			viewer.requestRepaint();
		}

		@Override
		final public void run()
		{
			while ( !isInterrupted() )
			{
				final String msg = socket.recvStr( Charset.defaultCharset() );
				updateAssignment( msg );
			}
		}
	}

	final protected ViewerPanel viewer;
	final protected RealRandomAccessible< VolatileLabelMultisetType > labels;
	final protected RealRandomAccess< VolatileLabelMultisetType > labelAccess;
	final protected AbstractSaturatedARGBStream colorStream;
	final protected FragmentSegmentAssignment assignment;
	protected long activeFragmentId = 0;
	final protected Socket socket;
	final protected SocketListener socketListener;

	final GsonBuilder gsonBuilder = new GsonBuilder();
	{
		gsonBuilder.registerTypeAdapter( FragmentSegmentAssignment.class, new FragmentSegmentAssignment.FragmentSegmentSerializer() );
		//gsonBuilder.setPrettyPrinting();
	}
	final Gson gson = gsonBuilder.create();


	public LabelMergeSplitClientController(
			final ViewerPanel viewer,
			final RealRandomAccessible< VolatileLabelMultisetType > labels,
			final AbstractSaturatedARGBStream colorStream,
			final FragmentSegmentAssignment assignment,
			final Socket socket )
	{
		this.viewer = viewer;
		this.labels = labels;
		this.colorStream = colorStream;
		this.assignment = assignment;
		this.socket = socket;
		labelAccess = labels.realRandomAccess();

		socketListener = new SocketListener();
		socketListener.start();
	}

	public LabelMergeSplitClientController(
			final ViewerPanel viewer,
			final RealRandomAccessible< VolatileLabelMultisetType > labels,
			final AbstractSaturatedARGBStream colorStream,
			final FragmentSegmentAssignment assignment,
			final ZContext ctx,
			final String socketUrl )
	{
		this( viewer, labels, colorStream, assignment, ctx.createSocket( ZMQ.PAIR ) );
		socket.connect( socketUrl );
	}

	@Override
	public void mouseClicked( final MouseEvent e )
	{
		labelAccess.setPosition( e.getX(), 0 );
		labelAccess.setPosition( e.getY(), 1 );
		labelAccess.setPosition( 0, 2 );

		viewer.displayToGlobalCoordinates( labelAccess );

		final VolatileLabelMultisetType labelValues = labelAccess.get();

		String labelSetString;
		if ( labelValues.isValid() )
		{
			labelSetString = "";
			final long oldActiveFragmentId = activeFragmentId;
			long maxCount = 0;
			for ( final Entry< Label > entry : labelValues.get().entrySet() )
			{
				final Label label = entry.getElement();
				final long count = entry.getCount();

				if ( count > maxCount )
				{
					maxCount = count;
					activeFragmentId = label.id();
				}
				labelSetString += entry.getElement().id() + ", ";
			}
			labelSetString = labelSetString.substring( 0, labelSetString.length() - 2 );

			if ( ( e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK ) != 0 )
			{
				System.out.println( "Merging" );

				final long oldActiveSegmentId = assignment.getSegment( oldActiveFragmentId );
				final long activeSegmentId = assignment.getSegment( activeFragmentId );
				if ( oldActiveSegmentId != activeSegmentId )
				{
					final long[] oldActiveFragments = assignment.getFragments( oldActiveSegmentId );
					final long[] activeFragments = assignment.getFragments( activeSegmentId );
//					final String msg =
//							"merge("
//							+ Arrays.toString( ArrayUtils.addAll( oldActiveSegments, activeSegments ) )
//							+ ")";
					final String msg =
							"merge("
							+ Arrays.toString( new long[]{ oldActiveFragmentId, activeFragmentId } )
							+ ")";
					System.out.println( "Sending : " + msg );
					socket.send( msg );
				}
				assignment.mergeFragmentSegments( oldActiveFragmentId, activeFragmentId );
			}
			else
			{
				if ( ( e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK ) != 0 )
				{
					System.out.println( "Detaching" );

					final long activeSegmentId = assignment.getSegment( activeFragmentId );
					final long[] fragments = assignment.getFragments( activeSegmentId );

					final String msg =
							"detach(["
							+ "[" + activeFragmentId + "], "
							+ Arrays.toString( fragments )
							+ "])";
					System.out.println( "Sending : " + msg );
					socket.send( msg );

					assignment.detachFragment( activeFragmentId );

				}

				colorStream.setActive( activeFragmentId );
			}

			colorStream.clearCache();

			viewer.requestRepaint();
		}
		else
			labelSetString = "invalid";

		System.out.println( " Labels = {" + labelSetString + "}" );
	}

	@Override
	public void mouseEntered( final MouseEvent e ) {}

	@Override
	public void mouseExited( final MouseEvent arg0 ) {}

	@Override
	public void mousePressed( final MouseEvent arg0 ) {}

	@Override
	public void mouseReleased( final MouseEvent arg0 ) {}

	@Override
	public void keyReleased( final KeyEvent e )	{}

	@Override
	public void keyPressed( final KeyEvent e ) {}

	@Override
	public void keyTyped( final KeyEvent e )
	{
		if ( e.getKeyChar() == 'c' )
		{
			colorStream.incSeed();
			colorStream.clearCache();
			viewer.requestRepaint();
		}
		else if ( e.getKeyChar() == 'C' )
		{
			colorStream.decSeed();
			colorStream.clearCache();
			viewer.requestRepaint();
		}
		else if ( e.getKeyChar() == 'e' )
		{
			System.out.println( gson.toJson( assignment ) );
		}
	}
}
