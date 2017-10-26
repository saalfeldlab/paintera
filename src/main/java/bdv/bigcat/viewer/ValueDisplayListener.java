package bdv.bigcat.viewer;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanelFX;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import net.imglib2.RealRandomAccess;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.ui.TransformListener;

public class ValueDisplayListener implements EventHandler< javafx.scene.input.MouseEvent >, TransformListener< AffineTransform3D >
{

	private final HashMap< Source< ? >, Source< ? > > dataSourceMap;

	private final HashMap< Source< ? >, Consumer > valueHandlers;

	private final ViewerPanelFX viewer;

	private final AffineTransform3D viewerTransform = new AffineTransform3D();

	private double x = -1;

	private double y = -1;

	public ValueDisplayListener( final HashMap< Source< ? >, Source< ? > > dataSourceMap, final HashMap< Source< ? >, Consumer > valueHandlers, final ViewerPanelFX viewer )
	{
		super();
		this.dataSourceMap = dataSourceMap;
		this.valueHandlers = valueHandlers;
		this.viewer = viewer;
	}

	@Override
	public void handle( final MouseEvent e )
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

	private static Object getVal( final double x, final double y, final RealRandomAccess< ? > access, final ViewerPanelFX viewer )
	{
		access.setPosition( x, 0 );
		access.setPosition( y, 1 );
		access.setPosition( 0l, 2 );
		return getVal( access, viewer );
	}

	private static Object getVal( final RealRandomAccess< ? > access, final ViewerPanelFX viewer )
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
