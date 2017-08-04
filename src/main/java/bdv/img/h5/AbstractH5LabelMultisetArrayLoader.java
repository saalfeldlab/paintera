package bdv.img.h5;

import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.EmptyArrayCreator;
import bdv.labels.labelset.LongMappedAccess;
import bdv.labels.labelset.LongMappedAccessData;
import bdv.labels.labelset.VolatileLabelMultisetArray;
import ch.systemsx.cisd.hdf5.IHDF5IntReader;
import ch.systemsx.cisd.hdf5.IHDF5Reader;

/**
 * {@link CacheArrayLoader} for
 * Jan Funke's and other's h5 files
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
abstract public class AbstractH5LabelMultisetArrayLoader implements CacheArrayLoader< VolatileLabelMultisetArray >
{
	protected VolatileLabelMultisetArray theEmptyArray;

	protected final IHDF5IntReader scaleReader;

	final protected String dataset;

	public AbstractH5LabelMultisetArrayLoader(
			final IHDF5Reader scaleReader,
			final String dataset )
	{
		theEmptyArray = new VolatileLabelMultisetArray( 1, false );
		this.scaleReader = ( scaleReader == null ) ? null : scaleReader.uint32();
		this.dataset = dataset;
	}

	abstract protected VolatileLabelMultisetArray loadArrayLevel0(
			final int[] dimensions,
			final long[] min ) throws InterruptedException;

	@Override
	public VolatileLabelMultisetArray loadArray(
			final int timepoint,
			final int setup,
			final int level,
			final int[] dimensions,
			final long[] min ) throws InterruptedException
	{
		if ( level == 0 )
			return loadArrayLevel0( dimensions, min );

		final String listsPath = String.format( "l%02d/z%05d/y%05d/x%05d/lists", level, min[ 2 ], min[ 1 ], min[ 0 ] );
		final String dataPath = String.format( "l%02d/z%05d/y%05d/x%05d/data", level, min[ 2 ], min[ 1 ], min[ 0 ] );

		final int[] offsets = scaleReader.readMDArray( dataPath ).getAsFlatArray();
		final int[] lists = scaleReader.readArray( listsPath );
		final LongMappedAccessData listData = LongMappedAccessData.factory.createStorage( lists.length * 4 );
		final LongMappedAccess access = listData.createAccess();
		for ( int i = 0; i < lists.length; ++i )
			access.putInt( lists[ i ], i * 4 );
		return new VolatileLabelMultisetArray( offsets, listData, 0, true );
	}

	@Override
	public EmptyArrayCreator< VolatileLabelMultisetArray > getEmptyArrayCreator()
	{
		return VolatileLabelMultisetArray.emptyArrayCreator;
	}
}
