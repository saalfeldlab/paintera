package org.janelia.saalfeldlab.paintera.cache;

import net.imglib2.Interval;
import org.scijava.plugin.Plugin;

import java.util.Arrays;

@Plugin(type = DiscoverableMemoryUsage.class)
public class IntervalArrayMemoryUsage implements DiscoverableMemoryUsage<Interval[]>
{
	@Override
	public boolean isApplicable(final Object obj)
	{
		return obj instanceof Interval[];
	}

	@Override
	public long applyAsLong(final Interval[] intervalArray)
	{
		if (intervalArray.length == 0)
			return 0;

		final int dim = intervalArray[0].numDimensions();
		assert Arrays.stream(intervalArray).filter(interval -> interval.numDimensions() == dim).count() == intervalArray.length;

		final int minMaxArraysSize = 2 * dim * Long.BYTES;
		return intervalArray.length * minMaxArraysSize;
	}
}
