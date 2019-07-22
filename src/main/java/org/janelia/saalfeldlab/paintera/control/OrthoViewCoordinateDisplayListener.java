package org.janelia.saalfeldlab.paintera.control;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import bdv.fx.viewer.ViewerPanelFX;
import javafx.scene.Node;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;
import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.InstallAndRemove;
import org.janelia.saalfeldlab.paintera.control.navigation.CoordinateDisplayListener;

public class OrthoViewCoordinateDisplayListener
{

	private final Map<ViewerPanelFX, InstallAndRemove<Node>> listeners = new HashMap<>();

	private final Map<ViewerPanelFX, TransformListener<AffineTransform3D>> transformListeners = new HashMap<>();

	private final Consumer<RealPoint> submitViewerCoordinate;

	private final Consumer<RealPoint> submitWorldCoordinate;

	public OrthoViewCoordinateDisplayListener(
			final Consumer<RealPoint> submitViewerCoordinate,
			final Consumer<RealPoint> submitWorldCoordinate)
	{
		super();
		this.submitViewerCoordinate = submitViewerCoordinate;
		this.submitWorldCoordinate = submitWorldCoordinate;
	}

	public Consumer<ViewerPanelFX> onEnter()
	{
		return t -> {
			if (!this.listeners.containsKey(t))
			{
				final CoordinateDisplayListener coordinateListener = new CoordinateDisplayListener(
						t,
						submitViewerCoordinate,
						submitWorldCoordinate);
				listeners.put(
						t,
						EventFX.MOUSE_MOVED("coordinate update",
								e -> coordinateListener.update(e.getX(), e.getY()),
								e -> true));
				final TransformListener<AffineTransform3D> transformListener = transform -> {
					final double[] mouseCoordinates = new double[] {t.mouseXProperty().get(), t.mouseYProperty().get(), 0.0};
					submitViewerCoordinate.accept(new RealPoint(mouseCoordinates));
					t.displayToGlobalCoordinates(mouseCoordinates);
					submitWorldCoordinate.accept(new RealPoint(mouseCoordinates));
				};
				this.transformListeners.put(t, transformListener);
			}
			listeners.get(t).installInto(t);
			t.addTransformListener(transformListeners.get(t));
		};
	}

	public Consumer<ViewerPanelFX> onExit()
	{
		return t -> {
			listeners.get(t).removeFrom(t);
			t.removeTransformListener(transformListeners.get(t));
			submitViewerCoordinate.accept(null);
			submitWorldCoordinate.accept(null);
		};
	}

}
