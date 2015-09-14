package bdv.img.dvid;

import java.io.IOException;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.util.BlockedInterval;
import bdv.util.dvid.DatasetBlk;
import bdv.util.dvid.DatasetBlkUint8;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class AbstractDvidImageWriter< T extends NumericType< T > >
{
	
	protected final DatasetBlk< T > dataset;
	
	protected final int[] blockSize;

	public AbstractDvidImageWriter( DatasetBlk< T > dataset, int[] blockSize )
	{
		this.dataset = dataset;
		this.blockSize = blockSize;
	}
	
	public AbstractDvidImageWriter( DatasetBlk< T > dataset ) throws JsonSyntaxException, JsonIOException, IOException
	{
		this( dataset, dataset.getBlockSize() );
	}

	/**
	 * @param image
	 *            Image to be stored in dvid server.
	 * @param iterationAxis
	 *            Along which axis to iterate.
	 * @param offset
	 *            Offset target position by offset.
	 *
	 *            Write image into data set. Calls
	 *            {@link DvidImage8Writer#writeImage(RandomAccessibleInterval, int, int[])}
	 *            with iterationAxis set to 2.
	 */
	public void writeImage(
			RandomAccessibleInterval< T > image,
			final int[] offset )
	{
		this.writeImage( image, 2, offset );
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
	 *            {@link DvidImage8Writer#writeImage(RandomAccessibleInterval, int, int[], int[])}
	 *            with iterationAxis set to 2.
	 */
	public void writeImage(
			RandomAccessibleInterval< T > image,
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
	 * @param offset
	 *            Offset target position by offset.
	 *
	 *            Write image into data set. Calls
	 *            {@link DvidImage8Writer#writeImage(RandomAccessibleInterval, int, int[], RealType)}
	 *            with borderExtension set to 0.
	 */
	public void writeImage(
			RandomAccessibleInterval< T > image,
			final int iterationAxis,
			final int[] offset )
	{
		T borderExtension = image.randomAccess().get().createVariable();
		borderExtension.setZero();
		this.writeImage( image, iterationAxis, offset, borderExtension );
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
	 *            {@link DvidImage8Writer#writeImage(RandomAccessibleInterval, int, int[], int[], RealType)}
	 *            with borderExtension set to 0.
	 */
	public void writeImage(
			RandomAccessibleInterval< T > image,
			final int iterationAxis,
			final int[] steps,
			final int[] offset )
	{
		T borderExtension = image.randomAccess().get().createVariable();
		borderExtension.setZero();
		this.writeImage( image, iterationAxis, steps, offset, borderExtension );
	}

	/**
	 * @param image
	 *            Image to be stored in dvid server.
	 * @param iterationAxis
	 *            Along which axis to iterate.
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
			RandomAccessibleInterval< T > image,
			final int iterationAxis,
			final int[] offset,
			T borderExtension )
	{
		this.writeImage( image, iterationAxis, this.blockSize, offset, borderExtension );
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
			RandomAccessibleInterval< T > image,
			final int iterationAxis,
			final int[] steps,
			final int[] offset,
			T borderExtension )
	{
		// realX ensures that realX[i] is integer multiple of blockSize
		long[] realDim = new long[ image.numDimensions() ];
		image.dimensions( realDim );
		adaptToBlockSize( realDim, this.blockSize );
		int[] realSteps = adaptToBlockSize( steps.clone(), this.blockSize );

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
		int[] withinBlockOffset = blockSize.clone();
		long min[] = new long[ withinBlockOffset.length ];
		long[] length = new long[ min.length ];
		int[] realOffset = new int[ min.length ];
		for ( int d = 0; d < withinBlockOffset.length; ++d )
		{
			int off = offset[ d ] % blockSize[ d ];
			realOffset[ d ] = offset[ d ] - off;
			withinBlockOffset[ d ] = off;
			min[ d ] = -off;
			length[ d ] = image.dimension( d ) + off;
		}
		IntervalView< T > shiftedImage = Views.offsetInterval( Views.extendValue( image, borderExtension ), min, length );
		BlockedInterval< T > blockedImage = BlockedInterval.createValueExtended( shiftedImage, stepSize, borderExtension );
		for ( int a = 0, aUnitIncrement = 0; a < shiftedImage.dimension( iterationAxis ); ++aUnitIncrement, a += realSteps[ iterationAxis ] )
		{
			IntervalView< RandomAccessibleInterval< T >> hs = 
					Views.hyperSlice( blockedImage, iterationAxis, aUnitIncrement );
			Cursor< RandomAccessibleInterval< T >> cursor = Views.flatIterable( hs ).cursor();
			while ( cursor.hasNext() )
			{
				RandomAccessibleInterval< T > block = cursor.next();
				int[] localOffset = realOffset.clone();
				localOffset[ iterationAxis ] += a;
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
	public void writeBlock(
			RandomAccessibleInterval< T > input,
			final int[] dims,
			final int[] offset ) throws IOException
	{
		// Offset and block dimensions must be integer multiples of
		// this.blockSize.
		// For now add the asserts, maybe make this method private/protected and
		// have
		// enclosing method take care of it.
		
		boolean isSingleBlock = true;

		for ( int d = 0; d < input.numDimensions(); ++d )
		{
			assert offset[ d ] % this.blockSize[ d ] == 0;
			assert input.dimension( d ) % this.blockSize[ d ] == 0;
			if ( input.dimension( d ) != this.blockSize[ d ] )
				isSingleBlock = false;
		}
		
		if ( isSingleBlock )
		{
			int[] position = new int[ offset.length ];
			for ( int d = 0; d < offset.length; ++d )
			{
				position[ d ] = offset[ d ] / blockSize[ d ];
			}
			dataset.writeBlock( input, position );
		}
		else
		{
			dataset.put( input, offset );
		}

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
}
