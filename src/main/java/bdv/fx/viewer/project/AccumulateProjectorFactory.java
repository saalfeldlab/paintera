package bdv.fx.viewer.project;

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
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import bdv.fx.viewer.PriorityExecutorService;
import bdv.viewer.Source;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;

public interface AccumulateProjectorFactory< A >
{
	/**
	 * @param sourceProjectors
	 *            projectors that will be used to render {@code sources}.
	 * @param sources
	 *            sources to identify which channels are being rendered
	 * @param sourceScreenImages
	 *            rendered images that will be accumulated into
	 *            {@code target}.
	 * @param targetScreenImage
	 *            final image to render.
	 * @param numThreads
	 *            how many threads to use for rendering.
	 * @param executorService
	 *            {@link ExecutorService} to use for rendering. may be null.
	 */
	public VolatileProjector createAccumulateProjector(
			final ArrayList< VolatileProjector > sourceProjectors,
			final ArrayList< Source< ? > > sources,
			final ArrayList< ? extends RandomAccessible< ? extends A > > sourceScreenImages,
			final RandomAccessibleInterval< A > targetScreenImage,
			final int numThreads,
			final PriorityExecutorService executorService );
}
