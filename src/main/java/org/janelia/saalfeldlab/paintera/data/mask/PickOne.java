package org.janelia.saalfeldlab.paintera.data.mask;

import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.AbstractConvertedRealRandomAccess;
import net.imglib2.converter.AbstractConvertedRealRandomAccessible;
import org.janelia.saalfeldlab.net.imglib2.util.Triple;

import java.util.function.Function;

public class PickOne<A, B, C, D> extends AbstractConvertedRealRandomAccessible<Triple<A, B, C>, D> {

	public interface PickAndConvert<A, B, C, D> extends Function<Triple<A, B, C>, D> {

		PickAndConvert<A, B, C, D> copy();

		default PickAndConvert<A, B, C, D> copyWithDifferentNumOccurences(final int numOccurrences) {

			return copy();
		}

	}

	private final PickAndConvert<A, B, C, D> pac;

	public PickOne(final RealRandomAccessible<Triple<A, B, C>> source, final PickAndConvert<A, B, C, D> pac) {

		super(source);
		this.pac = pac;
	}

	public static class PickOneAccess<A, B, C, D> extends AbstractConvertedRealRandomAccess<Triple<A, B, C>, D> {

		private final PickAndConvert<A, B, C, D> pac;

		public PickOneAccess(final RealRandomAccess<Triple<A, B, C>> source, final PickAndConvert<A, B, C, D> pac) {

			super(source);
			this.pac = pac;
		}

		@Override
		public D get() {

			return pac.apply(source.get());
		}

		@Override
		public AbstractConvertedRealRandomAccess<Triple<A, B, C>, D> copy() {

			return new PickOneAccess<>(source.copy(), pac.copy());
		}

	}

	@Override public D getType() {

		return realRandomAccess().getType();
	}

	@Override
	public AbstractConvertedRealRandomAccess<Triple<A, B, C>, D> realRandomAccess() {

		return new PickOneAccess<>(source.realRandomAccess(), pac.copy());
	}

	@Override
	public AbstractConvertedRealRandomAccess<Triple<A, B, C>, D> realRandomAccess(final RealInterval interval) {

		return realRandomAccess();
	}

}
