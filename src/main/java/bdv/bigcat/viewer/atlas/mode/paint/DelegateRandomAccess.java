package bdv.bigcat.viewer.atlas.mode.paint;

import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.Sampler;

public class DelegateRandomAccess< T > implements RandomAccess< T >
{

	private final RandomAccess< ? extends T > delegate;

	public DelegateRandomAccess( final RandomAccess< ? extends T > delegate )
	{
		super();
		this.delegate = delegate;
	}

	@Override
	public void localize( final int[] position )
	{
		delegate.localize( position );
	}

	@Override
	public void localize( final long[] position )
	{
		delegate.localize( position );
	}

	@Override
	public int getIntPosition( final int d )
	{
		return delegate.getIntPosition( d );
	}

	@Override
	public long getLongPosition( final int d )
	{
		return delegate.getLongPosition( d );
	}

	@Override
	public void localize( final float[] position )
	{
		delegate.localize( position );
	}

	@Override
	public void localize( final double[] position )
	{
		delegate.localize( position );
	}

	@Override
	public float getFloatPosition( final int d )
	{
		return delegate.getFloatPosition( d );
	}

	@Override
	public double getDoublePosition( final int d )
	{
		return delegate.getDoublePosition( d );
	}

	@Override
	public int numDimensions()
	{
		return delegate.numDimensions();
	}

	@Override
	public void fwd( final int d )
	{
		delegate.fwd( d );
	}

	@Override
	public void bck( final int d )
	{
		delegate.bck( d );
	}

	@Override
	public void move( final int distance, final int d )
	{
		delegate.move( distance, d );
	}

	@Override
	public void move( final long distance, final int d )
	{
		delegate.move( distance, d );
	}

	@Override
	public void move( final Localizable localizable )
	{
		delegate.move( localizable );
	}

	@Override
	public void move( final int[] distance )
	{
		delegate.move( distance );
	}

	@Override
	public void move( final long[] distance )
	{
		delegate.move( distance );
	}

	@Override
	public void setPosition( final Localizable localizable )
	{
		delegate.setPosition( localizable );
	}

	@Override
	public void setPosition( final int[] position )
	{
		delegate.setPosition( position );
	}

	@Override
	public void setPosition( final long[] position )
	{
		delegate.setPosition( position );
	}

	@Override
	public void setPosition( final int position, final int d )
	{
		delegate.setPosition( position, d );
	}

	@Override
	public void setPosition( final long position, final int d )
	{
		delegate.setPosition( position, d );
	}

	@Override
	public T get()
	{
		return delegate.get();
	}

	@Override
	public Sampler< T > copy()
	{
		return copyRandomAccess();
	}

	@Override
	public RandomAccess< T > copyRandomAccess()
	{
		return new DelegateRandomAccess<>( delegate.copyRandomAccess() );
	}

}
