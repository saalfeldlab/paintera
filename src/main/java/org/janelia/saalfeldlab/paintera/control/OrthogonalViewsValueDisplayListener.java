package org.janelia.saalfeldlab.paintera.control;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.MouseEvent;
import org.janelia.saalfeldlab.paintera.control.navigation.ValueDisplayListener;

public class OrthogonalViewsValueDisplayListener
{

	private final Map<ViewerPanelFX, ValueDisplayListener> listeners = new HashMap<>();

	private final Consumer<String> submitValue;

	private final ObservableValue<Source<?>> currentSource;

	private final Function<Source<?>, Interpolation> interpolation;

	public OrthogonalViewsValueDisplayListener(
			final Consumer<String> submitValue,
			final ObservableValue<Source<?>> currentSource,
			final Function<Source<?>, Interpolation> interpolation)
	{
		super();
		this.submitValue = submitValue;
		this.currentSource = currentSource;
		this.interpolation = interpolation;
	}

	public Consumer<ViewerPanelFX> onEnter()
	{
		return t -> {
			if (!this.listeners.containsKey(t))
				this.listeners.put(t, new ValueDisplayListener(t, currentSource, interpolation, submitValue));
			t.getDisplay().addEventFilter(MouseEvent.MOUSE_MOVED, this.listeners.get(t));
			t.addTransformListener(this.listeners.get(t));
		};
	}

	public Consumer<ViewerPanelFX> onExit()
	{
		return t -> {
			t.getDisplay().removeEventHandler(MouseEvent.MOUSE_MOVED, this.listeners.get(t));
			t.removeTransformListener(this.listeners.get(t));
			submitValue.accept("");
		};
	}

}
