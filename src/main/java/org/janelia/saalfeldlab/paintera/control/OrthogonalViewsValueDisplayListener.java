package org.janelia.saalfeldlab.paintera.control;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.MouseEvent;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.paintera.control.navigation.ValueDisplayListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class OrthogonalViewsValueDisplayListener {

  private final Map<ViewerPanelFX, ValueDisplayListener> listeners = new HashMap<>();

  private final Consumer<String> submitValue;

  private final ObservableValue<Source<?>> currentSource;

  private final Function<Source<?>, Interpolation> interpolation;

  private final ObjectProperty<OrthogonalViews.ViewerAndTransforms> activeViewerProperty = new SimpleObjectProperty<>();

  public OrthogonalViewsValueDisplayListener(
		  final Consumer<String> submitValue,
		  final ObservableValue<Source<?>> currentSource,
		  final Function<Source<?>, Interpolation> interpolation) {

	super();
	this.submitValue = submitValue;
	this.currentSource = currentSource;
	this.interpolation = interpolation;
	activeViewerProperty.addListener((obs, exiting, entering) -> {
	  if (Objects.nonNull(exiting)) {
		removeHandlers(exiting.viewer());
	  }
	  if (Objects.nonNull(entering)) {
		addHandlers(entering.viewer());
	  }
	});
  }

  public void addHandlers(ViewerPanelFX viewer) {

	if (!this.listeners.containsKey(viewer))
	  this.listeners.put(viewer, new ValueDisplayListener(viewer, currentSource, interpolation, submitValue));
	viewer.getDisplay().addEventFilter(MouseEvent.MOUSE_MOVED, this.listeners.get(viewer));
	viewer.addTransformListener(this.listeners.get(viewer));
  }

  public void removeHandlers(ViewerPanelFX viewer) {

	viewer.getDisplay().removeEventHandler(MouseEvent.MOUSE_MOVED, this.listeners.get(viewer));
	viewer.removeTransformListener(this.listeners.get(viewer));
	submitValue.accept("");
  }

  public void bindActiveViewer(ObservableValue<OrthogonalViews.ViewerAndTransforms> activeViewerObservable) {
	/* Binding would be neater here, but inexplicably, doesn't work? */
	activeViewerObservable.addListener((obs, oldv, newv) -> activeViewerProperty.set(newv));
  }
}
