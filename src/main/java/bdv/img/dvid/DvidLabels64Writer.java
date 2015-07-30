package bdv.img.dvid;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Random;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import bdv.util.BlockedInterval;
import bdv.util.Constants;
import bdv.util.DvidLabelBlkURL;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 *
 *         Write any {@link RandomAccessibleInterval} of type {@link UnsignedLongType}
 *         into an existing dvid repository/dataset.
 *
 */
public class DvidLabels64Writer
{

	private final String apiUrl;

	private final String uuid;

	private final String dataSet;

	private final int blockSize;

	/**
	 * @param apiUrl
	 *            Url to dvid api in the form of http://hostname:port/api
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
	public DvidLabels64Writer( final String apiUrl, final String uuid, final String dataSet )
	{
		this( apiUrl, uuid, dataSet, 32 );
	}

	/**
	 * @param apiUrl
	 *            Url to dvid api in the form of http://hostname:port/api
	 * @param uuid
	 *            Uuid of repository within the dvid server specified by apiUrl
	 * @param dataSet
	 *            Name of the data set within the repository specified by uuid.
	 *            This data set must be of type labelblk
	 * @param blockSize
	 *            Block size of the data set. Must suit block size stored in
	 *            dvid server.
	 */
	public DvidLabels64Writer( final String apiUrl, final String uuid, final String dataSet, final int blockSize )
	{
		super();
		this.apiUrl = apiUrl;
		this.uuid = uuid;
		this.dataSet = dataSet;
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
			final RandomAccessibleInterval< UnsignedLongType > image,
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
	 *            {@link DvidLabels64Writer#writeImage(RandomAccessibleInterval, int, int[], int[], UnsignedLongType)}
	 *            with borderExtension set to 0.
	 */
	public void writeImage(
			final RandomAccessibleInterval< UnsignedLongType > image,
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
			final RandomAccessibleInterval< UnsignedLongType > image,
			final int iterationAxis,
			final int[] steps,
			final int[] offset,
			final UnsignedLongType borderExtension )
	{
		// realX ensures that realX[i] is integer multiple of blockSize
		final long[] realDim = new long[ image.numDimensions() ];
		image.dimensions( realDim );
		adaptToBlockSize( realDim, this.blockSize );
		final int[] realSteps = adaptToBlockSize( steps.clone(), this.blockSize );
		final int[] realOffset = adaptToBlockSize( offset.clone(), this.blockSize );

		// For now, assume always that data is in [0,1,2] == "xyz" format.
		// Do we need flexibility to allow for different orderings?
		final int[] dims = new int[ image.numDimensions() ];
		for ( int d = 0; d < image.numDimensions(); ++d )
			dims[ d ] = d;

		// stepSize as long[], needed for BlockedInterval
		final long[] stepSize = new long[ image.numDimensions() ];
		for ( int i = 0; i < stepSize.length; i++ )
			stepSize[ i ] = realSteps[ i ];

		// Create BlockedInterval that allows for flat iteration and intuitive
		// hyperslicing.
		// Go along iterationAxis and hyperslice, then iterate over each
		// hyperslice.
		final BlockedInterval< UnsignedLongType > blockedImage = BlockedInterval.createZeroExtended( image, stepSize );
		for ( int a = 0, aUnitIncrement = 0; a < image.dimension( iterationAxis ); ++aUnitIncrement, a += realSteps[ iterationAxis ] )
		{
			final IntervalView< RandomAccessibleInterval< UnsignedLongType >> hs =
					Views.hyperSlice( blockedImage, iterationAxis, aUnitIncrement );
			final Cursor< RandomAccessibleInterval< UnsignedLongType >> cursor = Views.flatIterable( hs ).cursor();
			while ( cursor.hasNext() )
			{
				final RandomAccessibleInterval< UnsignedLongType > block = cursor.next();
				final int[] localOffset = realOffset.clone();
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
				catch ( final IOException e )
				{
					System.err.println( "Failed to write block: " + DvidLabelBlkURL.makeRawString( apiUrl, uuid, dataSet, dims, realSteps, localOffset ) );
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
			final RandomAccessibleInterval< UnsignedLongType > input,
			final int[] dims,
			final int[] offset ) throws IOException
	{
		// Offset and block dimensions must be integer multiples of
		// this.blockSize.
		// For now add the asserts, maybe make this method private/protected and
		// have
		// enclosing method take care of it.
		for ( final int o : offset )
			assert o % this.blockSize == 0;

		for ( int d = 0; d < input.numDimensions(); ++d )
			assert input.dimension( d ) % this.blockSize == 0;

		// Size of the block to be written to dvid.
		final int[] size = new int[ input.numDimensions() ];

		for ( int d = 0; d < size.length; ++d )
		{
			final int dim = ( int ) input.dimension( d );
			size[ d ] = dim;
		}

		// Create URL and open connection.
		final String urlString = DvidLabelBlkURL.makeRawString( this.apiUrl, this.uuid, dataSet, dims, size, offset );
		final URL url = new URL( urlString );

		final HttpURLConnection connection = ( HttpURLConnection ) url.openConnection();
		connection.setDoOutput( true );
		connection.setRequestMethod( Constants.POST );

		connection.setRequestProperty( "Content-Type", "application/octet-stream" );

		// Write data.
		final OutputStream stream = connection.getOutputStream();
		final DataOutputStream writer = new DataOutputStream( stream );
		for ( final UnsignedLongType p : Views.flatIterable( input ) )
			writer.writeLong( p.get() );

		writer.flush();
		writer.close();

		connection.disconnect();
		final int response = connection.getResponseCode();

		if ( response != 200 )
			throw new IOException( "POST request failed with response = " + response + " != 200 for " + urlString );

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
	public static int[] adaptToBlockSize( final int[] input, final int blockSize )
	{
		for ( int d = 0; d < input.length; ++d )
		{
			int val = input[ d ];
			final int mod = val % blockSize;
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
	public static long[] adaptToBlockSize( final long[] input, final int blockSize )
	{
		for ( int d = 0; d < input.length; ++d )
		{
			long val = input[ d ];
			final long mod = val % blockSize;
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
	public static void main( final String[] args ) throws IOException
	{

		// this is for testing purposes, modify apiUrl, uuid and dataSet
		// according to your needs
		// if values received from server differ from input, these values will
		// be printed to stdout
		// otherwise no output

		final String apiUrl = "http://vm570.int.janelia.org:8080/api";
		final String uuid = "9c7cc44aa0544d33905ce82d153e2544";
		final String dataSet = "bigcat-test2";

		final Random rng = new Random();

		final int[] dim = new int[] { 200, 200, 96 };
		final long[] longDim = new long[ dim.length ];
		for ( int i = 0; i < longDim.length; i++ )
			longDim[ i ] = dim[ i ];
		final ArrayImg< FloatType, FloatArray > ref = ArrayImgs.floats( longDim );
		for ( final FloatType r : ref )
			r.set( rng.nextFloat() );

		int numPixels = 1;
		for ( int d = 0; d < dim.length; ++d )
			numPixels *= dim[ d ];

		final DvidLabels64Writer writer = new DvidLabels64Writer( apiUrl, uuid, dataSet, 32 );
		final int[] steps = new int[] { 200, 200, 32 };
		final int[] offset = new int[] { 0, 0, 0 };
		final Converter< FloatType, UnsignedLongType > converter = new Converter< FloatType, UnsignedLongType >()
		{

			@Override
			public void convert( final FloatType input, final UnsignedLongType output )
			{
				output.set( Float.floatToIntBits( input.get() ) | 0l );
			}};

		final ConvertedRandomAccessibleInterval< FloatType, UnsignedLongType > refLong =
				new ConvertedRandomAccessibleInterval<FloatType,UnsignedLongType>( ref, converter, new UnsignedLongType() );
		writer.writeImage( refLong, steps, offset );

		// read image from dvid server
		final String urlString = DvidLabelBlkURL.makeRawString( apiUrl, uuid, dataSet, new int[] { 0, 1, 2 }, dim, new int[ 3 ] );
		final URL url = new URL( urlString );
		final HttpURLConnection connection = ( HttpURLConnection ) url.openConnection();
		final InputStream in = connection.getInputStream();

		final byte[] bytes = new byte[ numPixels * Long.BYTES ];

		int off = 0;
		for ( int l = in.read( bytes, off, bytes.length ); l > 0 && off + l < bytes.length; off += l, l = in.read( bytes, off, bytes.length - off ) );
		in.close();
		final ByteBuffer bb = ByteBuffer.wrap( bytes );
		for ( final UnsignedLongType r : Views.flatIterable( refLong ) )
		{
			final long comp = r.get();
			final long test = bb.getLong();
			if ( test != comp )
				System.out.println( test + " " + comp );
		}
		System.out.println( "Done." );
	}

}
