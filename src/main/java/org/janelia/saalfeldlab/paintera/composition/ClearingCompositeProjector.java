package org.janelia.saalfeldlab.paintera.composition;

import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.render.AccumulateProjectorFactory;
import bdv.viewer.render.VolatileProjector;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class ClearingCompositeProjector<A extends Type<A>> extends CompositeProjector<A> {

	public static class ClearingCompositeProjectorFactory<A extends Type<A>> implements AccumulateProjectorFactory<A> {

		private final Map<Source<?>, Composite<A, A>> composites;

		private final A clearValue;

		/**
		 * Constructor with a map that associates sources and {@link Composite Composites}.
		 *
		 * @param composites
		 */
		public ClearingCompositeProjectorFactory(final Map<Source<?>, Composite<A, A>> composites, final A clearValue) {

			this.composites = composites;
			this.clearValue = clearValue;
		}

		@Override
		public VolatileProjector createProjector(
				List<VolatileProjector> sourceProjectors,
				List<SourceAndConverter<?>> sources,
				List<? extends RandomAccessible<? extends A>> sourceScreenImages,
				RandomAccessibleInterval<A> targetScreenImage,
				int numThreads,
				ExecutorService executorService) {

			final ClearingCompositeProjector<A> projector = new ClearingCompositeProjector<>(
					sourceProjectors,
					sourceScreenImages,
					targetScreenImage,
					clearValue
			);

			final ArrayList<Composite<A, A>> activeComposites = new ArrayList<>();
			for (final var activeSource : sources) {
				activeComposites.add(composites.get(activeSource.getSpimSource()));
			}

			projector.setComposites(activeComposites);

			return projector;
		}
	}

	private final A clearValue;

	public ClearingCompositeProjector(
			final List<VolatileProjector> sourceProjectors,
			final List<? extends RandomAccessible<? extends A>> sources,
			final RandomAccessibleInterval<A> target,
			final A clearValue) {

		super(sourceProjectors, sources, target);
		this.clearValue = clearValue;
	}

	@Override
	protected void accumulate(final Cursor<? extends A>[] accesses, final A t) {

		t.set(clearValue);
		for (int i = 0; i < composites.size(); ++i) {
			composites.get(i).compose(t, accesses[i].get());
		}
	}

}
