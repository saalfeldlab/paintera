package org.janelia.saalfeldlab.paintera.ui;

import bdv.fx.viewer.OverlayRendererGeneric;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class CrossHair implements OverlayRendererGeneric< GraphicsContext >
{

	public static final Color DEFAULT_COLOR = Color.rgb( 255, 255, 255, 0.5 );

	private int w, h;

	private final int strokeWidth = 1;

	private final ObjectProperty< Color > color = new SimpleObjectProperty<>( DEFAULT_COLOR );

	private final BooleanProperty isVisible = new SimpleBooleanProperty( true );

	private final BooleanProperty wasChanged = new SimpleBooleanProperty( false );
	{
		color.addListener( ( obs, oldv, newv ) -> wasChanged.set( true ) );
		isVisible.addListener( ( obs, oldv, newv ) -> wasChanged.set( true ) );
		wasChanged.addListener( ( obs, oldv, newv ) -> wasChanged.set( false ) );
	}

	public void setColor( final int r, final int g, final int b )
	{
		setColor( r, g, b, 127 );
	}

	public void setColor( final double r, final double g, final double b, final double a )
	{
		setColor( new Color( r, g, b, a ) );
	}

	public void setColor( final int r, final int g, final int b, final double a )
	{
		setColor( Color.rgb( r, g, b, a ) );
	}

	public void setColor( final Color color )
	{
		this.color.set( color );
	}

	public ObjectProperty< Color > colorProperty()
	{
		return this.color;
	}

	public BooleanProperty isVisibleProperty()
	{
		return this.isVisible;
	}

	public ReadOnlyBooleanProperty wasChangedProperty()
	{
		return this.wasChanged;
	}

	@Override
	public void setCanvasSize( final int width, final int height )
	{
		w = width;
		h = height;
	}

	@Override
	public void drawOverlays( final GraphicsContext g )
	{

		final Color color = this.color.get();
		if ( color != null && color.getOpacity() > 0 && isVisible.get() )
		{
			g.setStroke( color );
			g.setLineWidth( strokeWidth );
			g.strokeLine( 0, h / 2, w, h / 2 );
			g.strokeLine( w / 2, 0, w / 2, h );
		}

	}

}
