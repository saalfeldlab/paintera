package bdv.bigcat.viewer.atlas;

import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;

public class WrappedRealRandomAccessible< T > implements RealRandomAccessible< T >
{

	private RealRandomAccessible< T > wrappee;

	public WrappedRealRandomAccessible( final RealRandomAccessible< T > wrappee )
	{
		super();
		wrap( wrappee );
	}

	public void wrap( final RealRandomAccessible< T > wrappee )
	{
		this.wrappee = wrappee;
	}

	@Override
	public int numDimensions()
	{
		return wrappee.numDimensions();
	}

	@Override
	public RealRandomAccess< T > realRandomAccess()
	{
		return wrappee.realRandomAccess();
	}

	@Override
	public RealRandomAccess< T > realRandomAccess( final RealInterval interval )
	{
		return wrappee.realRandomAccess( interval );
	}

}
