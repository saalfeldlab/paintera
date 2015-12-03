package bdv.img.janh5;

import java.io.IOException;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.img.cache.Cache;
import ch.systemsx.cisd.hdf5.IHDF5Reader;

/**
 * {@link ViewerImgLoader} for
 * Jan Funke's h5 files
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class JanH5FloatImageLoader extends JanH5FloatSetupImageLoader
		implements ViewerImgLoader
{
	public JanH5FloatImageLoader(
			final IHDF5Reader reader,
			final String dataset,
			final int setupId,
			final int[] blockDimensions ) throws JsonSyntaxException, JsonIOException, IOException
	{
		super( reader, dataset, 0, blockDimensions );
	}

	@Override
	public ViewerSetupImgLoader< ?, ? > getSetupImgLoader( final int setupId )
	{
		return this;
	}

	@Override
	public Cache getCache()
	{
		return cache;
	}

}
