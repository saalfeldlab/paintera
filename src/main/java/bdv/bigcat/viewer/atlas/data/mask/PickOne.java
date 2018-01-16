package bdv.bigcat.viewer.atlas.data.mask;

import java.util.function.Function;

import bdv.net.imglib2.util.Triple;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.AbstractConvertedRandomAccess;
import net.imglib2.converter.AbstractConvertedRandomAccessibleInterval;

public class PickOne< A, B, C, D > extends AbstractConvertedRandomAccessibleInterval< Triple< A, B, C >, D >
{

	public static interface PickAndConvert< A, B, C, D > extends Function< Triple< A, B, C >, D >
	{

		public PickAndConvert< A, B, C, D > copy();

	}

	private final PickAndConvert< A, B, C, D > pac;

	public PickOne( final RandomAccessibleInterval< Triple< A, B, C > > source, final PickAndConvert< A, B, C, D > pac )
	{
		super( source );
		this.pac = pac;
	}

	public static class PickOneAccess< A, B, C, D > extends AbstractConvertedRandomAccess< Triple< A, B, C >, D >
	{

		private final PickAndConvert< A, B, C, D > pac;

		public PickOneAccess( final RandomAccess< Triple< A, B, C > > source, final PickAndConvert< A, B, C, D > pac )
		{
			super( source );
			this.pac = pac;
		}

		@Override
		public D get()
		{
			return pac.apply( source.get() );
		}

		@Override
		public AbstractConvertedRandomAccess< Triple< A, B, C >, D > copy()
		{
			return new PickOneAccess<>( source.copyRandomAccess(), pac.copy() );
		}

	}

	@Override
	public AbstractConvertedRandomAccess< Triple< A, B, C >, D > randomAccess()
	{
		return new PickOneAccess<>( source.randomAccess(), pac.copy() );
	}

	@Override
	public AbstractConvertedRandomAccess< Triple< A, B, C >, D > randomAccess( final Interval interval )
	{
		return randomAccess();
	}

}
