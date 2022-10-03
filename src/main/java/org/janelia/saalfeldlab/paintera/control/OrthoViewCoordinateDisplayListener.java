package org.janelia.saalfeldlab.paintera.control;

import bdv.fx.viewer.ViewerPanelFX;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.MouseEvent;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;
import org.janelia.saalfeldlab.fx.actions.ActionSet;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.control.navigation.CoordinateDisplayListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import static org.janelia.saalfeldlab.fx.actions.PainteraActionSetKt.painteraActionSet;

public class OrthoViewCoordinateDisplayListener {

	private final Map<ViewerPanelFX, ActionSet> listeners = new HashMap<>();

	private final Map<ViewerPanelFX, TransformListener<AffineTransform3D>> transformListeners = new HashMap<>();

	private final Consumer<RealPoint> submitViewerCoordinate;

	private final Consumer<RealPoint> submitWorldCoordinate;

	private final Consumer<RealPoint> submitSourceCoordinate;

  private final ObjectProperty<OrthogonalViews.ViewerAndTransforms> activeViewerProperty = new SimpleObjectProperty<>();

  public OrthoViewCoordinateDisplayListener(
		  final Consumer<RealPoint> submitViewerCoordinate,
		  final Consumer<RealPoint> submitWorldCoordinate,
		  final Consumer<RealPoint> submitSourceCoordinate
  ) {

	super();
	this.submitViewerCoordinate = submitViewerCoordinate;
	this.submitWorldCoordinate = submitWorldCoordinate;
	this.submitSourceCoordinate = submitSourceCoordinate;
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

	if (!this.listeners.containsKey(viewer)) {
		final CoordinateDisplayListener coordinateListener = new CoordinateDisplayListener(viewer, submitViewerCoordinate, submitWorldCoordinate, submitSourceCoordinate);
		final var coordinateUpdate = painteraActionSet("coordinate update", null, actionSet -> {
			actionSet.addMouseAction(MouseEvent.MOUSE_MOVED, action -> {
				action.setConsume(false);
				action.setName("coordinate update");
				action.ignoreKeys();
				action.onAction(event -> coordinateListener.update(event.getX(), event.getY()));
			});
		});
		listeners.put(viewer, coordinateUpdate);
		this.transformListeners.put(viewer, getTransformListener(viewer));
	}
	ActionSet.installActionSet(viewer, listeners.get(viewer));
	viewer.addTransformListener(transformListeners.get(viewer));
  }

  private TransformListener<AffineTransform3D> getTransformListener(ViewerPanelFX viewer) {

	return transform -> {
	  final double[] mouseCoordinates = new double[]{viewer.getMouseXProperty().get(), viewer.getMouseYProperty().get(), 0.0};
	  submitViewerCoordinate.accept(new RealPoint(mouseCoordinates));
		final RealPoint globalCoordinates = new RealPoint(mouseCoordinates);
		viewer.displayToGlobalCoordinates(mouseCoordinates[0], mouseCoordinates[1], globalCoordinates);
		submitWorldCoordinate.accept(globalCoordinates);


		Optional.ofNullable(Paintera.getPaintera().getBaseView().sourceInfo().currentSourceProperty().get())
				.ifPresent(source -> {
					final var sourceToGlobalTransform = new AffineTransform3D();
					source.getSourceTransform(viewer.getState().getTimepoint(), 0, sourceToGlobalTransform);

					final RealPoint sourceCoordinates = new RealPoint(mouseCoordinates);
					viewer.displayToSourceCoordinates(mouseCoordinates[0], mouseCoordinates[1], sourceToGlobalTransform, sourceCoordinates);
					submitSourceCoordinate.accept(sourceCoordinates);
				});

	};
  }

  public void removeHandlers(ViewerPanelFX viewer) {

	ActionSet.removeActionSet(viewer, listeners.get(viewer));
	viewer.removeTransformListener(transformListeners.get(viewer));
	submitViewerCoordinate.accept(null);
	submitWorldCoordinate.accept(null);
  }

  public void bindActiveViewer(ObservableValue<OrthogonalViews.ViewerAndTransforms> activeViewerObservable) {
	/* Binding would be neater here, but inexplicably, doesn't work? */
	activeViewerObservable.addListener((obs, oldv, newv) -> activeViewerProperty.set(newv));
  }

}
