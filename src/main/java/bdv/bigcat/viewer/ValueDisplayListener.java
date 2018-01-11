package bdv.bigcat.viewer;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.state.ViewerState;
import javafx.beans.value.ObservableIntegerValue;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import net.imglib2.RealRandomAccess;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.ui.TransformListener;

public class ValueDisplayListener implements EventHandler< javafx.scene.input.MouseEvent >, TransformListener< AffineTransform3D >
{

	@SuppressWarnings( "rawtypes" )
	private final HashMap< DataSource< ?, ? >, Consumer > valueHandlers;

	private final ViewerPanelFX viewer;

	private final AffineTransform3D viewerTransform = new AffineTransform3D();

	private final ObservableValue< Source< ? > > currentSource;

	private final ObservableIntegerValue currentSourceIndexInVisibleSources;

	private double x = -1;

	private double y = -1;

	public ValueDisplayListener(
			@SuppressWarnings( "rawtypes" ) final HashMap< DataSource< ?, ? >, Consumer > valueHandlers,
			final ViewerPanelFX viewer,
			final ObservableValue< Source< ? > > currentSource,
			final ObservableIntegerValue currentSourceIndedxInVisibleSources )
	{
		super();
		this.valueHandlers = valueHandlers;
		this.viewer = viewer;
		this.currentSource = currentSource;
		this.currentSourceIndexInVisibleSources = currentSourceIndedxInVisibleSources;
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

	@SuppressWarnings( "unchecked" )
	private void getInfo()
	{
		final Optional< Source< ? > > optionalSource = Optional.ofNullable( currentSource.getValue() );
		if ( optionalSource.isPresent() && optionalSource.get() instanceof DataSource< ?, ? > )
		{
			final DataSource< ?, ? > source = ( DataSource< ?, ? > ) optionalSource.get();
			final int currentSourceIndex = currentSourceIndexInVisibleSources.get();
			if ( valueHandlers.containsKey( source ) && currentSourceIndex != -1 )
			{
				final ViewerState state = viewer.getState();
				final Interpolation interpolation = state.getInterpolation();
				final int level = state.getBestMipMapLevel( this.viewerTransform, currentSourceIndex );
				final AffineTransform3D affine = new AffineTransform3D();
				source.getSourceTransform( 0, level, affine );
				final RealRandomAccess< ? > access = RealViews.transformReal( source.getInterpolatedDataSource( 0, level, interpolation ), affine ).realRandomAccess();
				final Object val = getVal( x, y, access, viewer );
				valueHandlers.get( source ).accept( val );
			}
		}
	}

}
