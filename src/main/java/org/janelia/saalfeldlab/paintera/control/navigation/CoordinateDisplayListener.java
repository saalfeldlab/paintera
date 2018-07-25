package org.janelia.saalfeldlab.paintera.control.navigation;

import java.util.function.Consumer;

import bdv.fx.viewer.ViewerPanelFX;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;

public class CoordinateDisplayListener
{

	private final ViewerPanelFX viewer;

	private double x = -1;

	private double y = -1;

	private final Consumer<RealPoint> submitViewerCoordinate;

	private final Consumer<RealPoint> submitWorldCoordinate;

	public CoordinateDisplayListener(
			final ViewerPanelFX viewer,
			final Consumer<RealPoint> submitViewerCoordinate,
			final Consumer<RealPoint> submitWorldCoordinate)
	{
		super();
		this.viewer = viewer;
		this.submitViewerCoordinate = submitViewerCoordinate;
		this.submitWorldCoordinate = submitWorldCoordinate;
	}

	public void update(final double x, final double y)
	{
		this.x = x;
		this.y = y;
		final RealPoint p = new RealPoint(x, y);
		this.submitViewerCoordinate.accept(p);

		synchronized (viewer)
		{
			updateWorldCoordinates();
		}
	}

	private static <P extends RealLocalizable & RealPositionable> void toGlobalCoordinate(final double x, final double
			y, final P p, final ViewerPanelFX viewer)
	{
		p.setPosition(x, 0);
		p.setPosition(y, 1);
		p.setPosition(0l, 2);
		viewer.displayToGlobalCoordinates(p);
	}

	private void updateWorldCoordinates()
	{
		final RealPoint p = new RealPoint(3);
		toGlobalCoordinate(x, y, p, viewer);
		submitWorldCoordinate.accept(p);
	}

	public static String worldToString(final RealPoint p)
	{
		final String s1 = String.format("%.3f", p.getDoublePosition(0));
		final String s2 = String.format("%.3f", p.getDoublePosition(1));
		final String s3 = String.format("%.3f", p.getDoublePosition(2));

		return String.format(
				"(%8s, %8s, %8s)",
				s1.length() > 8 ? s1.substring(0, 8) : s1,
				s2.length() > 8 ? s2.substring(0, 8) : s2,
				s3.length() > 8 ? s3.substring(0, 8) : s3
		                    );

	}

}
