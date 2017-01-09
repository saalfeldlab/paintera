package bdv.bigcat.ui;

import bdv.labels.labelset.VolatileLabelMultisetType;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.util.Pair;
import net.imglib2.view.RandomAccessiblePair;
import net.imglib2.view.Views;

public class ARGBConvertedLabelPairSource extends AbstractARGBConvertedLabelsSource
{
	final private RandomAccessiblePair< VolatileLabelMultisetType, LongType > source;
	final private Interval interval;
	final private AffineTransform3D[] sourceTransforms;

	public ARGBConvertedLabelPairSource(
			final int setupId,
			final RandomAccessiblePair< VolatileLabelMultisetType, LongType > source,
			final Interval interval,
			final AffineTransform3D[] sourceTransforms,
			final ARGBStream argbStream )
	{
		super( setupId, argbStream );
		this.source = source;
		this.interval = interval;
		this.sourceTransforms = sourceTransforms;
	}

	@Override
	public RandomAccessibleInterval< VolatileARGBType > getSource( final int t, final int level )
	{
		return Converters.convert(
				// cast necessary for java-8-openjdk-amd64, version 1.8.0_66-internal, vendor: Oracle Corporation
				// to prevent
				// [ERROR] reference to convert is ambiguous both
				// [ERROR] method <A,B>convert(net.imglib2.RandomAccessibleInterval<A>,net.imglib2.converter.Converter<? super A,? super B>,B) in net.imglib2.converter.Converters and
				// [ERROR] method <A,B>convert(net.imglib2.IterableInterval<A>,net.imglib2.converter.Converter<? super A,? super B>,B) in net.imglib2.converter.Converters match
				( RandomAccessibleInterval< Pair< VolatileLabelMultisetType, LongType > > )Views.interval( source, interval ),
				//Views.interval( source, interval ),
				new PairVolatileLabelMultisetLongARGBConverter( argbStream ),
				new VolatileARGBType() );
	}

	@Override
	public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
	{
		transform.set( sourceTransforms[ level ] );
	}

	/**
	 * TODO Store this in a field
	 */
	@Override
	public int getNumMipmapLevels()
	{
		return sourceTransforms.length;
	}
}
