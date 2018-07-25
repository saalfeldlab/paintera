package org.janelia.saalfeldlab.paintera.ui;

import bdv.fx.viewer.OverlayRendererGeneric;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class Crosshair implements OverlayRendererGeneric<GraphicsContext>
{

	public static final Color DEFAULT_HIGHLIGHT_COLOR = Color.rgb(255, 255, 255, 0.5);

	public static final Color DEFAULT_REGULAR_COLOR = Color.rgb(255, 255, 255, 0.5);

	private int w, h;

	private final int strokeWidth = 1;

	private final BooleanProperty isHighlight = new SimpleBooleanProperty(true);

	private final ObjectProperty<Color> highlightColor = new SimpleObjectProperty<>(DEFAULT_HIGHLIGHT_COLOR);

	private final ObjectProperty<Color> regularColor = new SimpleObjectProperty<>(DEFAULT_REGULAR_COLOR);

	private final ObjectBinding<Color> color = Bindings.createObjectBinding(
			() -> isHighlight.get()
			      ? highlightColor.get()
			      : regularColor.get(),
			isHighlight,
			highlightColor,
			regularColor
	                                                                       );

	private final BooleanProperty isVisible = new SimpleBooleanProperty(true);

	private final BooleanProperty wasChanged = new SimpleBooleanProperty(false);

	{
		color.addListener((obs, oldv, newv) -> wasChanged.set(true));
		isVisible.addListener((obs, oldv, newv) -> wasChanged.set(true));
		wasChanged.addListener((obs, oldv, newv) -> wasChanged.set(false));
	}

	public void setHighlightColor(final double r, final double g, final double b, final double a)
	{
		setHighlightColor(new Color(r, g, b, a));
	}

	public void setHighlightColor(final int r, final int g, final int b, final double a)
	{
		setHighlightColor(Color.rgb(r, g, b, a));
	}

	public void setHighlightColor(final Color color)
	{
		this.highlightColor.set(color);
	}

	public ObjectProperty<Color> highlightColorProperty()
	{
		return this.highlightColor;
	}

	public void setRegularColor(final double r, final double g, final double b, final double a)
	{
		setRegularColor(new Color(r, g, b, a));
	}

	public void setReuglarColor(final int r, final int g, final int b, final double a)
	{
		setRegularColor(Color.rgb(r, g, b, a));
	}

	public void setRegularColor(final Color color)
	{
		this.regularColor.set(color);
	}

	public ObjectProperty<Color> regularColorProperty()
	{
		return this.regularColor;
	}

	public BooleanProperty isVisibleProperty()
	{
		return this.isVisible;
	}

	public BooleanProperty isHighlightProperty()
	{
		return this.isHighlight;
	}

	public ReadOnlyBooleanProperty wasChangedProperty()
	{
		return this.wasChanged;
	}

	@Override
	public void setCanvasSize(final int width, final int height)
	{
		w = width;
		h = height;
	}

	@Override
	public void drawOverlays(final GraphicsContext g)
	{

		final Color color = this.color.get();
		if (color != null && color.getOpacity() > 0 && isVisible.get())
		{
			g.setStroke(color);
			g.setLineWidth(strokeWidth);
			g.strokeLine(0, h / 2, w, h / 2);
			g.strokeLine(w / 2, 0, w / 2, h);
		}

	}

}
