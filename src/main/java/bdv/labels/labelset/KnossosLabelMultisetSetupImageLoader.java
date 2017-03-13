package bdv.labels.labelset;

import bdv.AbstractCachedViewerSetupImgLoader;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.knossos.AbstractKnossosImageLoader;
import bdv.img.knossos.AbstractKnossosImageLoader.KnossosConfig;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * Loader for uint8 volumes stored in the KNOSSOS format
 *
 * http://knossostool.org/
 *
 * Blocks of 128^3 voxels (fill with zero if smaller) uint64 voxels in network
 * byte order from left top front to right bottom rear,
 * index = z 128<sup>2</sup> + y 128 + x
 * naming convention
 *
 * x%1$d/y%2$d/z%3$d/%4$s_x%1$d_y%2$d_z%3$d.raw
 * with arguments
 *
 * (1) x / 128
 * (2) y / 128
 * (3) z / 128
 * (4) name
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class KnossosLabelMultisetSetupImageLoader
	extends AbstractCachedViewerSetupImgLoader< LabelMultisetType, VolatileLabelMultisetType, VolatileLabelMultisetArray >
{
	final protected KnossosConfig config;

	public KnossosLabelMultisetSetupImageLoader(
			final int setupId,
			final String configUrl,
			final String urlFormat,
			final VolatileGlobalCellCache cache )
	{
		super(
				setupId,
				new long[][]{ { config.width, config.height, config.depth } },
				new int[][]{ { 128, 128, 1 } },
				new double[][]{ { config.scaleX, config.scaleY, config.scaleZ } },
				LabelMultisetType.type,
				VolatileLabelMultisetType.type,
				new KnossosVolatileLabelsMultisetArrayLoader(
						config.baseUrl,
						urlFormat,
						config.experimentName,
						config.format ),
				cache );
		this.setupId = setupId;

		config = AbstractKnossosImageLoader.tryFetchConfig( configUrl, 20 );

		dimension = new long[] { config.width, config.height, config.depth };

		resolution =

		mipmapTransform = new AffineTransform3D();

		mipmapTransform.set( config.scaleX, 0, 0 );
		mipmapTransform.set( config.scaleY, 1, 1 );
		mipmapTransform.set( config.scaleZ, 2, 2 );

		loader =
	}

	public void setCache( final VolatileGlobalCellCache cache )
	{
		this.cache = cache;
	}

	static public class MultisetSource
	{
		private final RandomAccessibleInterval< LabelMultisetType >[] currentSources;

		private final KnossosLabelMultisetSetupImageLoader multisetImageLoader;

		private int currentTimePointIndex;

		@SuppressWarnings( "unchecked" )
		public MultisetSource( final KnossosLabelMultisetSetupImageLoader multisetImageLoader )
		{
			this.multisetImageLoader = multisetImageLoader;
			final int numMipmapLevels = multisetImageLoader.numMipmapLevels();
			currentSources = new RandomAccessibleInterval[ numMipmapLevels ];
			currentTimePointIndex = -1;
		}

		private void loadTimepoint( final int timepointIndex )
		{
			currentTimePointIndex = timepointIndex;
			for ( int level = 0; level < currentSources.length; level++ )
				currentSources[ level ] = multisetImageLoader.getImage( timepointIndex, level );
		}

		public synchronized RandomAccessibleInterval< LabelMultisetType > getSource( final int t, final int level )
		{
			if ( t != currentTimePointIndex )
				loadTimepoint( t );
			return currentSources[ level ];
		}
	}

	public int getSetupId()
	{
		return setupId;
	};
}
