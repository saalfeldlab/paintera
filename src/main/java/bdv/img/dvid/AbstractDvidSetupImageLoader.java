package bdv.img.dvid;

import java.io.IOException;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.AbstractCachedViewerSetupImgLoader;
import bdv.ViewerImgLoader;
import bdv.cache.CacheControl;
import bdv.img.SetCache;
import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.util.JsonHelper;
import net.imglib2.Volatile;
import net.imglib2.img.basictypeaccess.volatiles.VolatileAccess;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;

/**
 * {@link AbstractDvidSetupImgLoader} for
 * <a href= "http://emdata.janelia.org/api/help/">DVID</a>.
 *
 * Loads general properties from DVID and sets up final parameters.  This
 * abstract implementation works for sources with
 * <ul>
 * <li>a single scale level</li>
 * <li>a single loader</li>
 * </ul>
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class AbstractDvidSetupImageLoader< T extends NativeType< T > , V extends Volatile< T > & NativeType< V >, A extends VolatileAccess >
	extends AbstractCachedViewerSetupImgLoader< T, V, A >
	implements ViewerImgLoader, SetCache
{
	static protected class ConstructorParameters {
		protected final String apiUrl;
		protected final String nodeId;
		protected final String dataInstanceId;
		protected final long[] dimensions;
		protected final int[] cellDimensions;
		protected final double[] resolutions;
		protected final AffineTransform3D mipmapTransform;

		public ConstructorParameters(
				final String apiUrl,
				final String nodeId,
				final String dataInstanceId ) throws JsonSyntaxException, JsonIOException, IOException
		{
			this.apiUrl = apiUrl;
			this.nodeId = nodeId;
			this.dataInstanceId = dataInstanceId;

			final Grayscale8DataInstance dataInstance =
					JsonHelper.fetch(
							apiUrl + "/node/" + nodeId + "/" + dataInstanceId + "/info",
							Grayscale8DataInstance.class );

			dimensions = new long[]{
					dataInstance.Extended.MaxPoint[ 0 ] - dataInstance.Extended.MinPoint[ 0 ],
					dataInstance.Extended.MaxPoint[ 1 ] - dataInstance.Extended.MinPoint[ 1 ],
					dataInstance.Extended.MaxPoint[ 2 ] - dataInstance.Extended.MinPoint[ 2 ] };

			resolutions = new double[]{
					dataInstance.Extended.VoxelSize[ 0 ],
					dataInstance.Extended.VoxelSize[ 1 ],
					dataInstance.Extended.VoxelSize[ 2 ] };

			mipmapTransform = new AffineTransform3D();

			mipmapTransform.set( 1, 0, 0 );
			mipmapTransform.set( 1, 1, 1 );
			mipmapTransform.set( 1, 2, 2 );

			cellDimensions = dataInstance.Extended.BlockSize;
		}
	}

	protected AbstractDvidSetupImageLoader(
			final int setupId,
			final T t,
			final V v,
			final ConstructorParameters parameters,
			final CacheArrayLoader< A > loader ) throws JsonSyntaxException, JsonIOException, IOException
	{
		super(
				setupId,
				new long[][]{ parameters.dimensions },
				new int[][]{ parameters.cellDimensions },
				new double[][]{ parameters.resolutions },
				t,
				v,
				loader,
				new VolatileGlobalCellCache( 1, 10 ) );
	}

	@Override
	public void setCache( final VolatileGlobalCellCache cache )
	{
		this.cache = cache;
	}

	@Override
	public AbstractDvidSetupImageLoader< T, V, A > getSetupImgLoader( int setupId )
	{
		return this;
	}

	@Override
	public CacheControl getCacheControl()
	{
		return cache;
	}
}
