/**
 * License: GPL
 * <p>
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License 2 as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free
 * Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.janelia.saalfeldlab.paintera.composition;

import net.imglib2.type.numeric.ARGBType;

/**
 * Combines the Y-channel of a with the Cb and Cr channels of b, and mixes the result
 * into a weighted by b's alpha value.
 *
 * @author Stephan Saalfeld
 */
public class ARGBCompositeAlphaYCbCr implements Composite<ARGBType, ARGBType> {

	static private double det(final double[] a) {

		assert a.length == 9 : "Supports 3x3 double[] only.";

		return
				a[0] * a[4] * a[8] +
						a[3] * a[7] * a[2] +
						a[6] * a[1] * a[5] -
						a[2] * a[4] * a[6] -
						a[5] * a[7] * a[0] -
						a[8] * a[1] * a[3];
	}

	static private boolean invert(final double[] m) {

		assert m.length == 9 : "Supports 3x3 double[] only.";

		final double det = det(m);
		if (det == 0)
			return false;

		final double i00 = (m[4] * m[8] - m[5] * m[7]) / det;
		final double i01 = (m[2] * m[7] - m[1] * m[8]) / det;
		final double i02 = (m[1] * m[5] - m[2] * m[4]) / det;

		final double i10 = (m[5] * m[6] - m[3] * m[8]) / det;
		final double i11 = (m[0] * m[8] - m[2] * m[6]) / det;
		final double i12 = (m[2] * m[3] - m[0] * m[5]) / det;

		final double i20 = (m[3] * m[7] - m[4] * m[6]) / det;
		final double i21 = (m[1] * m[6] - m[0] * m[7]) / det;
		final double i22 = (m[0] * m[4] - m[1] * m[3]) / det;

		m[0] = i00;
		m[1] = i01;
		m[2] = i02;

		m[3] = i10;
		m[4] = i11;
		m[5] = i12;

		m[6] = i20;
		m[7] = i21;
		m[8] = i22;

		return true;
	}

	final static double[] rgb2ycbcr = new double[]{
			0.299, 0.587, 0.114,
			-0.168736, -0.331264, 0.5,
			0.5, -0.418688, -0.081312};

	final static double[] ycbcr2rgb = rgb2ycbcr.clone();

	static {
		invert(ycbcr2rgb);
	}

	static private double rgb2y(final double r, final double g, final double b) {

		return rgb2ycbcr[0] * r + rgb2ycbcr[1] * g + rgb2ycbcr[2] * b;
	}

	static private double rgb2cb(final double r, final double g, final double b) {

		return rgb2ycbcr[3] * r + rgb2ycbcr[4] * g + rgb2ycbcr[5] * b;
	}

	static private double rgb2cr(final double r, final double g, final double b) {

		return rgb2ycbcr[6] * r + rgb2ycbcr[7] * g + rgb2ycbcr[8] * b;
	}

	static private double ycbcr2r(final double y, final double cb, final double cr) {

		return ycbcr2rgb[0] * y + ycbcr2rgb[1] * cb + ycbcr2rgb[2] * cr;
	}

	static private double ycbcr2g(final double y, final double cb, final double cr) {

		return ycbcr2rgb[3] * y + ycbcr2rgb[4] * cb + ycbcr2rgb[5] * cr;
	}

	static private double ycbcr2b(final double y, final double cb, final double cr) {

		return ycbcr2rgb[6] * y + ycbcr2rgb[7] * cb + ycbcr2rgb[8] * cr;
	}

	@Override
	public void compose(final ARGBType a, final ARGBType b) {

		final int argbA = a.get();
		final int argbB = b.get();

		final double rA = ARGBType.red(argbA) / 255.0;
		final double rB = ARGBType.red(argbB) / 255.0;
		final double gA = ARGBType.green(argbA) / 255.0;
		final double gB = ARGBType.green(argbB) / 255.0;
		final double bA = ARGBType.blue(argbA) / 255.0;
		final double bB = ARGBType.blue(argbB) / 255.0;

		final double aA = ARGBType.alpha(argbA) / 255.0;
		final double aB = ARGBType.alpha(argbB) / 255.0;
		//		final double aB = ( rB == gB || gB == bB ) ? ARGBType.alpha( argbB ) / 255.0 * 0.5 : ARGBType.alpha(
		// argbB ) / 255.0 * 0.125;

		final double aTarget = aA + aB - aA * aB;

		final double yA = rgb2y(rA, gA, bA);
		final double cbA = rgb2cb(rA, gA, bA);
		final double crA = rgb2cr(rA, gA, bA);

		final double cbB = rgb2cb(rB, gB, bB);
		final double crB = rgb2cr(rB, gB, bB);

		final double aBInv = 1.0 - aB;

		final double cbTarget = cbA * aBInv + cbB * aB;
		final double crTarget = crA * aBInv + crB * aB;

		final double rTarget = ycbcr2r(yA, cbTarget, crTarget);
		final double gTarget = ycbcr2g(yA, cbTarget, crTarget);
		final double bTarget = ycbcr2b(yA, cbTarget, crTarget);

		a.set(ARGBType.rgba(
				Math.max(0, Math.min(255, (int)Math.round(rTarget * 255))),
				Math.max(0, Math.min(255, (int)Math.round(gTarget * 255))),
				Math.max(0, Math.min(255, (int)Math.round(bTarget * 255))),
				(int)(aTarget * 255)
		));
	}
}
