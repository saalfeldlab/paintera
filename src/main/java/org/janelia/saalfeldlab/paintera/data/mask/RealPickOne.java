package org.janelia.saalfeldlab.paintera.data.mask;

import java.util.function.Function;

import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.AbstractConvertedRealRandomAccess;
import net.imglib2.converter.AbstractConvertedRealRandomAccessible;
import net.imglib2.util.Triple;

public class RealPickOne<A, B, C, D> extends AbstractConvertedRealRandomAccessible<Triple<A, B, C>, D>
{

	public static interface RealPickAndConvert<A, B, C, D> extends Function<Triple<A, B, C>, D>
	{

		public RealPickAndConvert<A, B, C, D> copy();

	}

	private final RealPickAndConvert<A, B, C, D> pac;

	public RealPickOne(final RealRandomAccessible<Triple<A, B, C>> source, final RealPickAndConvert<A, B, C, D> pac)
	{
		super(source);
		this.pac = pac;
	}

	public static class RealPickOneAccess<A, B, C, D> extends AbstractConvertedRealRandomAccess<Triple<A, B, C>, D>
	{

		private final RealPickAndConvert<A, B, C, D> pac;

		public RealPickOneAccess(final RealRandomAccess<Triple<A, B, C>> source, final RealPickAndConvert<A, B, C, D> pac)
		{
			super(source);
			this.pac = pac;
		}

		@Override
		public D get()
		{
			return pac.apply(source.get());
		}

		@Override
		public AbstractConvertedRealRandomAccess<Triple<A, B, C>, D> copy()
		{
			return new RealPickOneAccess<>(source.copyRealRandomAccess(), pac.copy());
		}

	}

	@Override
	public AbstractConvertedRealRandomAccess<Triple<A, B, C>, D> realRandomAccess()
	{
		return new RealPickOneAccess<>(source.realRandomAccess(), pac.copy());
	}

	@Override
	public AbstractConvertedRealRandomAccess<Triple<A, B, C>, D> realRandomAccess(final RealInterval interval)
	{
		return realRandomAccess();
	}

}
