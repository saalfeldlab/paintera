package org.janelia.saalfeldlab.paintera.composition;

import bdv.fx.viewer.PriorityExecutorService;
import bdv.fx.viewer.project.AccumulateProjectorFactory;
import bdv.fx.viewer.project.VolatileProjector;
import bdv.viewer.Source;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.Type;

import java.util.ArrayList;
import java.util.Map;

public class ClearingCompositeProjector<A extends Type<A>> extends CompositeProjector<A>
{

	public static class ClearingCompositeProjectorFactory<A extends Type<A>> implements AccumulateProjectorFactory<A>
	{
		private final Map<Source<?>, Composite<A, A>> composites;

		private final A clearValue;

		/**
		 * Constructor with a map that associates sources and {@link Composite Composites}.
		 *
		 * @param composites
		 */
		public ClearingCompositeProjectorFactory(final Map<Source<?>, Composite<A, A>> composites, final A clearValue)
		{
			this.composites = composites;
			this.clearValue = clearValue;
		}

		@Override
		public VolatileProjector createAccumulateProjector(
				final ArrayList<VolatileProjector> sourceProjectors,
				final ArrayList<Source<?>> sources,
				final ArrayList<? extends RandomAccessible<? extends A>> sourceScreenImages,
				final RandomAccessibleInterval<A> targetScreenImage,
				final int numThreads,
				final PriorityExecutorService executorService)
		{
			final ClearingCompositeProjector<A> projector = new ClearingCompositeProjector<>(
					sourceProjectors,
					sourceScreenImages,
					targetScreenImage,
					clearValue,
					numThreads,
					executorService
			);

			final ArrayList<Composite<A, A>> activeComposites = new ArrayList<>();
			for (final Source<?> activeSource : sources)
				activeComposites.add(composites.get(activeSource));

			projector.setComposites(activeComposites);

			return projector;
		}
	}

	private final A clearValue;

	public ClearingCompositeProjector(
			final ArrayList<VolatileProjector> sourceProjectors,
			final ArrayList<? extends RandomAccessible<? extends A>> sources,
			final RandomAccessibleInterval<A> target,
			final A clearValue,
			final int numThreads,
			final PriorityExecutorService executorService)
	{
		super(sourceProjectors, sources, target, numThreads, executorService);
		this.clearValue = clearValue;
	}

	@Override
	protected void accumulate(final Cursor<? extends A>[] accesses, final A t)
	{
		t.set(clearValue);
		for (int i = 0; i < composites.size(); ++i)
			composites.get(i).compose(t, accesses[i].get());
	}

}
