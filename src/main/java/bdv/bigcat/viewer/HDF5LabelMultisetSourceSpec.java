package bdv.bigcat.viewer;

import java.io.IOException;
import java.util.Iterator;

import bdv.bigcat.ui.ARGBStream;
import bdv.bigcat.ui.LabelMultisetSource;
import bdv.bigcat.ui.VolatileLabelMultisetSource;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.bigcat.viewer.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset.Entry;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.util.volatiles.SharedQueue;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

public class HDF5LabelMultisetSourceSpec implements DatasetSpec< LabelMultisetType, VolatileLabelMultisetType >
{

	private final ARGBStream stream;

	private final H5LabelMultisetSetupImageLoader loader;

	private final SelectedIds selectedIds = new SelectedIds();

	public HDF5LabelMultisetSourceSpec( final String path, final String dataset, final int[] cellSize ) throws IOException
	{
		super();
		this.stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( selectedIds );
		final IHDF5Reader h5reader = HDF5Factory.open( path );
		this.loader = new H5LabelMultisetSetupImageLoader( h5reader, null, dataset, 0, cellSize, new VolatileGlobalCellCache( new SharedQueue( 8 ) ) );
	}

	public SelectedIds getSelectedIds()
	{
		return selectedIds;
	}

	@Override
	public LabelMultisetSource getSource()
	{
		final LabelMultisetSource source = new LabelMultisetSource( 0, loader, stream );
		return source;
	}

	@Override
	public VolatileLabelMultisetSource getVolatileSource()
	{
		final VolatileLabelMultisetSource source = new VolatileLabelMultisetSource( 0, loader, stream );
		return source;
	}

	@Override
	public Converter< LabelMultisetType, ARGBType > getConverter()
	{
		return defaultConverter( stream );
	}

	@Override
	public Converter< VolatileLabelMultisetType, ARGBType > getVolatileConverter()
	{
		final Converter< LabelMultisetType, ARGBType > conv = defaultConverter( stream );
		return ( s, t ) -> {
			conv.convert( s.get(), t );
		};
	}

	public static Converter< LabelMultisetType, ARGBType > defaultConverter( final ARGBStream stream )
	{
		return ( s, t ) -> {
			final Iterator< Entry< Label > > it = s.entrySet().iterator();
			t.set( it.hasNext() ? stream.argb( it.next().getElement().id() ) : 0 );
		};
	}

}
