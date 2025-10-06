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
 * Composes the RGB from A, and uses the Alpha from B
 */
public class AlphaCopy implements Composite<ARGBType, ARGBType> {


	@Override
	public void compose(final ARGBType a, final ARGBType b) {

		final int argbA = a.get();
		final int argbB = b.get();

		final int redA = ARGBType.red(argbA);
		final int greenA = ARGBType.green(argbA);
		final int blueA = ARGBType.blue(argbA);

		final double alphaB = ARGBType.alpha(argbB);

		a.set(ARGBType.rgba(redA, greenA, blueA, alphaB));
	}
}
