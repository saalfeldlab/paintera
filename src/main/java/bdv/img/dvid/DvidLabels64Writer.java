package bdv.img.dvid;

import java.io.IOException;
import java.util.Random;

import bdv.util.BlockedInterval;
import bdv.util.dvid.DatasetBlkLabel;
import bdv.util.dvid.Node;
import bdv.util.dvid.Repository;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 * 
 *         Write any {@link RandomAccessibleInterval} of type {@link RealType}
 *         into an existing dvid repository/dataset.
 *
 */
public class DvidLabels64Writer
{

	private final DatasetBlkLabel< UnsignedLongType > dataset;

	private final int blockSize;

	/**
	 * @param url
	 *            Url to dvid server in the form of http://hostname:port
	 * @param uuid
	 *            Uuid of repository within the dvid server specified by apiUrl
	 * @param dataSet
	 *            Name of the data set within the repository specified by uuid.
	 *            This data set must be of type labelblk.
	 * 
	 *            This calls
	 *            {@link DvidLabels64Writer#DvidLabels64ByteWriter(String, String, String, int)}
	 *            with a default block size of 32.
	 **/
	public DvidLabels64Writer( String url, String uuid, String dataSet )
	{
		this( url, uuid, dataSet, 32 );
	}

	/**
	 * @param url
	 *            Url to dvid server in the form of http://hostname:port
	 * @param uuid
	 *            Uuid of repository within the dvid server specified by apiUrl
	 * @param dataSet
	 *            Name of the data set within the repository specified by uuid.
	 *            This data set must be of type labelblk
	 * @param blockSize
	 *            Block size of the data set. Must suit block size stored in
	 *            dvid server.
	 */
	public DvidLabels64Writer( String url, String uuid, final String dataSetName, final int blockSize )
	{
		this( new DatasetBlkLabel< UnsignedLongType > ( new Repository( url, uuid ).getRootNode(), dataSetName ), blockSize );
	}
	
	public DvidLabels64Writer( DatasetBlkLabel< UnsignedLongType >  dataset, int blockSize )
	{
		this.dataset = dataset;
		this.blockSize = blockSize;
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
	 *            {@link DvidLabels64Writer#writeImage(RandomAccessibleInterval, int, int[], int[])}
	 *            with iterationAxis set to 2.
	 */
	public void writeImage(
			RandomAccessibleInterval< UnsignedLongType > image,
			final int[] steps,
			final int[] offset )
	{
		this.writeImage( image, 2, steps, offset );
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
	 *            {@link DvidLabels64Writer#writeImage(RandomAccessibleInterval, int, int[], int[], RealType)}
	 *            with borderExtension set to 0.
	 */
	public void writeImage(
			RandomAccessibleInterval< UnsignedLongType > image,
			final int iterationAxis,
			final int[] steps,
			final int[] offset )
	{
		this.writeImage( image, iterationAxis, steps, offset, new UnsignedLongType( 0l ) );
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
	public void writeImage(
			RandomAccessibleInterval< UnsignedLongType > image,
			final int iterationAxis,
			final int[] steps,
			final int[] offset,
			UnsignedLongType borderExtension )
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
		BlockedInterval< UnsignedLongType > blockedImage = BlockedInterval.createZeroExtended( image, stepSize );
		for ( int a = 0, aUnitIncrement = 0; a < image.dimension( iterationAxis ); ++aUnitIncrement, a += realSteps[ iterationAxis ] )
		{
			IntervalView< RandomAccessibleInterval< UnsignedLongType >> hs = 
					Views.hyperSlice( blockedImage, iterationAxis, aUnitIncrement );
			Cursor< RandomAccessibleInterval< UnsignedLongType >> cursor = Views.flatIterable( hs ).cursor();
			while ( cursor.hasNext() )
			{
				RandomAccessibleInterval< UnsignedLongType > block = cursor.next();
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
					this.writeBlock( block, dims, localOffset );
				}
				catch ( IOException e )
				{
					System.err.println( "Failed to write block: " + dataset.getRequestString( DatasetBlkLabel.getBlockRequestString( image, realSteps ) ) );
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
	public void writeBlock(
			RandomAccessibleInterval< UnsignedLongType > input,
			final int[] dims,
			final int[] offset ) throws IOException
	{
		// Offset and block dimensions must be integer multiples of
		// this.blockSize.
		// For now add the asserts, maybe make this method private/protected and
		// have
		// enclosing method take care of it.
		for ( int o : offset )
			assert o % this.blockSize == 0;

		for ( int d = 0; d < input.numDimensions(); ++d )
			assert input.dimension( d ) % this.blockSize == 0;

		dataset.put( input, offset );

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
	public static int[] adaptToBlockSize( int[] input, final int blockSize )
	{
		for ( int d = 0; d < input.length; ++d )
		{
			int val = input[ d ];
			int mod = val % blockSize;
			if ( mod > 0 )
				val += blockSize - mod;
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
	public static long[] adaptToBlockSize( long[] input, final int blockSize )
	{
		for ( int d = 0; d < input.length; ++d )
		{
			long val = input[ d ];
			long mod = val % blockSize;
			if ( mod > 0 )
				val += blockSize - mod;
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

		// this is for testing purposes, modify apiUrl, uuid and dataSet
		// according to your needs
		// if values received from server differ from input, these values will
		// be printed to stdout
		// otherwise no output

		String url = "http://vm570.int.janelia.org:8080";
		String uuid = "9c7cc44aa0544d33905ce82d153e2544";
		String dataSet = "bigcat-test2";
		
		Repository repo = new Repository( url, uuid );

		Random rng = new Random();

		int[] dim = new int[] { 200, 200, 96 };
		long[] longDim = new long[ dim.length ];
		for ( int i = 0; i < longDim.length; i++ )
			longDim[ i ] = dim[ i ];
		ArrayImg< FloatType, FloatArray > ref = ArrayImgs.floats( longDim );
		for ( FloatType r : ref )
			r.set( rng.nextFloat() );

		DvidLabels64Writer writer = new DvidLabels64Writer( url, uuid, dataSet, 32 );
		int[] steps = new int[] { 200, 200, 32 };
		int[] offset = new int[] { 0, 0, 0 };
		Converter< FloatType, UnsignedLongType > converter = new Converter< FloatType, UnsignedLongType >()
		{

			@Override
			public void convert( FloatType input, UnsignedLongType output )
			{
				output.set( Float.floatToIntBits( input.get() ) | 0l );
			}};
			
		ConvertedRandomAccessibleInterval< FloatType, UnsignedLongType > refLong = 
				new ConvertedRandomAccessibleInterval<FloatType,UnsignedLongType>( ref, converter, new UnsignedLongType() );
		writer.writeImage( refLong, steps, offset );

		// read image from dvid server
		Node node = repo.getRootNode();
		DatasetBlkLabel< UnsignedIntType >  ds = new DatasetBlkLabel< UnsignedIntType > ( node, "bigcat-test2" );
		ArrayImg< UnsignedIntType, IntArray > target = ArrayImgs.unsignedInts( longDim );
		ds.get( target, offset );
		
		ArrayCursor< UnsignedIntType > t = target.cursor();
		for ( UnsignedLongType r : Views.flatIterable( refLong ) )
		{
			long comp = r.get();
			long test = t.next().getIntegerLong();
			if ( test != comp )
				System.out.println( test + " " + comp );
		}
		System.out.println( "Done." );
	}

}
