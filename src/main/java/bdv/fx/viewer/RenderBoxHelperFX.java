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

import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * Helper for rendering overlay boxes.
 *
 * @author Stephan Saalfeld
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
public class RenderBoxHelperFX
{
	/**
	 * distance from the eye to the projection plane z=0.
	 */
	private double depth = 10.0;

	/**
	 * scale the 2D projection of the overlay box by this factor.
	 */
	private double scale = 0.1;

	private final double[] origin = new double[3];

	public void setScale(final double scale)
	{
		this.scale = scale;
	}

	public void setDepth(final double depth)
	{
		this.depth = depth;
		origin[2] = -depth;
	}

	public void setOrigin(final double x, final double y)
	{
		origin[0] = x;
		origin[1] = y;
	}

	/**
	 * @param p
	 * 		point to project
	 *
	 * @return X coordinate of projected point
	 */
	public double perspectiveX(final double[] p)
	{
		return scale * (p[0] - origin[0]) / (p[2] - origin[2]) * depth;
	}

	/**
	 * @param p
	 * 		point to project
	 *
	 * @return Y coordinate of projected point
	 */
	public double perspectiveY(final double[] p)
	{
		return scale * (p[1] - origin[1]) / (p[2] - origin[2]) * depth;
	}

	public void splitEdge(final double[] a, final double[] b, final Path before, final Path behind)
	{
		final double[] t = new double[3];
		if (a[2] <= 0)
		{
			before.getElements().add(new MoveTo(perspectiveX(a), perspectiveY(a)));
			//			before.moveTo( perspectiveX( a ), perspectiveY( a ) );
			if (b[2] <= 0)
				before.getElements().add(new LineTo(perspectiveX(b), perspectiveY(b)));
				//				before.lineTo( perspectiveX( b ), perspectiveY( b ) );
			else
			{
				final double d = a[2] / (a[2] - b[2]);
				t[0] = (b[0] - a[0]) * d + a[0];
				t[1] = (b[1] - a[1]) * d + a[1];
				before.getElements().add(new LineTo(perspectiveX(t), perspectiveY(t)));
				behind.getElements().add(new MoveTo(perspectiveX(t), perspectiveY(t)));
				behind.getElements().add(new LineTo(perspectiveX(b), perspectiveY(b)));
			}
		}
		else
		{
			behind.getElements().add(new MoveTo(perspectiveX(a), perspectiveY(a)));
			if (b[2] > 0)
				behind.getElements().add(new LineTo(perspectiveX(b), perspectiveY(b)));
			else
			{
				final double d = a[2] / (a[2] - b[2]);
				t[0] = (b[0] - a[0]) * d + a[0];
				t[1] = (b[1] - a[1]) * d + a[1];
				behind.getElements().add(new LineTo(perspectiveX(t), perspectiveY(t)));
				before.getElements().add(new MoveTo(perspectiveX(t), perspectiveY(t)));
				before.getElements().add(new LineTo(perspectiveX(b), perspectiveY(b)));
			}
		}
	}

	public void renderBox(final Interval sourceInterval, final AffineTransform3D transform, final Path front, final
	Path back)
	{
		final double sX0 = sourceInterval.min(0);
		final double sX1 = sourceInterval.max(0);
		final double sY0 = sourceInterval.min(1);
		final double sY1 = sourceInterval.max(1);
		final double sZ0 = sourceInterval.min(2);
		final double sZ1 = sourceInterval.max(2);

		final double[] p000 = new double[] {sX0, sY0, sZ0};
		final double[] p100 = new double[] {sX1, sY0, sZ0};
		final double[] p010 = new double[] {sX0, sY1, sZ0};
		final double[] p110 = new double[] {sX1, sY1, sZ0};
		final double[] p001 = new double[] {sX0, sY0, sZ1};
		final double[] p101 = new double[] {sX1, sY0, sZ1};
		final double[] p011 = new double[] {sX0, sY1, sZ1};
		final double[] p111 = new double[] {sX1, sY1, sZ1};

		final double[] q000 = new double[3];
		final double[] q100 = new double[3];
		final double[] q010 = new double[3];
		final double[] q110 = new double[3];
		final double[] q001 = new double[3];
		final double[] q101 = new double[3];
		final double[] q011 = new double[3];
		final double[] q111 = new double[3];

		transform.apply(p000, q000);
		transform.apply(p100, q100);
		transform.apply(p010, q010);
		transform.apply(p110, q110);
		transform.apply(p001, q001);
		transform.apply(p101, q101);
		transform.apply(p011, q011);
		transform.apply(p111, q111);

		splitEdge(q000, q100, front, back);
		splitEdge(q100, q110, front, back);
		splitEdge(q110, q010, front, back);
		splitEdge(q010, q000, front, back);

		splitEdge(q001, q101, front, back);
		splitEdge(q101, q111, front, back);
		splitEdge(q111, q011, front, back);
		splitEdge(q011, q001, front, back);

		splitEdge(q000, q001, front, back);
		splitEdge(q100, q101, front, back);
		splitEdge(q110, q111, front, back);
		splitEdge(q010, q011, front, back);
	}

	public void renderCanvas(final Interval targetInterval, final Path canvas)
	{
		final double tX0 = targetInterval.min(0);
		final double tX1 = targetInterval.max(0);
		final double tY0 = targetInterval.min(1);
		final double tY1 = targetInterval.max(1);

		final double[] c000 = new double[] {tX0, tY0, 0};
		final double[] c100 = new double[] {tX1, tY0, 0};
		final double[] c010 = new double[] {tX0, tY1, 0};
		final double[] c110 = new double[] {tX1, tY1, 0};

		canvas.getElements().add(new MoveTo(perspectiveX(c000), perspectiveY(c000)));
		canvas.getElements().add(new LineTo(perspectiveX(c100), perspectiveY(c100)));
		canvas.getElements().add(new LineTo(perspectiveX(c110), perspectiveY(c110)));
		canvas.getElements().add(new LineTo(perspectiveX(c010), perspectiveY(c010)));
		canvas.getElements().add(new ClosePath());
	}
}
