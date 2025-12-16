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
 * Multiplies b by b's alpha value and adds it to a.
 *
 * @author Stephan Saalfeld
 */
public class ARGBCompositeAlphaAdd implements Composite<ARGBType, ARGBType> {

	@Override
	public void compose(final ARGBType a, final ARGBType b) {

		final int argbA = a.get();
		final int argbB = b.get();

		final int rA = ARGBType.red(argbA);
		final int rB = ARGBType.red(argbB);
		final int gA = ARGBType.green(argbA);
		final int gB = ARGBType.green(argbB);
		final int bA = ARGBType.blue(argbA);
		final int bB = ARGBType.blue(argbB);

		final double aA = ARGBType.alpha(argbA) / 255.0;
		final double aB = ARGBType.alpha(argbB) / 255.0;

		final double aTarget = aA + aB - aA * aB;

		final int rTarget = Math.min(255, (int)Math.round(rA + rB * aB));
		final int gTarget = Math.min(255, (int)Math.round(gA + gB * aB));
		final int bTarget = Math.min(255, (int)Math.round(bA + bB * aB));

		a.set(ARGBType.rgba(rTarget, gTarget, bTarget, (int)(aTarget * 255)));
	}
}
