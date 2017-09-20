package bdv.bigcat.viewer;

import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import net.imglib2.RealRandomAccess;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.ui.TransformListener;

public class ValueDisplayListener implements MouseMotionListener, TransformListener< AffineTransform3D >
{

	private final HashMap< Source< ? >, Source< ? > > dataSourceMap;

	private final HashMap< Source< ? >, Consumer > valueHandlers;

	private final ViewerPanel viewer;

	private final AffineTransform3D viewerTransform = new AffineTransform3D();

	private int x = -1;

	private int y = -1;

	public ValueDisplayListener( final HashMap< Source< ? >, Source< ? > > dataSourceMap, final HashMap< Source< ? >, Consumer > valueHandlers, final ViewerPanel viewer )
	{
		super();
		this.dataSourceMap = dataSourceMap;
		this.valueHandlers = valueHandlers;
		this.viewer = viewer;
	}

	@Override
	public void mouseDragged( final MouseEvent e )
	{}

	@Override
	public void mouseMoved( final MouseEvent e )
	{
		x = e.getX();
		y = e.getY();

		synchronized ( viewer )
		{
			getInfo();
		}
	}

	@Override
	public void transformChanged( final AffineTransform3D transform )
	{
		this.viewerTransform.set( transform );
		synchronized ( viewer )
		{
			getInfo();
		}
	}

	private static Object getVal( final int x, final int y, final RealRandomAccess< ? > access, final ViewerPanel viewer )
	{
		access.setPosition( x, 0 );
		access.setPosition( y, 1 );
		access.setPosition( 0l, 2 );
		return getVal( access, viewer );
	}

	private static Object getVal( final RealRandomAccess< ? > access, final ViewerPanel viewer )
	{
		viewer.displayToGlobalCoordinates( access );
		return access.get();
	}

	private Optional< Source< ? > > getSource()
	{
		final int currentSource = viewer.getState().getCurrentSource();
		final List< SourceState< ? > > sources = viewer.getState().getSources();
		if ( sources.size() <= currentSource || currentSource < 0 )
			return Optional.empty();
		final Source< ? > activeSource = sources.get( currentSource ).getSpimSource();
		return Optional.of( activeSource );
	}

	private void getInfo()
	{
		final Optional< Source< ? > > optionalSource = getSource();
		if ( optionalSource.isPresent() )
		{
			final Source< ? > activeSource = optionalSource.get();
			if ( dataSourceMap.containsKey( activeSource ) && valueHandlers.containsKey( activeSource ) )
			{
				final ViewerState state = viewer.getState();
				final int currentSource = state.getCurrentSource();
				final Interpolation interpolation = state.getInterpolation();
				final int level = state.getBestMipMapLevel( this.viewerTransform, currentSource );
				final Source< ? > source = dataSourceMap.get( activeSource );
				final AffineTransform3D affine = new AffineTransform3D();
				source.getSourceTransform( 0, level, affine );
				final RealRandomAccess< ? > access = RealViews.transformReal( source.getInterpolatedSource( 0, level, interpolation ), affine ).realRandomAccess();
				final Object val = getVal( x, y, access, viewer );
				valueHandlers.get( activeSource ).accept( val );
			}
		}
	}

}
