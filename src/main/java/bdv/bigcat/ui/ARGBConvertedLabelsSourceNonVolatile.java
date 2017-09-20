package bdv.bigcat.ui;

import bdv.AbstractViewerSetupImgLoader;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.VolatileLabelMultisetType;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;

public class ARGBConvertedLabelsSourceNonVolatile extends AbstractARGBConvertedLabelsSourceNonVolatile
{
	final private AbstractViewerSetupImgLoader< LabelMultisetType, VolatileLabelMultisetType > multisetImageLoader;

	public ARGBConvertedLabelsSourceNonVolatile(
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
	public RandomAccessibleInterval< ARGBType > getSource( final int t, final int level )
	{
		return Converters.convert(
				multisetImageLoader.getImage( t, level ),
				new VolatileLabelMultisetARGBConverterNonVolatile( argbStream ),
				new ARGBType() );
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
