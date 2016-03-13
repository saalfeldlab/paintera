package bdv.img.h5;

import bdv.img.cache.CacheArrayLoader;
import bdv.labels.labelset.VolatileLabelMultisetArray;

/**
 * {@link CacheArrayLoader} for
 * Jan Funke's and other's h5 files
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
abstract public class AbstractH5LabelMultisetArrayLoader implements CacheArrayLoader< VolatileLabelMultisetArray >
{
	protected VolatileLabelMultisetArray theEmptyArray;

	final protected String dataset;

	public AbstractH5LabelMultisetArrayLoader(
			final String dataset )
	{
		theEmptyArray = new VolatileLabelMultisetArray( 1, false );
		this.dataset = dataset;
	}

	/**
	 * Reuses the existing empty array if it already has the desired size.
	 */
	@Override
	public VolatileLabelMultisetArray emptyArray( final int[] dimensions )
	{
		int numEntities = 1;
		for ( int i = 0; i < dimensions.length; ++i )
			numEntities *= dimensions[ i ];
		if ( theEmptyArray.getCurrentStorageArray().length < numEntities )
			theEmptyArray = new VolatileLabelMultisetArray( numEntities, false );
		return theEmptyArray;
	}
}
