/*
 * #%L
 * BigDataViewer core classes with minimal dependencies
 * %%
 * Copyright (C) 2012 - 2016 Tobias Pietzsch, Stephan Saalfeld, Stephan Preibisch,
 * Jean-Yves Tinevez, HongKee Moon, Johannes Schindelin, Curtis Rueden, John Bogovic
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bdv.fx.viewer;

import java.util.List;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.text.Font;
import javafx.scene.transform.Affine;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * Paint an overlay showing multiple transformed boxes (interval + transform). Boxes represent sources that are shown in
 * the viewer. Boxes are different colors depending whether the sources are visible.
 *
 * @author Stephan Saalfeld
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
public class MultiBoxOverlayFX
{
	public interface IntervalAndTransform
	{
		public boolean isVisible();

		/**
		 * Get interval of the source (stack) in source-local coordinates.
		 *
		 * @return extents of the source.
		 */
		public Interval getSourceInterval();

		/**
		 * Current transformation from {@link #getSourceInterval() source} to viewer. This is a concatenation of
		 * source-local-to-global transform and the interactive viewer transform.
		 */
		public AffineTransform3D getSourceToViewer();
	}

	private final Color activeBackColor = Color.rgb(0xff, 0x00, 0xff, 1.0);

	private final Color activeFrontColor = Color.rgb(0x00, 0xff, 0x00, 1.0);

	private final Color inactiveBackColor = Color.DARKGRAY;

	private final Color inactiveFrontColor = Color.LIGHTGRAY;

	private final Color canvasColor = Color.rgb(0xbb, 0xbb, 0xbb, 0xb0 / 255.0);
	// new Color( 0xb0bbbbbb, true );

	private final RenderBoxHelperFX renderBoxHelper = new RenderBoxHelperFX();

	/**
	 * This paints the box overlay with perspective and scale set such that it fits approximately into the specified
	 * screen area.
	 *
	 * @param graphics
	 * 		graphics context to paint to.
	 * @param sources
	 * 		source intervals (3D boxes) to be shown.
	 * @param targetInterval
	 * 		target interval (2D box) into which a slice of sourceInterval is projected.
	 * @param boxScreen
	 * 		(approximate) area of the screen which to fill with the box visualisation.
	 */
	public <I extends IntervalAndTransform> void paint(final GraphicsContext graphics, final List<I> sources, final
	Interval targetInterval, final Interval boxScreen)
	{
		assert targetInterval.numDimensions() >= 2;

		if (sources.isEmpty())
			return;

		final double perspective    = 3;
		final double screenBoxRatio = 0.75;

		long maxSourceSize = 0;
		for (final IntervalAndTransform source : sources)
			maxSourceSize = Math.max(
					maxSourceSize,
					Math.max(
							Math.max(source.getSourceInterval().dimension(0), source.getSourceInterval().dimension(1)),
							source.getSourceInterval().dimension(2)
					        )
			                        );
		final long sourceSize = maxSourceSize;
		final long targetSize = Math.max(targetInterval.dimension(0), targetInterval.dimension(1));

		final AffineTransform3D transform      = sources.get(0).getSourceToViewer();
		final double            vx             = transform.get(0, 0);
		final double            vy             = transform.get(1, 0);
		final double            vz             = transform.get(2, 0);
		final double            transformScale = Math.sqrt(vx * vx + vy * vy + vz * vz);
		renderBoxHelper.setDepth(perspective * sourceSize * transformScale);

		final double bw    = screenBoxRatio * boxScreen.dimension(0);
		final double bh    = screenBoxRatio * boxScreen.dimension(1);
		double       scale = Math.min(bw / targetInterval.dimension(0), bh / targetInterval.dimension(1));

		final double tsScale = transformScale * sourceSize / targetSize;
		if (tsScale > 1.0)
			scale /= tsScale;
		renderBoxHelper.setScale(scale);

		final long x = boxScreen.min(0) + boxScreen.dimension(0) / 2;
		final long y = boxScreen.min(1) + boxScreen.dimension(1) / 2;

		final Affine t = graphics.getTransform();
		//		awt affine to javafx affine:
		//		https://stackoverflow.com/questions/32896309/how-to-convert-an-instance-of-java-awt-geom
		// -affinetransform-to-an-instance-of-ja
		//		mxx = m00
		//		mxy = m01
		//		tx  = m02
		//		myx = m10
		//		myy = m11
		//		ty  = m12
		//		new AffineTransform( m00, m10, m01, m11, m02, m12 )
		//		final AffineTransform translate = new AffineTransform( 1, 0, 0, 1, x, y );
		final Affine translate = new Affine(1, 0, x, 0, 1, y);
		translate.prepend(t);
		graphics.setTransform(translate);
		paint(graphics, sources, targetInterval);
		graphics.setTransform(t);
	}

	private volatile boolean highlightInProgress;

	public boolean isHighlightInProgress()
	{
		return highlightInProgress;
	}

	private int highlightIndex = -1;

	private long highlighStartTime = -1;

	private final int highlightDuration = 300;

	public void highlight(final int sourceIndex)
	{
		highlightIndex = sourceIndex;
		highlighStartTime = -1;
	}

	/**
	 * @param graphics
	 * 		graphics context to paint to.
	 * @param sources
	 * 		source intervals (3D boxes) to be shown.
	 * @param targetInterval
	 * 		target interval (2D box) into which a slice of sourceInterval is projected.
	 */
	private <I extends IntervalAndTransform> void paint(final GraphicsContext graphics, final List<I> sources, final
	Interval targetInterval)
	{
		final double ox = targetInterval.min(0) + targetInterval.dimension(0) / 2;
		final double oy = targetInterval.min(1) + targetInterval.dimension(1) / 2;
		renderBoxHelper.setOrigin(ox, oy);

		final Path canvas = new Path();
		renderBoxHelper.renderCanvas(targetInterval, canvas);

		final Path activeFront    = new Path();
		final Path activeBack     = new Path();
		final Path inactiveFront  = new Path();
		final Path inactiveBack   = new Path();
		final Path highlightFront = new Path();
		final Path highlightBack  = new Path();

		boolean highlight           = false;
		Color   highlightFrontColor = null;
		Color   highlightBackColor  = null;

		for (int i = 0; i < sources.size(); ++i)
		{
			final IntervalAndTransform source = sources.get(i);
			if (highlightIndex == i)
			{
				highlight = true;
				if (highlighStartTime == -1)
					highlighStartTime = System.currentTimeMillis();
				double t = (System.currentTimeMillis() - highlighStartTime) / (double) highlightDuration;
				if (t >= 1)
				{
					highlightInProgress = false;
					highlightIndex = -1;
					highlighStartTime = -1;
					t = 1;
				}
				else
					highlightInProgress = true;

				final float  alpha;
				final double fadeInTime  = 0.2;
				final double fadeOutTime = 0.5;
				if (t <= fadeInTime)
					alpha = (float) Math.sin(Math.PI / 2 * t / fadeInTime);
				else if (t >= 1.0 - fadeOutTime)
					alpha = (float) Math.sin(Math.PI / 2 * (1.0 - t) / fadeOutTime);
				else
					alpha = 1;
				Color c = source.isVisible() ? activeFrontColor : inactiveFrontColor;
				int   r = (int) (alpha * 255 + (1 - alpha) * c.getRed());
				int   g = (int) (alpha * 255 + (1 - alpha) * c.getGreen());
				int   b = (int) (alpha * 255 + (1 - alpha) * c.getBlue());
				highlightFrontColor = Color.rgb(r, g, b, 1.0);
				c = source.isVisible() ? activeBackColor : inactiveBackColor;
				r = (int) (alpha * 255 + (1 - alpha) * c.getRed());
				g = (int) (alpha * 255 + (1 - alpha) * c.getGreen());
				b = (int) (alpha * 255 + (1 - alpha) * c.getBlue());
				highlightBackColor = Color.rgb(r, g, b, 1.0);
				renderBoxHelper.renderBox(
						source.getSourceInterval(),
						source.getSourceToViewer(),
						highlightFront,
						highlightBack
				                         );
			}
			else if (source.isVisible())
				renderBoxHelper.renderBox(
						source.getSourceInterval(),
						source.getSourceToViewer(),
						activeFront,
						activeBack
				                         );
			else
				renderBoxHelper.renderBox(
						source.getSourceInterval(),
						source.getSourceToViewer(),
						inactiveFront,
						inactiveBack
				                         );
		}

		if (highlightIndex >= sources.size())
		{
			highlightInProgress = false;
			highlightIndex = -1;
			highlighStartTime = -1;
		}

		//		graphics.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
		graphics.setStroke(inactiveBackColor);
		addPath(graphics, inactiveBack, graphics::stroke);
		graphics.setStroke(activeBackColor);
		addPath(graphics, activeBack, graphics::stroke);
		if (highlight)
		{
			graphics.setStroke(highlightBackColor);
			addPath(graphics, highlightBack, graphics::stroke);
		}
		graphics.setFill(canvasColor);
		addPath(graphics, canvas, graphics::fill);
		// graphics.fill( canvas );
		graphics.setStroke(inactiveFrontColor);
		addPath(graphics, inactiveFront, graphics::stroke);
		// graphics.draw( inactiveFront );
		graphics.setStroke(activeFrontColor);
		addPath(graphics, activeFront, graphics::stroke);
		// graphics.draw( activeFront );
		if (highlight)
		{
			graphics.setStroke(highlightFrontColor);
			addPath(graphics, highlightFront, graphics::stroke);
			// graphics.draw( highlightFront );
		}

		final IntervalAndTransform source = sources.get(0);
		final double               sX0    = source.getSourceInterval().min(0);
		final double               sY0    = source.getSourceInterval().min(1);
		final double               sZ0    = source.getSourceInterval().min(2);

		final double[] px = new double[] {sX0 + source.getSourceInterval().dimension(0) / 2, sY0, sZ0};
		final double[] py = new double[] {sX0, sY0 + source.getSourceInterval().dimension(1) / 2, sZ0};
		final double[] pz = new double[] {sX0, sY0, sZ0 + source.getSourceInterval().dimension(2) / 2};

		final double[] qx = new double[3];
		final double[] qy = new double[3];
		final double[] qz = new double[3];

		source.getSourceToViewer().apply(px, qx);
		source.getSourceToViewer().apply(py, qy);
		source.getSourceToViewer().apply(pz, qz);

		graphics.setFill(Color.WHITE);
		graphics.setFont(new Font("SansSerif", 8));
		graphics.fillText("x", (float) renderBoxHelper.perspectiveX(qx), (float) renderBoxHelper.perspectiveY(qx) - 2);
		graphics.fillText("y", (float) renderBoxHelper.perspectiveX(qy), (float) renderBoxHelper.perspectiveY(qy) - 2);
		graphics.fillText("z", (float) renderBoxHelper.perspectiveX(qz), (float) renderBoxHelper.perspectiveY(qz) - 2);
	}

	private static void addPath(
			final GraphicsContext graphics,
			final Path p,
			final Runnable strokeOrFillOperation)
	{
		graphics.beginPath();
		for (final PathElement el : p.getElements())
		{
			if (el instanceof MoveTo)
			{
				final MoveTo mt = (MoveTo) el;
				graphics.moveTo(mt.getX(), mt.getY());
			}
			if (el instanceof LineTo)
			{
				final LineTo mt = (LineTo) el;
				graphics.lineTo(mt.getX(), mt.getY());
			}
		}
		strokeOrFillOperation.run();
		graphics.closePath();
	}
}
