package bdv.bigcat.viewer.viewer3d.util;

import java.io.IOException;
import java.util.ArrayList;

import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import ch.systemsx.cisd.hdf5.IHDF5Reader;

public class HDF5Reader
{
	protected static int setupId = 0;

	final static protected int[] cellDimensions = new int[] { 64, 64, 8 };

	public static ArrayList< H5LabelMultisetSetupImageLoader > readLabels( final IHDF5Reader reader,
			final String labelDataset ) throws IOException
	{
		/** loaded segments */
		final ArrayList< H5LabelMultisetSetupImageLoader > labels = new ArrayList<>();

		final H5LabelMultisetSetupImageLoader labelLoader = new H5LabelMultisetSetupImageLoader( reader, null,
				labelDataset, setupId++, cellDimensions, new VolatileGlobalCellCache( 1, 10 ) );
		labels.add( labelLoader );

		return labels;
	}
}
