package bdv.util.dvid;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;

import bdv.util.http.HttpRequest;
import bdv.util.http.HttpRequest.Writer;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 * 
 * Dataset class corresponding to dvid dataype uint8blk.
 *
 */
public class DatasetBlkUint8 extends DatasetBlk< UnsignedByteType >
{
	
	public static interface NByteResponseHandler< T > extends HttpRequest.ResponseHandler
	{
		public Interval getInterval();
	}

	public static abstract class RandomAccessibleNByteResponseHandler< T > implements NByteResponseHandler< T >
	{

		private final RandomAccessibleInterval< T > target;

		private final int nBytes;

		RandomAccessibleNByteResponseHandler( final RandomAccessibleInterval< T > target, final int nBytes )
		{
			this.target = target;
			this.nBytes = nBytes;
		}

		public Interval getInterval()
		{
			return target;
		}

		@Override
		public void handle( InputStream in ) throws IOException
		{
			int size = nBytes;
			for ( int d = 0; d < target.numDimensions(); ++d )
				size *= target.dimension( d );
			byte[] data = new byte[ size ];
			int off = 0, l = 0;
			do
			{
				l = in.read( data, off, data.length - off );
				off += l;
			}
			while ( l > 0 && off < data.length );

			ByteBuffer bb = ByteBuffer.wrap( data );
			for ( T t : Views.flatIterable( target ) )
			{
				getNext( bb, t );
			}
		}

		abstract protected void getNext( ByteBuffer bb, T target );

	}

	public static final String TYPE = "uint8blk";
	
	public DatasetBlkUint8( Node node, String name )
	{
		super( node, name, TYPE );
	}

	@Override
	public void put( 
			RandomAccessibleInterval< UnsignedByteType > source,
			int[] offset
			) throws MalformedURLException, IOException
	{
		HttpRequest.postRequest( 
				getIntervalRequestUrl( source, offset ), 
				Views.flatIterable( source ), "application/octet-stream" );
	}

	public < T > void putNByteDataType(
			final RandomAccessibleInterval< T > source,
			final int[] offset,
			final int sizeInBytes,
			HttpRequest.Writer< T > pixelWriter
			) throws MalformedURLException, IOException
	{

		long[] dimensions = new long[ source.numDimensions() ];
		source.dimensions( dimensions );
		dimensions[ 0 ] *= sizeInBytes;
		FinalInterval interval = new FinalInterval( dimensions );

		Writer< RandomAccessibleInterval< T > > writer = new HttpRequest.Writer< RandomAccessibleInterval< T > >()
		{

			@Override
			public void write( DataOutputStream out, RandomAccessibleInterval< T > data ) throws IOException
			{
				for ( T d : Views.flatIterable( data ) )
					pixelWriter.write( out, d );
			}
		};

		HttpRequest.postRequest( getIntervalRequestUrl( interval, offset ), source, "application/octet-stream", writer );
	}

	@Override
	public void get( RandomAccessibleInterval< UnsignedByteType > target, int[] offset ) throws MalformedURLException, IOException
	{
		int size = Byte.BYTES;
		for ( int d = 0; d < target.numDimensions(); ++d )
			size *= target.dimension( d );
		
		byte[] data = new byte[ size ];
		
		getByteArray( target, data, offset );
		ByteBuffer bb = ByteBuffer.wrap( data );
		for( UnsignedByteType t : Views.flatIterable( target ) )
			t.setInteger( bb.get() );
	}

	public < T > void getNByteDataType(
			NByteResponseHandler< T > target,
			final int[] offset,
			final int sizeInBytes
			) throws MalformedURLException, IOException
	{
		Interval targetInterval = target.getInterval();
		long[] dimensions = new long[ targetInterval.numDimensions() ];
		targetInterval.dimensions( dimensions );
		dimensions[ 0 ] *= sizeInBytes;
		FinalInterval interval = new FinalInterval( dimensions );

		HttpRequest.getRequest( getIntervalRequestUrl( interval, offset ), target );
		
	}

}