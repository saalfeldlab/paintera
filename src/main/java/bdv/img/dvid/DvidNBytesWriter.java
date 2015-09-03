package bdv.img.dvid;

import java.io.IOException;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.util.BlockedInterval;
import bdv.util.dvid.DatasetBlk;
import bdv.util.dvid.DatasetBlkUint8;
import bdv.util.dvid.Repository;
import bdv.util.http.HttpRequest;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 * 
 *         Write any {@link RandomAccessibleInterval} of type {@link RealType}
 *         into an existing dvid repository/dataset.
 *
 */
public class DvidNBytesWriter
{

	private final DatasetBlkUint8 dataset;

	private final int[] blockSize;

	/**
	 * @param url
	 *            Url to dvid server in the form of http://hostname:port
	 * @param uuid
	 *            Uuid of repository within the dvid server specified by apiUrl.
	 *            The root node of the repository will be used for writing.
	 * @param dataSet
	 *            Name of the data set within the repository specified by uuid.
	 *            This data set must be of type imageblk.
	 * 
	 *            This calls
	 *            {@link DvidNBytesWriter#DvidLabels64ByteWriter(String, String, String, int)}
	 *            with a default block size of 32.
	 * @throws IOException 
	 * @throws JsonIOException 
	 * @throws JsonSyntaxException 
	 **/
	public DvidNBytesWriter( String url, String uuid, String dataSet )
	{
		this( url, uuid, dataSet, DatasetBlk.defaultBlockSize() );
	}

	/**
	 * @param url
	 *            Url to dvid server in the form of http://hostname:port
	 * @param uuid
	 *            Uuid of repository within the dvid server specified by apiUrl.
	 *            The root node of the repository will be used for writing.
	 * @param dataSet
	 *            Name of the data set within the repository specified by uuid.
	 *            This data set must be of type imageblk
	 * @param blockSize
	 *            Block size of the data set. Must suit block size stored in
	 *            dvid server.
	 * @throws IOException 
	 * @throws JsonIOException 
	 * @throws JsonSyntaxException 
	 */
	public DvidNBytesWriter( String url, String uuid, final String dataSetName, final int[] blockSize )
	{
		this( new DatasetBlkUint8( new Repository( url, uuid ).getRootNode(), dataSetName ), blockSize );
	}
	
	public DvidNBytesWriter( DatasetBlkUint8 dataset, int[] blockSize )
	{
		this.dataset = dataset;
		this.blockSize = blockSize;
	}
	
	public DvidNBytesWriter( DatasetBlkUint8 dataset ) throws JsonSyntaxException, JsonIOException, IOException
	{
		this( dataset, dataset.getBlockSize() );
	}

	/**
	 * @param image
	 *            Image to be stored in dvid server.
	 * @param iterationAxis
	 *            Along which axis to iterate.
	 * @param steps
	 *            Step sizes along each axis.
	 * @param offset
	 *            Offset target position by offset.
	 * 
	 *            Write image into data set. Calls
	 *            {@link DvidNBytesWriter#writeImage(RandomAccessibleInterval, int, int[], int[])}
	 *            with iterationAxis set to 2.
	 */
	public < T extends RealType< T > > void writeImage(
			RandomAccessibleInterval< T > image,
			final int[] steps,
			final int[] offset,
			final HttpRequest.Writer< T > writer )
	{
		this.writeImage( image, 2, steps, offset, writer );
	}

	/**
	 * @param image
	 *            Image to be stored in dvid server.
	 * @param iterationAxis
	 *            Along which axis to iterate.
	 * @param steps
	 *            Step sizes along each axis.
	 * @param offset
	 *            Offset target position by offset.
	 * 
	 *            Write image into data set. Calls
	 *            {@link DvidNBytesWriter#writeImage(RandomAccessibleInterval, int, int[], int[], RealType)}
	 *            with borderExtension set to 0.
	 */
	public < T extends RealType< T > > void writeImage(
			RandomAccessibleInterval< T > image,
			final int iterationAxis,
			final int[] steps,
			final int[] offset,
			final HttpRequest.Writer< T > writer )
	{
		T zeroExtension = image.randomAccess().get().createVariable();
		zeroExtension.setZero();
		this.writeImage( image, iterationAxis, steps, offset, writer, zeroExtension );
	}

	/**
	 * @param image
	 *            Image to be stored in dvid server.
	 * @param iterationAxis
	 *            Along which axis to iterate.
	 * @param steps
	 *            Step sizes along each axis.
	 * @param offset
	 *            Offset target position by offset.
	 * @param borderExtension
	 *            Extend border with this value.
	 * 
	 *            Write image into data set. The image will be divided into
	 *            blocks as defined by steps. The target coordinates will be the
	 *            image coordinates shifted by offset.
	 */
	public < T extends RealType< T > > void writeImage(
			RandomAccessibleInterval< T > image,
			final int iterationAxis,
			final int[] steps,
			final int[] offset,
			final HttpRequest.Writer< T > writer,
			T borderExtension )
	{
		// realX ensures that realX[i] is integer multiple of blockSize
		long[] realDim = new long[ image.numDimensions() ];
		image.dimensions( realDim );
		adaptToBlockSize( realDim, this.blockSize );
		int[] realSteps = adaptToBlockSize( steps.clone(), this.blockSize );
		int[] realOffset = adaptToBlockSize( offset.clone(), this.blockSize );

		// For now, assume always that data is in [0,1,2] == "xyz" format.
		// Do we need flexibility to allow for different orderings?
		int[] dims = new int[ image.numDimensions() ];
		for ( int d = 0; d < image.numDimensions(); ++d )
			dims[ d ] = d;

		// stepSize as long[], needed for BlockedInterval
		long[] stepSize = new long[ image.numDimensions() ];
		for ( int i = 0; i < stepSize.length; i++ )
			stepSize[ i ] = realSteps[ i ];

		// Create BlockedInterval that allows for flat iteration and intuitive
		// hyperslicing.
		// Go along iterationAxis and hyperslice, then iterate over each
		// hyperslice.
		BlockedInterval< T > blockedImage = BlockedInterval.createZeroExtended( image, stepSize );
		for ( int a = 0, aUnitIncrement = 0; a < image.dimension( iterationAxis ); ++aUnitIncrement, a += realSteps[ iterationAxis ] )
		{
			IntervalView< RandomAccessibleInterval< T >> hs = 
					Views.hyperSlice( blockedImage, iterationAxis, aUnitIncrement );
			Cursor< RandomAccessibleInterval< T >> cursor = Views.flatIterable( hs ).cursor();
			while ( cursor.hasNext() )
			{
				RandomAccessibleInterval< T > block = cursor.next();
				System.out.println( cursor.getLongPosition( 0 ) + " " + cursor.getLongPosition( 1 ) + " " + a );
				int[] localOffset = realOffset.clone();
				localOffset[ iterationAxis ] = a;
				for ( int i = 0, k = 0; i < localOffset.length; i++ )
				{
					if ( i == iterationAxis )
						continue;
					localOffset[ i ] += cursor.getIntPosition( k++ ) * stepSize[ i ];
				}
				try
				{
					this.writeBlock( block, dims, localOffset, writer );
				}
				catch ( IOException e )
				{
					System.err.println( "Failed to write block: " + dataset.getRequestString( DatasetBlkUint8.getIntervalRequestString( image, realSteps ) ) );
					e.printStackTrace();
				}
			}
		}

		return;
	}

	/**
	 * @param input
	 *            Block of data to be written to dvid data set.
	 * @param dims
	 *            Interpretation of the dimensionality of the block, e.g.
	 *            [0,1,2] corresponds to "xyz".
	 * @param offset
	 *            Position of the "upper left" corner of the block within the
	 *            dvid coordinate system.
	 * @throws IOException
	 * 
	 *             Write block into dvid data set at position specified by
	 *             offset using http POST request.
	 */
	public < T extends RealType< T > > void writeBlock(
			RandomAccessibleInterval< T > input,
			final int[] dims,
			final int[] offset,
			final HttpRequest.Writer< T > writer ) throws IOException
	{
		// Offset and block dimensions must be integer multiples of
		// this.blockSize.
		// For now add the asserts, maybe make this method private/protected and
		// have
		// enclosing method take care of it.
		
		int sizeInBytes = input.randomAccess().get().getBitsPerPixel() / new ByteType().getBitsPerPixel();
		
		int[] realOffset = offset.clone();
		realOffset[ 0 ] *= sizeInBytes;

		for ( int d = 0; d < input.numDimensions(); ++d )
		{
			assert offset[ d ] % this.blockSize[ d ] == 0;
			assert ( input.dimension( d ) * d == 0 ? sizeInBytes : 1 ) % this.blockSize[ d ] == 0;
		}

		dataset.putNByteDataType( input, realOffset, sizeInBytes, writer );

	}

	/**
	 * @param input
	 *            Integer array corresponding to be adapted (will be modified).
	 * @param blockSize
	 *            Block size.
	 * @return Modified input.
	 * 
	 *         When sending POST request to dvid, all block sizes and offsets
	 *         need to be integral multiples of blockSize. This is a convenience
	 *         function to modify integer arrays accordingly.
	 */
	public static int[] adaptToBlockSize( int[] input, final int[]blockSize )
	{
		for ( int d = 0; d < input.length; ++d )
		{
			int val = input[ d ];
			int bs = blockSize[ d ];
			int mod = val % bs;
			if ( mod > 0 )
				val += bs - mod;
			input[ d ] = val;
		}
		return input;
	}

	/**
	 * @param input
	 *            Long array corresponding to be adapted (will be modified).
	 * @param blockSize
	 *            Block size.
	 * @return Modified input.
	 * 
	 *         When sending POST request to dvid, all block sizes and offsets
	 *         need to be integral multiples of blockSize. This is a convenience
	 *         function to modify long arrays accordingly.
	 */
	public static long[] adaptToBlockSize( long[] input, final int[] blockSize )
	{
		for ( int d = 0; d < input.length; ++d )
		{
			long val = input[ d ];
			int bs = blockSize[d];
			long mod = val % bs;
			if ( mod > 0 )
				val += bs - mod;
			input[ d ] = val;
		}
		return input;
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

//		// this is for testing purposes, modify apiUrl, uuid and dataSet
//		// according to your needs
//		// if values received from server differ from input, these values will
//		// be printed to stdout
//		// otherwise no output
//
//		String url = "http://vm570.int.janelia.org:8080";
//		String uuid = "9c7cc44aa0544d33905ce82d153e2544";
//		String dataSet = "bigcat-test2";
//		
//		Repository repo = new Repository( url, uuid );
//
//		Random rng = new Random();
//
//		int[] dim = new int[] { 200, 200, 96 };
//		long[] longDim = new long[ dim.length ];
//		for ( int i = 0; i < longDim.length; i++ )
//			longDim[ i ] = dim[ i ];
//		ArrayImg< FloatType, FloatArray > ref = ArrayImgs.floats( longDim );
//		for ( FloatType r : ref )
//			r.set( rng.nextFloat() );
//
//		DvidImage32Writer writer = new DvidImage32Writer( url, uuid, dataSet );
//		int[] steps = new int[] { 200, 200, 32 };
//		int[] offset = new int[] { 0, 0, 0 };
//		Converter< FloatType, UnsignedLongType > converter = new Converter< FloatType, UnsignedLongType >()
//		{
//
//			@Override
//			public void convert( FloatType input, UnsignedLongType output )
//			{
//				output.set( Float.floatToIntBits( input.get() ) | 0l );
//			}};
//			
//		ConvertedRandomAccessibleInterval< FloatType, UnsignedLongType > refLong = 
//				new ConvertedRandomAccessibleInterval<FloatType,UnsignedLongType>( ref, converter, new UnsignedLongType() );
//		writer.writeImage( refLong, steps, offset );
//
//		// read image from dvid server
//		Node node = repo.getRootNode();
//		DatasetBlkImage  ds = new DatasetBlkImage ( node, "bigcat-test2" );
////		ArrayImg< UnsignedIntType, IntArray > target = ArrayImgs.unsignedInts( longDim );
//		ArrayImg< UnsignedLongType, ? > target = new ArrayImgFactory<UnsignedLongType>().create( longDim, new UnsignedLongType() );
//		ds.get( target, offset );
//		
//		ArrayCursor< UnsignedLongType > t = target.cursor();
//		for ( UnsignedLongType r : Views.flatIterable( refLong ) )
//		{
//			long comp = r.get();
//			long test = t.next().getIntegerLong();
//			if ( test != comp )
//				System.out.println( test + " " + comp );
//		}
//		System.out.println( "Done." );
	}

}
