package bdv.bigcat.ui;

import bdv.AbstractViewerSetupImgLoader;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.VolatileLabelMultisetType;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;

public class LabelMultisetSource extends AbstractLabelMultisetSource
{
	final private AbstractViewerSetupImgLoader< LabelMultisetType, VolatileLabelMultisetType > multisetImageLoader;

	public LabelMultisetSource(
			final int setupId,
			final AbstractViewerSetupImgLoader< LabelMultisetType, VolatileLabelMultisetType > multisetImageLoader,
			final ARGBStream argStream )
	{
		super( setupId, argStream );
		this.multisetImageLoader = multisetImageLoader;
	}

	final public AbstractViewerSetupImgLoader< LabelMultisetType, VolatileLabelMultisetType > getLoader()
	{
		return multisetImageLoader;
	}

	@Override
	public RandomAccessibleInterval< LabelMultisetType > getSource( final int t, final int level )
	{
		return multisetImageLoader.getImage( level );
	}

	@Override
	public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
	{
		transform.set( multisetImageLoader.getMipmapTransforms()[ level ] );
	}

	/**
	 * TODO Store this in a field
	 */
	@Override
	public int getNumMipmapLevels()
	{
		return multisetImageLoader.getMipmapResolutions().length;
	}
}
