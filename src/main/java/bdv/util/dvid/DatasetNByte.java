package bdv.util.dvid;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.util.Random;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.util.http.HttpRequest;
import net.imglib2.Cursor;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class DatasetNByte< T > extends DatasetBlk< T >
{
	
	public static interface NByteToBuffer< U >
	{
		public void write( U data, ByteBuffer bb );
		
		public void read( U data, ByteBuffer bb );
	}
	
	public static final String TYPE = "nbyte";
	
	private final int nBytes;
	
	private final int[] blockSize;
	
	private final int numByteBlocks;
	
	private final byte[] buffer;
	
	public DatasetNByte( Node node, String name, int nBytes ) throws JsonSyntaxException, JsonIOException, IOException
	{
		super( node, name, TYPE );
		this.nBytes = nBytes;
		this.blockSize = getBlockSize();
		assert nBytes <= this.blockSize[ 0 ] : "Currently, size limit is data of size 32 bytes";
		assert this.blockSize[ 0 ] % nBytes == 0;
		this.numByteBlocks = this.nBytes; // this.blockSize[ 0 ] / this.nBytes; 
		this.buffer = new byte[ product( this.blockSize ) ];
	}

	@Override
	public void get( RandomAccessibleInterval< T > target, int[] offset ) throws MalformedURLException, IOException
	{
		throw new UnsupportedOperationException( "Can only write blocks with arbitrary size" );
	}

	@Override
	public void put( RandomAccessibleInterval< T > source, int[] offset ) throws MalformedURLException, IOException
	{
		throw new UnsupportedOperationException( "Can only read blocks with arbitrary size" );
	}
	
	public void writeBlock( 
			RandomAccessibleInterval< T > source, 
			int[] position,
			NByteToBuffer< T > writer ) throws MalformedURLException, IOException
	{
		for ( int d = 0; d < blockSize.length; ++d )
			assert source.dimension( d ) == blockSize[ d ];
		
		int[] correctedPosition = correctPosition( position.clone() );
		Cursor< T > cursor = Views.flatIterable( source ).cursor();
		for( int i = 0; i < this.numByteBlocks; ++i, ++correctedPosition[ 0 ] )
		{
			ByteBuffer bb = ByteBuffer.wrap( this.buffer );
			for( int k = 0; k < this.buffer.length; k += this.nBytes )
			{
				writer.write( cursor.next(), bb );
			}
			String url = getBlockRequestUrl( correctedPosition, 1 );
			HttpRequest.postRequest( url, this.buffer );
		}
		
	}
	
	public void getBlock( 
			RandomAccessibleInterval< T > target,
			int[] position,
			NByteToBuffer< T > reader ) throws MalformedURLException, IOException
	{
		for ( int d = 0; d < blockSize.length; ++d )
			assert target.dimension( d ) == blockSize[ d ];
		
		int[] correctedPosition = correctPosition( position.clone() );
		Cursor< T > cursor = Views.flatIterable( target ).cursor();
		for( int i = 0; i < this.numByteBlocks; ++i, ++correctedPosition[ 0 ] )
		{
			String url = getBlockRequestUrl( correctedPosition, 1 );
			HttpRequest.getRequest( url, buffer );
			
			ByteBuffer bb = ByteBuffer.wrap( this.buffer );
			
			for( int k = 0; k < this.buffer.length; k += this.nBytes )
			{
				reader.read( cursor.next(), bb );
			}
			
		}
	}
	
	protected int[] correctPosition( int[] position )
	{
		position[ 0 ] *= this.numByteBlocks;
		return position;
	}
	
	public static int product( int[] array )
	{
		int result = 1;
		for( int a : array )
			result *= a;
		return result;
	}
	
	public static long product( long[] array )
	{
		long result = 1;
		for( long a : array )
			result *= a;
		return result;
	}
	
	final static public void indexToPosition( long index, final long[] dimensions, final long[] position )
	{
		final int maxDim = dimensions.length - 1;
		for ( int d = 0; d < maxDim; ++d )
		{
			final long j = index / dimensions[ d ];
			position[ d ] = index - j * dimensions[ d ];
			index = j;
		}
		position[ maxDim ] = index;
	}
	
	public static void main( String[] args ) throws JsonSyntaxException, JsonIOException, IOException
	{
		
		String url = "http://vm570.int.janelia.org:8080";
		String uuid = "4668221206e047648f622dc4690ff7dc";
		String dataSet = "bigcat-nbyte-2";
		
		Repository repo = new Repository( url, uuid );
		
		try
		{
			repo.getRootNode().createDataset( dataSet, DatasetBlkUint8.TYPE );
		}
		catch ( Exception e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace( System.err );
		}
		
		DatasetNByte< LongType > ds = new DatasetNByte< LongType >( 
				repo.getRootNode(), 
				dataSet, 
				( new LongType().getBitsPerPixel() / new ByteType().getBitsPerPixel() ) );
		
		NByteToBuffer< LongType > io = new NByteToBuffer< LongType >()
		{

			@Override
			public void write( LongType data, ByteBuffer bb )
			{
				bb.putLong( data.get() );
			}

			@Override
			public void read( LongType data, ByteBuffer bb )
			{
				data.set( bb.getLong() );
			}
		};
		
		long[] dim = new long[] { 35, 80, 65 };
		long[] targetDim = new long[ dim.length ];
		int[] blockSize = new int[] { 32, 32, 32 };
		long[] blockSizeLong = new long[] { 32, 32, 32 };
		long[] nBlocksByDimension = new long[ 3 ];
		for ( int  d = 0; d < dim.length; ++d )
		{
			long remainder = dim[ d ] % blockSize[ d ];
			nBlocksByDimension[ d ] = dim[ d ] / blockSize[ d ] + ( remainder == 0 ? 0 : 1 );
			targetDim[ d ] = nBlocksByDimension[ d ] * blockSize[ d ];
		}
		long nBlocks = product( nBlocksByDimension );
		
		ArrayImg< LongType, LongArray > img = ArrayImgs.longs( dim );
		Random rng = new Random( 100 );
		for ( LongType i : img )
			i.set( rng.nextLong() );
		
		ArrayImg< LongType, LongArray > target = ArrayImgs.longs( targetDim );
		
		ExtendedRandomAccessibleInterval< LongType, ArrayImg< LongType, LongArray > > extended = Views.extendZero( img );
		
		// write
		for ( long i = 0; i < nBlocks; ++i )
		{
			long[] position = new long[ 3 ];
			int[] positionInt = new int[ 3 ];
			indexToPosition( i, nBlocksByDimension, position );
			for ( int d = 0; d < position.length; ++d )
			{
				positionInt[ d ] = ( int ) position[ d ];
				position[ d ] *= blockSize[ d ];
			}
			IntervalView< LongType > interval = Views.offsetInterval( extended, position, blockSizeLong );
			ds.writeBlock( interval, positionInt, io );
		}
		
		// read
		for ( long i = 0; i < nBlocks; ++i )
		{
			long[] position = new long[ 3 ];
			int[] positionInt = new int[ 3 ];
			indexToPosition( i, nBlocksByDimension, position );
			for ( int d = 0; d < position.length; ++d )
			{
				positionInt[ d ] = ( int ) position[ d ];
				position[ d ] *= blockSize[ d ];
			}
			IntervalView< LongType > interval = Views.offsetInterval( target, position, blockSizeLong );
			ds.getBlock( interval, positionInt, io );
		}
		
		ArrayCursor< LongType > cursor = img.cursor();
		ArrayRandomAccess< LongType > ra = target.randomAccess();
		while( cursor.hasNext() )
		{
			cursor.fwd();
			ra.setPosition( cursor );
			LongType ref = cursor.get();
			LongType comp = ra.get();
			if( comp.get() != ref.get() )
			{
				Point p = new Point( cursor.numDimensions() );
				p.setPosition( cursor );
				System.out.println( p + " " + ref.get() + " " + comp.get() );
				System.exit( 9001 );
			}
		}
	System.out.println( "Done." );	
	}
	
}
