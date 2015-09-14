package bdv.img.dvid;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.util.dvid.DatasetBlkUint8;
import bdv.util.dvid.DatasetNByte;
import bdv.util.dvid.DatasetNByte.NByteIO;
import bdv.util.dvid.Repository;
import net.imglib2.Cursor;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 * 
 *         Write any {@link RandomAccessibleInterval} of type {@link RealType}
 *         into an existing dvid repository/dataset.
 *
 */
public class DvidNBytesWriter< T extends NumericType< T > > extends AbstractDvidImageWriter< T >
{

	/**
	 * @param url
	 *            Url to dvid server in the form of http://hostname:port
	 * @param uuid
	 *            Uuid of repository within the dvid server specified by apiUrl.
	 *            The root node of the repository will be used for writing.
	 * @param dataSet
	 *            Name of the data set within the repository specified by uuid.
	 *            This data set must be of type labelblk
	 * @param blockSize
	 *            Block size of the data set. Must suit block size stored in
	 *            dvid server.
	 * @throws IOException 
	 * @throws JsonIOException 
	 * @throws JsonSyntaxException 
	 */
	public DvidNBytesWriter( 
			final String url,
			final String uuid,
			final String dataSet,
			int nBytes,
			DatasetNByte.NByteIO< T > io ) throws JsonSyntaxException, JsonIOException, IOException
	{
		this( new DatasetNByte< T >( new Repository( url, uuid ).getRootNode(), dataSet, nBytes, io ) );
	}
	
	public DvidNBytesWriter( DatasetNByte< T > dataset ) throws JsonSyntaxException, JsonIOException, IOException
	{
		super( dataset, dataset.getBlockSize() );
	}
	
	@Override
	public void writeImage( RandomAccessibleInterval<T> image, int[] steps, int[] offset )
	{
		for( int d = 0; d < this.blockSize.length; ++d )
			if( steps[ d ] != this.blockSize[ d ] )
				throw new IllegalArgumentException( 
						"steps != blockSize (" +
						Arrays.toString( steps ) + " != " +
						Arrays.toString( this.blockSize ) + ")" );
		super.writeImage( image, steps, offset );
	}
	
	@Override
	public void writeImage( RandomAccessibleInterval<T> image, int iterationAxis, int[] steps, int[] offset )
	{
		for( int d = 0; d < this.blockSize.length; ++d )
			if( steps[ d ] != this.blockSize[ d ] )
				throw new IllegalArgumentException( 
						"steps != blockSize (" +
						Arrays.toString( steps ) + " != " +
						Arrays.toString( this.blockSize ) + ")" );
		super.writeImage( image, iterationAxis, steps, offset );
	}
	
	@Override
	public void writeImage( 
			RandomAccessibleInterval<T> image,
			int iterationAxis,
			int[] steps,
			int[] offset,
			T borderExtension )
	{
		for( int d = 0; d < this.blockSize.length; ++d )
			if( steps[ d ] != this.blockSize[ d ] )
				throw new IllegalArgumentException( 
						"steps != blockSize (" +
						Arrays.toString( steps ) + " != " +
						Arrays.toString( this.blockSize ) + ")" );
		super.writeImage( image, iterationAxis, this.blockSize, offset, borderExtension );
	}
	
	/**
	 * This is for checking functionality. Adjust apiUrl, uuid and dataSet
	 * according to your needs.
	 * 
	 * @param args
	 * @throws IOException
	 */
	public static void main( String[] args ) throws IOException
	{

		// this is for testing purposes, modify apiUrl, uuid and dataSet
		// according to your needs
		// if values received from server differ from input, these values will
		// be printed to stdout
		// otherwise no output

		String url = "http://vm570.int.janelia.org:8080";
		String uuid = "4668221206e047648f622dc4690ff7dc";
		String dataSet = "bigcat-nbyte-writer";
		
		Repository repo = new Repository( url, uuid );
		
		try
		{
			repo.getRootNode().createDataset( dataSet, DatasetBlkUint8.TYPE );
		}
		catch ( Exception e )
		{
			e.printStackTrace( System.err );
		}
		NByteIO< LongType > io = new NByteIO< LongType >()
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
		
		DatasetNByte< LongType > ds = new DatasetNByte< LongType >( 
				repo.getRootNode(), 
				dataSet, 
				( new LongType().getBitsPerPixel() / new ByteType().getBitsPerPixel() ),
				io );
		
		DvidNBytesWriter< LongType > writer = new DvidNBytesWriter< LongType >( ds );
		
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
		long nBlocks = DatasetNByte.product( nBlocksByDimension );
		
		ArrayImg< LongType, LongArray > img = ArrayImgs.longs( dim );
		Random rng = new Random( 100 );
		for ( LongType i : img )
			i.set( rng.nextLong() );
		
		ArrayImg< LongType, LongArray > target = ArrayImgs.longs( targetDim );
		
		writer.writeImage( img, new int[] { 0, 0, 0 } );
		
		// read
		for ( long i = 0; i < nBlocks; ++i )
		{
			long[] position = new long[ 3 ];
			int[] positionInt = new int[ 3 ];
			DatasetNByte.indexToPosition( i, nBlocksByDimension, position );
			for ( int d = 0; d < position.length; ++d )
			{
				positionInt[ d ] = ( int ) position[ d ];
				position[ d ] *= blockSize[ d ];
			}
			IntervalView< LongType > interval = Views.offsetInterval( target, position, blockSizeLong );
			ds.getBlock( interval, positionInt, io );
		}
		
		ArrayRandomAccess< LongType > t = target.randomAccess();
		
		
		for ( Cursor< LongType > r = Views.flatIterable( img ).cursor(); r.hasNext(); )
		{
			long comp = r.next().get();
			t.setPosition( r );
			long test = t.get().get();
			if ( test != comp )
			{
				Point p = new Point( r.numDimensions() );
				p.setPosition( r );
				System.out.println( p + " " + test + " " + comp );
				System.exit( 9001 );
			}
		}
		System.out.println( "Done." );
	}

}
