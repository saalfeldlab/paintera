/*
 * #%L
 * ImgLib2: a general-purpose, multidimensional image processing library.
 * %%
 * Copyright (C) 2009 - 2016 Tobias Pietzsch, Stephan Preibisch, Stephan Saalfeld,
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
package org.janelia.saalfeldlab.net.imglib2.view;

import kotlin.Triple;
import net.imglib2.Localizable;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealRandomAccessible;

public class RealRandomAccessibleTriple<A, B, C> implements RealRandomAccessible<Triple<A, B, C>> {

	final protected RealRandomAccessible<A> sourceA;

	final protected RealRandomAccessible<B> sourceB;

	final protected RealRandomAccessible<C> sourceC;

	public class RealRandomAccess implements net.imglib2.RealRandomAccess<Triple<A, B, C>> {

		final protected net.imglib2.RealRandomAccess<A> a;

		final protected net.imglib2.RealRandomAccess<B> b;

		final protected net.imglib2.RealRandomAccess<C> c;

		public RealRandomAccess() {

			a = sourceA.realRandomAccess();
			b = sourceB.realRandomAccess();
			c = sourceC.realRandomAccess();
		}

		@Override
		public void localize(final float[] position) {

			a.localize(position);
		}

		@Override
		public void localize(final double[] position) {

			a.localize(position);
		}

		@Override
		public float getFloatPosition(final int d) {

			return a.getFloatPosition(d);
		}

		@Override
		public double getDoublePosition(final int d) {

			return a.getDoublePosition(d);
		}

		@Override
		public int numDimensions() {

			return RealRandomAccessibleTriple.this.numDimensions();
		}

		@Override
		public void fwd(final int d) {

			a.fwd(d);
			b.fwd(d);
			c.fwd(d);
		}

		@Override
		public void bck(final int d) {

			a.bck(d);
			b.bck(d);
			c.bck(d);
		}

		@Override
		public void move(final int distance, final int d) {

			a.move(distance, d);
			b.move(distance, d);
			c.move(distance, d);
		}

		@Override
		public void move(final long distance, final int d) {

			a.move(distance, d);
			b.move(distance, d);
			c.move(distance, d);
		}

		@Override
		public void move(final float distance, final int d) {

			a.move(distance, d);
			b.move(distance, d);
			c.move(distance, d);
		}

		@Override
		public void move(final double distance, final int d) {

			a.move(distance, d);
			b.move(distance, d);
			c.move(distance, d);
		}

		@Override
		public void move(final Localizable localizable) {

			a.move(localizable);
			b.move(localizable);
			c.move(localizable);
		}

		@Override
		public void move(final int[] distance) {

			a.move(distance);
			b.move(distance);
			c.move(distance);
		}

		@Override
		public void move(final long[] distance) {

			a.move(distance);
			b.move(distance);
			c.move(distance);
		}

		@Override
		public void move(final RealLocalizable distance) {

			a.move(distance);
			b.move(distance);
			c.move(distance);
		}

		@Override
		public void move(final float[] distance) {

			a.move(distance);
			b.move(distance);
			c.move(distance);
		}

		@Override
		public void move(final double[] distance) {

			a.move(distance);
			b.move(distance);
			c.move(distance);
		}

		@Override
		public void setPosition(final Localizable localizable) {

			a.setPosition(localizable);
			b.setPosition(localizable);
			c.setPosition(localizable);
		}

		@Override
		public void setPosition(final int[] position) {

			a.setPosition(position);
			b.setPosition(position);
			c.setPosition(position);
		}

		@Override
		public void setPosition(final long[] position) {

			a.setPosition(position);
			b.setPosition(position);
			c.setPosition(position);
		}

		@Override
		public void setPosition(final float[] position) {

			a.setPosition(position);
			b.setPosition(position);
			c.setPosition(position);
		}

		@Override
		public void setPosition(final double[] position) {

			a.setPosition(position);
			b.setPosition(position);
			c.setPosition(position);
		}

		@Override
		public void setPosition(final RealLocalizable position) {

			a.setPosition(position);
			b.setPosition(position);
			c.setPosition(position);
		}

		@Override
		public void setPosition(final int position, final int d) {

			a.setPosition(position, d);
			b.setPosition(position, d);
			c.setPosition(position, d);
		}

		@Override
		public void setPosition(final long position, final int d) {

			a.setPosition(position, d);
			b.setPosition(position, d);
			c.setPosition(position, d);
		}

		@Override
		public void setPosition(final float position, final int d) {

			a.setPosition(position, d);
			b.setPosition(position, d);
			c.setPosition(position, d);
		}

		@Override
		public void setPosition(final double position, final int d) {

			a.setPosition(position, d);
			b.setPosition(position, d);
			c.setPosition(position, d);
		}

		@Override
		public Triple<A, B, C> get() {

			return new Triple<>(a.get(), b.get(), c.get());
		}

		@Override
		public RealRandomAccess copy() {

			final RealRandomAccess copy = new RealRandomAccess();
			copy.setPosition(this);
			return copy;
		}
	}

	public RealRandomAccessibleTriple(
			final RealRandomAccessible<A> sourceA,
			final RealRandomAccessible<B> sourceB,
			final RealRandomAccessible<C> sourceC) {

		this.sourceA = sourceA;
		this.sourceB = sourceB;
		this.sourceC = sourceC;
	}

	@Override public Triple<A, B, C> getType() {

		return new Triple<>(sourceA.getType(), sourceB.getType(), sourceC.getType());
	}

	@Override
	public int numDimensions() {

		return sourceA.numDimensions();
	}

	@Override
	public RealRandomAccess realRandomAccess() {

		return new RealRandomAccess();
	}

	@Override
	public RealRandomAccess realRandomAccess(final RealInterval interval) {

		return new RealRandomAccess();
	}
}
