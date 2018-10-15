package org.janelia.saalfeldlab.paintera.control;

import javafx.scene.input.ScrollEvent;

public class ControlUtils {

	/**
	 *
	 * @param e event
	 * @return {@code e.getDeltaX()} if {@code e.getDeltaX() > e.getDeltaY()}, {@code e.getDeltaY()} otherwise.
	 */
	public static double getBiggestScroll(ScrollEvent e)
	{
		return getArgMaxWithRespectToAbs(e.getDeltaX(), e.getDeltaY());
	}

	private static double getArgMaxWithRespectToAbs(double v1, double v2)
	{
		return Math.abs(Math.abs(v1)) > Math.abs(v2) ? v1 : v2;
	}

}
