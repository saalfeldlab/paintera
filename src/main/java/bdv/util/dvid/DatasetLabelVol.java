package bdv.util.dvid;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import bdv.util.http.HttpRequest;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedLongType;

public class DatasetLabelVol extends Dataset
{
	
	public static final String TYPE = "labelvol";

	public DatasetLabelVol( Node node, String name )
	{
		super( node, name, TYPE );
	}
	
//	public String getUrl()
//	{
//		return getNode().getUrl() + "/" + name;
//	}
	
	public static String getSparsevolRequestString( UnsignedLongType label )
	{
		return "sparsevol/" + label.getIntegerLong();
	}
	
	public byte[] getSparseVol( UnsignedLongType label, Map< String, String > options ) throws MalformedURLException, IOException
	{
		String url = getRequestString( getSparsevolRequestString( label ), null, options );
		byte[] data = HttpRequest.getRequest( url );
		return data;
	}
	
	public static void drawInto( 
			RandomAccessibleInterval< UnsignedLongType > target,
			byte[] rleData,
			UnsignedLongType label,
			long[] offset
			)
	{
		ByteBuffer bb = ByteBuffer.wrap( rleData );
		byte payloadDescriptor = bb.get();
		assert payloadDescriptor == 0: "Expected zero payload!";
		int nDim = bb.get() & 0xff;
		assert nDim == 3: "Expected three dimensions";
		int runDim = bb.get() & 0xff;
		assert runDim >= 0 && runDim <= 2: "Run dimension must be less than three";
		bb.get(); // ignore reserved byte
		bb.getInt(); // ignore (number of pixels, falls back to 0 right now);
		bb.getInt(); // ignore (number of spans <- what is that?);
		RandomAccess< UnsignedLongType > ra = target.randomAccess();
		long runDimLength = target.dimension( runDim );
		ArrayList< Integer > nonRunDims = new ArrayList< Integer >();
		for ( int d = 0; d < 3; ++d )
			if( d != runDim )
				nonRunDims.add(  d  );
		long[] dims = new long[ 3 ];
		target.dimensions( dims );
		System.out.println( runDim + " " + bb.position() + "   LAENGE!" );
		while( bb.position() < rleData.length )
		{
			long[] startingCoordinates = new long[]
					{
							bb.getInt() - offset[ 0 ],
							bb.getInt() - offset[ 1 ],
							bb.getInt() - offset[ 2 ]
					};
			System.out.println( Arrays.toString( startingCoordinates ) );
			boolean isOutOfBounds = false;
			for( Integer d : nonRunDims )
			{
				long v = startingCoordinates[ d.intValue() ];
				if ( v < 0l || v >= dims[ d ] )
				{
					isOutOfBounds = true;
					break;
				}
			}
			if ( isOutOfBounds ) continue;
			long offsetRunDimLength = startingCoordinates[ runDim ] + runDimLength;
			long maxLength = Math.min(  offsetRunDimLength, dims[ runDim ] );
			for ( 
					ra.setPosition( startingCoordinates ); 
					ra.getLongPosition( runDim ) < maxLength;
					ra.fwd( runDim )
				)
			{
				ra.get().set( label );
			}
		}
	}

}
