package bdv.bigcat.viewer.state;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;

public class GlobalTransformManager
{

	private final ArrayList< TransformListener< AffineTransform3D > > listeners;

	private final AffineTransform3D affine;

	public GlobalTransformManager( final TransformListener< AffineTransform3D >... listeners )
	{
		this( new AffineTransform3D(), listeners );
	}

	public GlobalTransformManager( final AffineTransform3D affine, final TransformListener< AffineTransform3D >... listeners )
	{
		this( affine, Arrays.asList( listeners ) );
	}

	public GlobalTransformManager( final AffineTransform3D affine, final List< TransformListener< AffineTransform3D > > listeners )
	{
		super();
		this.listeners = new ArrayList<>();
		this.affine = affine;
		listeners.forEach( l -> addListener( l ) );
	}

	public synchronized void setTransform( final AffineTransform3D affine )
	{
		this.affine.set( affine );
		notifyListeners();
	}

	public void addListener( final TransformListener< AffineTransform3D > listener )
	{
		this.listeners.add( listener );
		listener.transformChanged( this.affine.copy() );
	}

	public void removeListener( final TransformListener< AffineTransform3D > listener )
	{
		this.listeners.remove( listener );
	}

	public synchronized void preConcatenate( final AffineTransform3D transform )
	{
		this.affine.preConcatenate( transform );
		notifyListeners();
	}

	public synchronized void concatenate( final AffineTransform3D transform )
	{
		this.affine.concatenate( transform );
		notifyListeners();
	}

	private synchronized void notifyListeners()
	{
		for ( final TransformListener< AffineTransform3D > l : listeners )
			l.transformChanged( this.affine.copy() );
	}

}
