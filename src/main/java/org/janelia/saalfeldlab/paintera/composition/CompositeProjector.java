package org.janelia.saalfeldlab.paintera.composition;

import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.render.AccumulateProjector;
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

/**
 * @author Stephan Saalfeld
 */
public class CompositeProjector<A extends Type<A>> extends AccumulateProjector<A, A> {

	public static class CompositeProjectorFactory<A extends Type<A>> implements AccumulateProjectorFactory<A> {

		final private Map<Source<?>, Composite<A, A>> composites;

		/**
		 * Constructor with a map that associates sources and {@link Composite Composites}.
		 *
		 * @param composites
		 */
		public CompositeProjectorFactory(final Map<Source<?>, Composite<A, A>> composites) {

			this.composites = composites;
		}

		@Override
		public VolatileProjector createProjector(
				List<VolatileProjector> sourceProjectors,
				List<SourceAndConverter<?>> sources,
				List<? extends RandomAccessible<? extends A>> sourceScreenImages,
				RandomAccessibleInterval<A> targetScreenImage,
				int numThreads,
				ExecutorService executorService) {

			final CompositeProjector<A> projector = new CompositeProjector<>(
					sourceProjectors,
					sourceScreenImages,
					targetScreenImage
			);

			final ArrayList<Composite<A, A>> activeComposites = new ArrayList<>();
			for (final var activeSource : sources) {
				activeComposites.add(composites.get(activeSource.getSpimSource()));
			}

			projector.setComposites(activeComposites);

			return projector;
		}
	}

	final protected ArrayList<Composite<A, A>> composites = new ArrayList<>();

	public CompositeProjector(
			final List<VolatileProjector> sourceProjectors,
			final List<? extends RandomAccessible<? extends A>> sources,
			final RandomAccessibleInterval<A> target) {

		super(sourceProjectors, sources, target);
	}

	public void setComposites(final List<Composite<A, A>> composites) {

		this.composites.clear();
		this.composites.addAll(composites);
	}

	@Override
	protected void accumulate(final Cursor<? extends A>[] accesses, final A t) {

		for (int i = 0; i < composites.size(); ++i) {
			composites.get(i).compose(t, accesses[i].get());
		}
	}
}
