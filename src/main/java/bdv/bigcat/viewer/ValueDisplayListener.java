package bdv.bigcat.viewer;

import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.HashMap;
import java.util.function.Consumer;

import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import net.imglib2.RealRandomAccess;

public class ValueDisplayListener implements MouseMotionListener
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

		final Source< ? > activeSource = viewer.getState().getSources().get( viewer.getState().getCurrentSource() ).getSpimSource();
		if ( accessMap.containsKey( activeSource ) )
		{
			final Object val = getVal( x, y, accessMap.get( activeSource ), viewer );
			if ( valueHandlers.containsKey( activeSource ) )
				valueHandlers.get( activeSource ).accept( val );
		}
	}

	public static Object getVal( final int x, final int y, final RealRandomAccess< ? > access, final ViewerPanel viewer )
	{
		viewer.displayToGlobalCoordinates( x, y, access );
		return access.get();
	}

}
