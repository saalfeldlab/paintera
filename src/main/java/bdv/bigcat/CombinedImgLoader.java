package bdv.bigcat;

import java.util.HashMap;

import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.img.cache.VolatileGlobalCellCache;

/**
 * {@link ViewerImgLoader} for multiple
 * {@link ViewerSetupImgLoader ViewerSetupImgLoaders} that share a single
 * {@link Cache}.
 *
 * @author Tobias Pietzsch <tobias.pietzsch@gmail.com>
 */
public class CombinedImgLoader implements ViewerImgLoader
{
	static public class SetupIdAndLoader
	{
		public final int setupId;

		public final ViewerSetupImgLoader< ?, ? > loader;

		public SetupIdAndLoader( final int setupId, final ViewerSetupImgLoader< ?, ? > loader )
		{
			this.setupId = setupId;
			this.loader = loader;
		}

		public static CombinedImgLoader.SetupIdAndLoader setupIdAndLoader( final int setupId, final ViewerSetupImgLoader< ?, ? > loader )
		{
			return new SetupIdAndLoader( setupId, loader );
		}
	}

	private final HashMap< Integer, ViewerSetupImgLoader< ?, ? > > setupImgLoaders;

	final VolatileGlobalCellCache cache;

	public CombinedImgLoader( final CombinedImgLoader.SetupIdAndLoader... loaders )
	{
		setupImgLoaders = new HashMap< Integer, ViewerSetupImgLoader< ?, ? > >();
		int maxNumLevels = 1;
		for ( final CombinedImgLoader.SetupIdAndLoader il : loaders )
		{
			maxNumLevels = Math.max( maxNumLevels, il.loader.numMipmapLevels() );
			setupImgLoaders.put( il.setupId, il.loader );
		}

		cache = new VolatileGlobalCellCache( maxNumLevels, 10 );
	}

	@Override
	public VolatileGlobalCellCache getCacheControl()
	{
		return cache;
	}

	@Override
	public ViewerSetupImgLoader< ?, ? > getSetupImgLoader( final int setupId )
	{
		return setupImgLoaders.get( setupId );
	}
}