package bdv.bigcat.viewer;

import java.io.IOException;

import bdv.bigcat.ui.ARGBConvertedLabelsSource;
import bdv.bigcat.ui.ARGBStream;
import bdv.bigcat.ui.highlighting.ModalGoldenAngleSaturatedHighlightingARGBStream;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.labels.labelset.LabelMultisetType;
import bdv.util.RandomAccessibleIntervalMipmapSource;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Source;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import gnu.trove.set.hash.TLongHashSet;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.converter.TypeIdentity;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;

public class HDF5LabelMultisetSourceSpec implements DatasetSpec< ARGBType, VolatileARGBType >
{

	private final ARGBStream stream;

	private final H5LabelMultisetSetupImageLoader loader;

	public HDF5LabelMultisetSourceSpec( final String path, final String dataset, final int[] cellSize ) throws IOException
	{
		this( path, dataset, cellSize, new ModalGoldenAngleSaturatedHighlightingARGBStream( new TLongHashSet() ) );
	}

	public HDF5LabelMultisetSourceSpec( final String path, final String dataset, final int[] cellSize, final ARGBStream stream ) throws IOException
	{
		super();
		this.stream = stream;
		final IHDF5Reader h5reader = HDF5Factory.open( path );
		this.loader = new H5LabelMultisetSetupImageLoader( h5reader, null, dataset, 0, cellSize, new VolatileGlobalCellCache( new SharedQueue( 8 ) ) );
	}

	@Override
	public Source< ARGBType > getSource()
	{
		final ARGBConvertedLabelsSource convertedSource = new ARGBConvertedLabelsSource( 0, loader, stream );
		final VoxelDimensions vd = convertedSource.getVoxelDimensions();
		final int numMipMapLevels = convertedSource.getNumMipmapLevels();
		final double[][] mipmapScales = new double[ numMipMapLevels ][];
		final RandomAccessibleInterval< ARGBType >[] imgs = new RandomAccessibleInterval[ numMipMapLevels ];

		for ( int level = 0;  level < numMipMapLevels; ++level ) {
			final RandomAccessibleInterval< VolatileARGBType > src = convertedSource.getSource( 0, level );
			imgs[ level ] = Converters.convert( src, ( s, t ) -> {
				t.set( s.get() );
			}, new ARGBType() );
			final AffineTransform3D affine = new AffineTransform3D();
			convertedSource.getSource( 0, level );
			mipmapScales[ level ] = new double[ affine.numDimensions() ];
			for ( int d = 0; d < affine.numDimensions(); ++d )
				mipmapScales[ level ][ d ] = affine.get( d, d );

		}

		return new RandomAccessibleIntervalMipmapSource<>( imgs, new ARGBType(), mipmapScales, vd, convertedSource.getName() );
	}

	@Override
	public ARGBConvertedLabelsSource getVolatileSource()
	{
		final ARGBConvertedLabelsSource convertedSource = new ARGBConvertedLabelsSource( 0, loader, stream );
		return convertedSource;
	}

	@Override
	public Converter< ARGBType, ARGBType > getConverter()
	{
		return new TypeIdentity<>();
	}

	@Override
	public Converter< VolatileARGBType, ARGBType > getVolatileConverter()
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
