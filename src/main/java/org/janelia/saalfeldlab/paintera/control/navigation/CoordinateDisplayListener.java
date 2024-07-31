package org.janelia.saalfeldlab.paintera.control.navigation;

import bdv.viewer.Source;
import javafx.beans.value.ObservableValue;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX;

import java.util.function.Consumer;

import static java.util.FormatProcessor.FMT;

public class CoordinateDisplayListener {

	private final ViewerPanelFX viewer;
	private final ObservableValue<Source<?>> currentSource;

	private double x = -1;

	private double y = -1;

	private final Consumer<RealPoint> submitViewerCoordinate;

	private final Consumer<RealPoint> submitWorldCoordinate;

	private final Consumer<RealPoint> submitSourceCoordinate;

	public CoordinateDisplayListener(
			final ViewerPanelFX viewer,
			final ObservableValue<Source<?>> currentSource,
			final Consumer<RealPoint> submitViewerCoordinate,
			final Consumer<RealPoint> submitWorldCoordinate,
			final Consumer<RealPoint> submitSourceCoordinate) {

		this.viewer = viewer;
		this.currentSource = currentSource;
		this.submitViewerCoordinate = submitViewerCoordinate;
		this.submitWorldCoordinate = submitWorldCoordinate;
		this.submitSourceCoordinate = submitSourceCoordinate;
	}

	public void update(final double x, final double y) {

		this.x = x;
		this.y = y;
		final RealPoint p = new RealPoint(x, y);
		this.submitViewerCoordinate.accept(p);

		synchronized (viewer) {
			updateWorldCoordinates();
			updateSourceCoordinates(x, y);
		}
	}

	private static <P extends RealLocalizable & RealPositionable> void toGlobalCoordinate(
			final double x,
			final double y,
			final P p,
			final ViewerPanelFX viewer) {

		p.setPosition(x, 0);
		p.setPosition(y, 1);
		p.setPosition(0L, 2);
		viewer.displayToGlobalCoordinates(p);
	}

	private void updateWorldCoordinates() {

		final RealPoint p = new RealPoint(3);
		toGlobalCoordinate(x, y, p, viewer);
		submitWorldCoordinate.accept(p);
	}

	private void updateSourceCoordinates(final double x, final double y) {

		final double[] mouseCoordinates = new double[]{x, y, 0.0};
		final Source<?> source = currentSource.getValue();
		if (source == null)
			return;

		final var sourceToGlobalTransform = new AffineTransform3D();
		source.getSourceTransform(viewer.getState().getTimepoint(), 0, sourceToGlobalTransform);

		final RealPoint sourceCoordinates = new RealPoint(mouseCoordinates);
		viewer.displayToSourceCoordinates(mouseCoordinates[0], mouseCoordinates[1], sourceToGlobalTransform, sourceCoordinates);
		submitSourceCoordinate.accept(sourceCoordinates);
	}

	public static String realPointToString(final RealPoint p) {

		final double d0 = p.getDoublePosition(0);
		final double d1 = p.getDoublePosition(1);
		final double d2 = p.getDoublePosition(2);
		return FMT."(%8.3f\{d0}, %8.3f\{d1}, %8.3f\{d2})";
	}

}
