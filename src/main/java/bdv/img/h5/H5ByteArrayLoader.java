package bdv.img.h5;

import bdv.img.cache.CacheArrayLoader;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.img.basictypeaccess.volatiles.array.DirtyVolatileByteArray;
import net.imglib2.type.numeric.integer.UnsignedByteType;

/**
 * {@link CacheArrayLoader} for
 * Jan Funke's and other's h5 files
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class H5ByteArrayLoader implements CacheArrayLoader< DirtyVolatileByteArray >
{
	final private H5CellLoader< UnsignedByteType > loader;

	public H5ByteArrayLoader(
			final IHDF5Reader reader,
			final String dataset )
	{
		this.loader = new H5CellLoader<>( reader, dataset );
	}

	@Override
	public int getBytesPerElement()
	{
		return 1;
	}


	@Override
	public DirtyVolatileByteArray loadArray(
			final int timepoint,
			final int setup,
			final int level,
			final int[] dimensions,
			final long[] min ) throws InterruptedException
	{
		final byte[] data = new byte[ dimensions[ 2 ] * dimensions[ 1 ] * dimensions[ 0 ] ];
		final DirtyVolatileByteArray cellData = new DirtyVolatileByteArray( data, true );
		final SingleCellArrayImg< UnsignedByteType, DirtyVolatileByteArray > cell = new SingleCellArrayImg< UnsignedByteType, DirtyVolatileByteArray >( dimensions, min, cellData, cellData );
		final UnsignedByteType linkedType = new UnsignedByteType( cell );
		cell.setLinkedType( linkedType );
		loader.load( cell );

		return cellData;
	}
}
