package org.janelia.saalfeldlab.bdv.fx.viewer.render;

import net.imglib2.Interval;

/**
 * Tracks rendering speed (nanoseconds per rendered pixel) and estimates a target screen scale
 * for a repaint request, given the request's interval and a target render time.
 * <p>
 * The rate is scale-independent: mipmap selection keeps each rendered output pixel ≈ 1 source
 * pixel regardless of screen scale, so a single global rate (maintained as an exponentially
 * weighted moving average over successful renders) extrapolates to every scale by simple
 * pixel-count multiplication.
 * <p>
 * The selection rule is: pick the finest scale whose estimated render time for the request is
 * less than {@code targetRenderNanos}. With no measurements yet the coarsest scale is returned
 * so the first frame is responsive; one measurement is enough to seed informed picks for every
 * subsequent request.
 */
class ScreenScaleRenderTimer {

	/**
	 * Higher values weight recent measurements more.
	 */
	private static final double EWMA_WEIGHT = 0.3;

	private final long targetRenderNanos;

	private double[] screenScales;

	private double nanosPerPixel = Double.NaN;

	ScreenScaleRenderTimer(final double[] screenScales, final long targetRenderNanos) {

		this.targetRenderNanos = targetRenderNanos;
		setScreenScales(screenScales);
	}

	/**
	 * @param screenScales to use for the render timer. The learned rate is preserved across calls
	 * because it does not depend on the scale ladder.
	 */
	synchronized void setScreenScales(final double[] screenScales) {

		this.screenScales = screenScales.clone();
	}

	/**
	 * Update the moving-average rate after a successful render of {@code screenInterval} at
	 * {@code scaleIdx}, which took {@code elapsedNanos}.
	 *
	 * @param scaleIdx the index of the screen scale used for rendering
	 * @param elapsedNanos the time taken for the render in nanoseconds
	 * @param screenInterval interval which was rendered
	 */
	synchronized void updateRenderTime(final int scaleIdx, final long elapsedNanos, final Interval screenInterval) {

		if (scaleIdx < 0 || scaleIdx >= screenScales.length || elapsedNanos <= 0 || screenInterval == null)
			return;
		final long pixelsRendered = numScreenPixels(screenInterval, screenScales[scaleIdx]);
		if (pixelsRendered <= 0)
			return;
		final double nsPerPixelNow = (double) elapsedNanos / pixelsRendered;
		nanosPerPixel = exponentialWeightedMovingAverage(EWMA_WEIGHT, nsPerPixelNow, nanosPerPixel);
	}

	/**
	 * Best guess initial screen scale index for a repaint of {@code requestInterval}.
	 *
	 * @return the smallest index whose estimated render time fits the configured budget. With no
	 * measurement yet, or if no scale fits the budget, the coarsest available scale is returned.
	 */
	synchronized int estimateTargetScreenScale(final Interval requestInterval) {

		final int coarsestScale = Math.max(0, screenScales.length - 1);
		if (requestInterval == null || screenScales.length == 0 || Double.isNaN(nanosPerPixel))
			return coarsestScale;

		for (int idx = 0; idx < screenScales.length; ++idx) {
			final long pixels = numScreenPixels(requestInterval, screenScales[idx]);
			if (nanosPerPixel * pixels <= targetRenderNanos)
				return idx;
		}
		return coarsestScale;
	}

	private static long numScreenPixels(final Interval screenInterval, double screenScale) {

		final long width = (long) Math.ceil(screenInterval.dimension(0) * screenScale);
		final long height = (long) Math.ceil(screenInterval.dimension(1) * screenScale);
		return Math.max(1L, width * height);
	}

	public static double exponentialWeightedMovingAverage(double weight, double newValue, double currentAverage) {
		return Double.isNaN(currentAverage) ? newValue : weight * newValue + (1.0 - weight) * currentAverage;
	}
}
