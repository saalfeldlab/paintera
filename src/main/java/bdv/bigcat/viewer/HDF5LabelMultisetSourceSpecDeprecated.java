package bdv.bigcat.viewer;

import java.io.IOException;

import bdv.bigcat.ui.ARGBConvertedLabelsSource;
import bdv.bigcat.ui.ARGBConvertedLabelsSourceNonVolatile;
import bdv.bigcat.ui.ARGBStream;
import bdv.bigcat.ui.highlighting.ModalGoldenAngleSaturatedHighlightingARGBStream;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.labels.labelset.LabelMultisetType;
import bdv.util.volatiles.SharedQueue;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;

public class HDF5LabelMultisetSourceSpecDeprecated implements DatasetSpec< ARGBType, VolatileARGBType >
{

	private final ARGBStream stream;

	private final H5LabelMultisetSetupImageLoader loader;

	public HDF5LabelMultisetSourceSpecDeprecated( final String path, final String dataset, final int[] cellSize ) throws IOException
	{
		this( path, dataset, cellSize, new ModalGoldenAngleSaturatedHighlightingARGBStream( new TLongHashSet() ) );
	}

	public HDF5LabelMultisetSourceSpecDeprecated( final String path, final String dataset, final int[] cellSize, final ARGBStream stream ) throws IOException
	{
		super();
		this.stream = stream;
		final IHDF5Reader h5reader = HDF5Factory.open( path );
		this.loader = new H5LabelMultisetSetupImageLoader( h5reader, null, dataset, 0, cellSize, new VolatileGlobalCellCache( new SharedQueue( 8 ) ) );
	}

	@Override
	public ARGBConvertedLabelsSourceNonVolatile getSource()
	{
		final ARGBConvertedLabelsSourceNonVolatile convertedSource = new ARGBConvertedLabelsSourceNonVolatile( 0, loader, stream );
		return convertedSource;
	}

	@Override
	public ARGBConvertedLabelsSource getViewerSource()
	{
		final ARGBConvertedLabelsSource convertedSource = new ARGBConvertedLabelsSource( 0, loader, stream );
		return convertedSource;
	}

	@Override
	public Converter< VolatileARGBType, ARGBType > getViewerConverter()
	{
		return ( s, t ) -> t.set( s.get() );
	}

	public static Converter< LabelMultisetType, ARGBType > defaultConverter()
	{
		final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( new TLongHashSet() );
		return ( s, t ) -> {
			t.set( stream.argb( s.entrySet().iterator().next().getElement().id() ) );
		};
	}

}
