/*
 * #%L
 * ImgLib2: a general-purpose, multidimensional image processing library.
 * %%
 * Copyright (C) 2009 - 2018 Tobias Pietzsch, Stephan Preibisch, Stephan Saalfeld,
 * John Bogovic, Albert Cardona, Barry DeZonia, Christian Dietz, Jan Funke,
 * Aivar Grislis, Jonathan Hale, Grant Harris, Stefan Helfrich, Mark Hiner,
 * Martin Horn, Steffen Jaensch, Lee Kamentsky, Larry Lindsey, Melissa Linkert,
 * Mark Longair, Brian Northan, Nick Perry, Curtis Rueden, Johannes Schindelin,
 * Jean-Yves Tinevez and Michael Zinsmaier.
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

package org.janelia.saalfeldlab.net.imglib2.outofbounds;

import net.imglib2.AbstractRealLocalizable;
import net.imglib2.Localizable;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.outofbounds.RealOutOfBounds;
import net.imglib2.type.Type;

public abstract class AbstractRealOutOfBoundsValue<T extends Type<T>> extends AbstractRealLocalizable implements RealOutOfBounds<T> {

	final protected RealRandomAccess<T> sampler;

	final protected double[] min, max;

	final protected boolean[] dimIsOutOfBounds;

	protected boolean isOutOfBounds = false;

	protected AbstractRealOutOfBoundsValue(final AbstractRealOutOfBoundsValue<T> outOfBounds) {

		super(outOfBounds.numDimensions());
		this.sampler = outOfBounds.sampler.copyRealRandomAccess();
		min = new double[n];
		max = new double[n];
		dimIsOutOfBounds = new boolean[n];
		for (int d = 0; d < n; ++d) {
			min[d] = outOfBounds.min[d];
			max[d] = outOfBounds.max[d];
			position[d] = outOfBounds.position[d];
			dimIsOutOfBounds[d] = outOfBounds.dimIsOutOfBounds[d];
		}
	}

	public <F extends RealInterval & RealRandomAccessible<T>> AbstractRealOutOfBoundsValue(final F f) {

		super(f.numDimensions());
		this.sampler = f.realRandomAccess();
		min = new double[n];
		f.realMin(min);
		max = new double[n];
		f.realMax(max);
		dimIsOutOfBounds = new boolean[n];
	}

	protected void checkOutOfBounds() {

		for (int d = 0; d < n; ++d) {
			if (dimIsOutOfBounds[d]) {
				isOutOfBounds = true;
				return;
			}
		}
		isOutOfBounds = false;
	}

	/* OutOfBounds */

	@Override
	public boolean isOutOfBounds() {

		checkOutOfBounds();
		return isOutOfBounds;
	}

	/* Positionable */

	@Override
	public void fwd(final int dim) {

		final boolean wasOutOfBounds = isOutOfBounds;
		++position[dim];
		updateOutOfBounds(dim);

		if (isOutOfBounds)
			return;
		if (wasOutOfBounds)
			sampler.setPosition(position);
		else
			sampler.fwd(dim);
	}

	@Override
	public void bck(final int dim) {

		final boolean wasOutOfBounds = isOutOfBounds;
		--position[dim];
		updateOutOfBounds(dim);

		if (isOutOfBounds)
			return;
		if (wasOutOfBounds)
			sampler.setPosition(position);
		else
			sampler.bck(dim);
	}

	private void updateOutOfBounds(final int dim) {

		if (position[dim] >= min[dim] && position[dim] <= max[dim]) {
			dimIsOutOfBounds[dim] = false;
			checkOutOfBounds();
		} else {
			dimIsOutOfBounds[dim] = isOutOfBounds = true;
		}
	}

	@Override
	public void move(final long distance, final int dim) {

		move((double)distance, dim);
	}

	@Override
	public void move(final int distance, final int dim) {

		move((double)distance, dim);
	}

	@Override
	public void move(final Localizable localizable) {

		for (int d = 0; d < n; ++d) {
			move(localizable.getLongPosition(d), d);
		}
	}

	@Override
	public void move(final int[] distance) {

		for (int d = 0; d < n; ++d) {
			move(distance[d], d);
		}
	}

	@Override
	public void move(final long[] distance) {

		for (int d = 0; d < n; ++d) {
			move(distance[d], d);
		}
	}

	@Override
	public void move(final double distance, final int dim) {

		setPosition(position[dim] + distance, dim);
	}

	@Override
	public void move(final float distance, final int dim) {

		move((double)distance, dim);
	}

	@Override
	public void move(final RealLocalizable localizable) {

		for (int d = 0; d < n; ++d) {
			move(localizable.getDoublePosition(d), d);
		}
	}

	@Override
	public void move(final float[] distance) {

		for (int d = 0; d < n; ++d) {
			move(distance[d], d);
		}
	}

	@Override
	public void move(final double[] distance) {

		for (int d = 0; d < n; ++d) {
			move(distance[d], d);
		}
	}

	@Override
	public void setPosition(final double position, final int dim) {

		this.position[dim] = position;
		if (position < min[dim] || position > max[dim])
			dimIsOutOfBounds[dim] = isOutOfBounds = true;
		else if (isOutOfBounds) {
			dimIsOutOfBounds[dim] = false;
			checkOutOfBounds();
			if (!isOutOfBounds)
				sampler.setPosition(this.position);
		} else
			sampler.setPosition(position, dim);
	}

	@Override
	public void setPosition(final Localizable localizable) {

		for (int d = 0; d < n; ++d) {
			setPosition(localizable.getLongPosition(d), d);
		}
	}

	@Override
	public void setPosition(final int[] position) {

		for (int d = 0; d < position.length; ++d) {
			setPosition(position[d], d);
		}
	}

	@Override
	public void setPosition(final long[] position) {

		for (int d = 0; d < position.length; ++d) {
			setPosition(position[d], d);
		}
	}

	@Override
	public void setPosition(final RealLocalizable localizable) {

		for (int d = 0; d < n; ++d) {
			setPosition(localizable.getDoublePosition(d), d);
		}
	}

	@Override
	public void setPosition(final float[] position) {

		for (int d = 0; d < position.length; ++d) {
			setPosition(position[d], d);
		}
	}

	@Override
	public void setPosition(final double[] position) {

		for (int d = 0; d < position.length; ++d) {
			setPosition(position[d], d);
		}
	}

	@Override
	public void setPosition(final long position, final int dim) {

		setPosition((double)position, dim);
	}

	@Override
	public void setPosition(final int position, final int dim) {

		setPosition((double)position, dim);
	}

	@Override
	public void setPosition(final float position, final int dim) {

		setPosition((double)position, dim);
	}

	@Override
	public void localize(final int[] pos) {

		for (int d = 0; d < n; ++d) {
			pos[d] = getIntPosition(d);
		}
	}

	@Override
	public void localize(final long[] pos) {

		for (int d = 0; d < n; ++d) {
			pos[d] = getLongPosition(d);
		}
	}

	@Override
	public int getIntPosition(final int d) {

		return (int)getLongPosition(d);
	}

	@Override
	public long getLongPosition(final int d) {

		return Math.round(position[d]);
	}
}
