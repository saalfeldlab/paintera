package org.janelia.saalfeldlab.paintera.ui;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import bdv.fx.viewer.ViewerPanelFX;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MouseCoordinatePrinter
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private class Listener implements EventHandler<javafx.scene.input.MouseEvent>, TransformListener<AffineTransform3D>
	{

		private final ViewerPanelFX viewer;

		private double x, y;

		private final double[] pos = new double[3];

		private final RealPoint p = RealPoint.wrap(pos);

		private Listener(final ViewerPanelFX viewer)
		{
			this.viewer = viewer;
		}

		private final void updateStatusBar()
		{
			viewer.displayToGlobalCoordinates(x, y, p);
			LOG.trace("display-coordinates=({} {}), world-coordinates={}", x, y, p);
			Platform.runLater(() -> statusBar.setText(String.format(
					"(%.3f, %.3f) (%.3f, %.3f, %.3f)",
					x,
					y,
					pos[0],
					pos[1],
					pos[2]
			                                                       )));
		}

		@Override
		public void transformChanged(final AffineTransform3D transform)
		{
			updateStatusBar();
		}

		@Override
		public void handle(final MouseEvent e)
		{
			this.x = e.getX();
			this.y = e.getY();
			updateStatusBar();
		}

	}

	private final Label statusBar;

	public MouseCoordinatePrinter(final Label statusBar)
	{
		super();
		this.statusBar = statusBar;
	}

	private final HashMap<ViewerPanelFX, Listener> listeners = new HashMap<>();

	private final Function<ViewerPanelFX, Listener> generator = Listener::new;

	public Consumer<ViewerPanelFX> onEnter()
	{
		return t -> {
			this.listeners.put(t, generator.apply(t));
			final Listener listener = this.listeners.get(t);
			t.addEventHandler(MouseEvent.MOUSE_MOVED, listener);
			//			t.getDisplay().addMouseMotionListener( listener );
			t.addTransformListener(listener);
		};
	}

	public Consumer<ViewerPanelFX> onExit()
	{
		return t -> {
			final Listener listener = this.listeners.get(t);
			t.removeEventHandler(MouseEvent.MOUSE_MOVED, listener);
			t.removeTransformListener(listener);
			if (statusBar != null)
				statusBar.setText("(---.---, ---.---) (---.---, ---.---, ---.---)");
		};
	}

}
