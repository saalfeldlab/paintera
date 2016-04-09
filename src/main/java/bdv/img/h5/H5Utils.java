package bdv.img.h5;

import static bdv.img.hdf5.Util.reorder;

import java.io.File;

import ch.systemsx.cisd.base.mdarray.MDLongArray;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5LongReader;
import ch.systemsx.cisd.hdf5.IHDF5LongWriter;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.hdf5.IHDF5Writer;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.cell.CellImg;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

/**
 * Utility methods for simple HDF5 files.
 * 
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class H5Utils
{
	static public void cropCellDimensions(
			final Dimensions sourceDimensions,
			final long[] offset,
			final int[] cellDimensions,
			final long[] croppedCellDimensions )
	{
		final int n = sourceDimensions.numDimensions();
		for ( int d = 0; d < n; ++d )
			croppedCellDimensions[ d ] = Math.min( cellDimensions[ d ], sourceDimensions.dimension( d ) - offset[ d ] );
	}
	
	/**
	 * Save a {@link RandomAccessibleInterval} of {@link LongType} into an HDF5
	 * uint64 dataset.
	 * 
	 * @param source
	 * @param file
	 * @param dataset
	 * @param cellDimensions
	 */
	static public void saveUnsignedLong(
			final RandomAccessibleInterval< LongType > source,
			final File file,
			final String dataset,
			final int[] cellDimensions )
	{
		final int n = source.numDimensions();
		final long[] dimensions = Intervals.dimensionsAsLongArray( source );
		final IHDF5Writer writer = HDF5Factory.open( file );
		final IHDF5LongWriter uint64Writer = writer.uint64();
		uint64Writer.createMDArray(
				dataset,
				reorder( dimensions ),
				reorder( cellDimensions ) );
		
		final long[] offset = new long[ n ];
		final long[] sourceCellDimensions = new long[ n ];
		for ( int d = 0; d < n; )
		{
			cropCellDimensions( source, offset, cellDimensions, sourceCellDimensions );
			final RandomAccessibleInterval< LongType > sourceBlock = Views.offsetInterval( source, offset, sourceCellDimensions );
			final MDLongArray targetCell = new MDLongArray( reorder( sourceCellDimensions ) );
			int i = 0;
			for ( final LongType t : Views.flatIterable( sourceBlock ) )
				targetCell.set( t.get(), i++ );
			
			uint64Writer.writeMDArrayBlockWithOffset( dataset, targetCell, reorder( offset ) );
			
			for ( d = 0; d < n; ++d )
			{
				offset[ d ] += cellDimensions[ d ];
				if ( offset[ d ] < source.dimension( d ) )
					break;
				else
					offset[ d ] = 0;
			}
			
//			System.out.println( Util.printCoordinates( offset ) );
		}
		
		writer.close();
	}
	
	/**
	 * Load an HDF5 uint64 dataset into a {@link CellImg} of {@link LongType}.
	 * 
	 * @param file
	 * @param dataset
	 * @param cellDimensions
	 */
	static public CellImg< LongType, ?, ? > loadUnsignedLong(
			final File file,
			final String dataset,
			final int[] cellDimensions )
	{
		final IHDF5Reader reader = HDF5Factory.openForReading( file );
		final IHDF5LongReader uint64Reader = reader.uint64();
		
		final long[] dimensions = reorder( reader.object().getDimensions( dataset ) );
		final int n = dimensions.length;

		final CellImg< LongType, ?, ? > target = new CellImgFactory< LongType >( cellDimensions ).create( dimensions, new LongType() );
				
		final long[] offset = new long[ n ];
		final long[] targetCellDimensions = new long[ n ];
		for ( int d = 0; d < n; )
		{
			cropCellDimensions( target, offset, cellDimensions, targetCellDimensions );
			final RandomAccessibleInterval< LongType > targetBlock = Views.offsetInterval( target, offset, targetCellDimensions );
			final MDLongArray targetCell = uint64Reader.readMDArrayBlockWithOffset(
					dataset,
					Util.long2int( reorder( targetCellDimensions ) ),
					reorder( offset ) );
					
			int i = 0;
			for ( final LongType t : Views.flatIterable( targetBlock ) )
				t.set( targetCell.get( i++ ) );
			
			for ( d = 0; d < n; ++d )
			{
				offset[ d ] += cellDimensions[ d ];
				if ( offset[ d ] < dimensions[ d ] )
					break;
				else
					offset[ d ] = 0;
			}
			
//			System.out.println( Util.printCoordinates( offset ) );
		}
		reader.close();
		
		return target;
	}
}
