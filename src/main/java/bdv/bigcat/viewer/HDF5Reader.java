package bdv.bigcat.viewer;

import java.io.IOException;
import java.util.ArrayList;

import bdv.bigcat.ui.ModalGoldenAngleSaturatedARGBStream;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import ch.systemsx.cisd.hdf5.IHDF5Reader;

public class HDF5Reader {

	protected static int setupId = 0;

	final static protected int[] cellDimensions = new int[] { 64, 64, 8 };
	final protected static VolatileGlobalCellCache cache = new VolatileGlobalCellCache( 1, 12 );

	/** color generator for composition of loaded segments and canvas */
	protected static ModalGoldenAngleSaturatedARGBStream colorStream;

	public static ArrayList<H5LabelMultisetSetupImageLoader> readLabels(final IHDF5Reader reader,
			final String labelDataset) throws IOException {

		/** loaded segments */
		final ArrayList<H5LabelMultisetSetupImageLoader> labels = new ArrayList<>();

		final H5LabelMultisetSetupImageLoader labelLoader = new H5LabelMultisetSetupImageLoader(
				reader,
				null,
				labelDataset,
				setupId++,
				cellDimensions,
				cache );
		labels.add(labelLoader);

		return labels;
	}

}
