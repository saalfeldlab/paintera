package bdv.bigcat.viewer;

import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import bdv.viewer.state.SourceState;
import net.imglib2.RealRandomAccess;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;

public class ValueDisplayListener implements MouseMotionListener, TransformListener< AffineTransform3D >
{

	private final HashMap< Source< ? >, RealRandomAccess< ? > > accessMap;

	private final HashMap< Source< ? >, Consumer > valueHandlers;

	private final ViewerPanel viewer;

	public ValueDisplayListener( final HashMap< Source< ? >, RealRandomAccess< ? > > accessMap, final HashMap< Source< ? >, Consumer > valueHandlers, final ViewerPanel viewer )
	{
		super();
		this.accessMap = accessMap;
		this.valueHandlers = valueHandlers;
		this.viewer = viewer;
	}

	@Override
	public void mouseDragged( final MouseEvent e )
	{}

	@Override
	public void mouseMoved( final MouseEvent e )
	{
		final int x = e.getX();
		final int y = e.getY();

		synchronized ( viewer )
		{
			final Optional< Source< ? > > optionalSource = getSource();
			if ( optionalSource.isPresent() )
			{
				final Source< ? > activeSource = optionalSource.get();
				if ( accessMap.containsKey( activeSource ) && valueHandlers.containsKey( activeSource ) )
				{
					final RealRandomAccess< ? > access = accessMap.get( activeSource );
					final Object val = getVal( x, y, access, viewer );
					valueHandlers.get( activeSource ).accept( val );
				}
			}
		}
	}

	@Override
	public void transformChanged( final AffineTransform3D transform )
	{
		synchronized ( viewer )
		{
			final Optional< Source< ? > > optionalSource = getSource();
			if ( optionalSource.isPresent() )
			{
				final Source< ? > activeSource = optionalSource.get();
				if ( accessMap.containsKey( activeSource ) && valueHandlers.containsKey( activeSource ) )
				{
					final RealRandomAccess< ? > access = accessMap.get( activeSource );
					viewer.getMouseCoordinates( access );
					access.setPosition( 0l, 2 );
					viewer.displayToGlobalCoordinates( access );
					final Object val = access.get();
					valueHandlers.get( activeSource ).accept( val );
				}
			}
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

}
