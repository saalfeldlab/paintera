package org.janelia.saalfeldlab.paintera.config;

import java.util.Arrays;
import java.util.Collection;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.paint.Color;
import org.janelia.saalfeldlab.paintera.ui.Crosshair;
import org.janelia.saalfeldlab.util.Colors;

public class CrosshairConfig
{

	public static final Color DEFAULT_ON_FOCUS_COLOR = Colors.CREMI;

	public static final Color DEFAULT_OUT_OF_FOCUS_COLOR = Color.WHITE.deriveColor(0, 1, 1, 0.5);

	private final ObjectProperty<Color> onFocusColor = new SimpleObjectProperty<>(DEFAULT_ON_FOCUS_COLOR);

	private final ObjectProperty<Color> outOfFocusColor = new SimpleObjectProperty<>(DEFAULT_OUT_OF_FOCUS_COLOR);

	private final BooleanProperty showCrosshairs = new SimpleBooleanProperty(true);

	private final BooleanProperty wasChanged = new SimpleBooleanProperty(false);

	{
		onFocusColor.addListener((obs, oldv, newv) -> wasChanged.set(true));
		outOfFocusColor.addListener((obs, oldv, newv) -> wasChanged.set(true));
		showCrosshairs.addListener((obs, oldv, newv) -> wasChanged.set(true));
		wasChanged.addListener((obs, oldv, newv) -> wasChanged.set(false));
	}

	public Color getOnFocusColor()
	{
		return onFocusColor.get();
	}

	public void setOnFocusColor(final Color color)
	{
		this.onFocusColor.set(color);
	}

	public ObjectProperty<Color> onFocusColorProperty()
	{
		return this.onFocusColor;
	}

	public Color getOutOfFocusColor()
	{
		return outOfFocusColor.get();
	}

	public void setOutOfFocusColor(final Color color)
	{
		this.outOfFocusColor.set(color);
	}

	public ObjectProperty<Color> outOfFocusColorProperty()
	{
		return this.outOfFocusColor;
	}

	public boolean getShowCrosshairs()
	{
		return showCrosshairs.get();
	}

	public void setShowCrosshairs(final boolean show)
	{
		this.showCrosshairs.set(show);
	}

	public BooleanProperty showCrosshairsProperty()
	{
		return this.showCrosshairs;
	}

	public ObservableBooleanValue wasChanged()
	{
		return this.wasChanged();
	}

	public void bindCrosshairsToConfig(final Collection<Crosshair> crosshairs)
	{
		crosshairs.stream().forEach(this::bindCrosshairToConfig);
	}

	public void bindCrosshairsToConfig(final Crosshair... crosshairs)
	{
		this.bindCrosshairsToConfig(Arrays.asList(crosshairs));
	}

	public void bindCrosshairToConfig(final Crosshair crosshair)
	{
		crosshair.highlightColorProperty().bind(this.onFocusColor);
		crosshair.regularColorProperty().bind(this.outOfFocusColor);
		crosshair.isVisibleProperty().bind(this.showCrosshairs);
	}
}
