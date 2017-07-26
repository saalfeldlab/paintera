package bdv.bigcat.viewer;

import java.io.IOException;

import bdv.bigcat.ui.highlighting.ModalGoldenAngleSaturatedHighlightingARGBStream;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Source;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import gnu.trove.set.hash.TLongHashSet;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

public class HDF5ConvertedLabelMultisetSourceSpec implements DatasetSpec< LabelMultisetType, VolatileLabelMultisetType >
{

	private final String path;

	private final String dataset;

	private final Converter< LabelMultisetType, ARGBType > converter;

	private final Converter< VolatileLabelMultisetType, ARGBType > vconverter;

	private final H5LabelMultisetSetupImageLoader loader;

	private final String name;

	private final VoxelDimensions voxelDimensions;

	public HDF5ConvertedLabelMultisetSourceSpec( final String path, final String dataset, final String name, final VoxelDimensions voxelDimensions, final int[] cellSize ) throws IOException
	{
		this( path, dataset, cellSize, name, voxelDimensions, defaultConverter() );
	}

	public HDF5ConvertedLabelMultisetSourceSpec( final String path, final String dataset, final int[] cellSize, final String name, final VoxelDimensions voxelDimensions, final Converter< LabelMultisetType, ARGBType > converter ) throws IOException
	{
		this( path, dataset, cellSize, name, voxelDimensions, converter, ( s, t ) -> {
			converter.convert( s.get(), t );
		} );
	}

	public HDF5ConvertedLabelMultisetSourceSpec( final String path, final String dataset, final int[] cellSize, final String name, final VoxelDimensions voxelDimensions, final Converter< LabelMultisetType, ARGBType > converter, final Converter< VolatileLabelMultisetType, ARGBType > vconverter ) throws IOException
	{
		super();
		this.path = path;
		this.dataset = dataset;
		this.name = name;
		this.voxelDimensions = voxelDimensions;
		this.converter = converter;
		this.vconverter = vconverter;

		final IHDF5Reader h5reader = HDF5Factory.open( path );
		this.loader = new H5LabelMultisetSetupImageLoader( h5reader, null, dataset, 0, cellSize, new VolatileGlobalCellCache( new SharedQueue( 8 ) ) );
	}

	@Override
	public Source< LabelMultisetType > getSource()
	{
		final int numMipMapLevels = this.loader.getMipmapResolutions().length;
		return null;
	}

	@Override
	public Source< VolatileLabelMultisetType > getViewerSource()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Converter< VolatileLabelMultisetType, ARGBType > getViewerConverter()
	{
		return vconverter;
	}

	public static Converter< LabelMultisetType, ARGBType > defaultConverter()
	{
		final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( new TLongHashSet() );
		return ( s, t ) -> {
			t.set( stream.argb( s.entrySet().iterator().next().getElement().id() ) );
		};
	}

}
