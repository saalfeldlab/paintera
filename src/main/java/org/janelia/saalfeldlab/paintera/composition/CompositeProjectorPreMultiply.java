package org.janelia.saalfeldlab.paintera.composition;

import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.render.AccumulateProjector;
import bdv.viewer.render.AccumulateProjectorFactory;
import bdv.viewer.render.VolatileProjector;
import com.sun.javafx.image.PixelUtils;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.ARGBType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * @author Stephan Saalfeld
 * @author Philipp Hanslovsky
 */
@SuppressWarnings("restriction")
public class CompositeProjectorPreMultiply extends AccumulateProjector<ARGBType, ARGBType> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static class CompositeProjectorFactory implements AccumulateProjectorFactory<ARGBType> {

		final private Map<Source<?>, Composite<ARGBType, ARGBType>> composites;

		/**
		 * Constructor with a map that associates sources and {@link Composite Composites}.
		 *
		 * @param composites
		 */
		public CompositeProjectorFactory(final Map<Source<?>, Composite<ARGBType, ARGBType>> composites) {

			this.composites = composites;
		}

		@Override
		public VolatileProjector createProjector(
				final List<VolatileProjector> sourceProjectors,
				final List<SourceAndConverter<?>> sources,
				final List<? extends RandomAccessible<? extends ARGBType>> sourceScreenImages,
				final RandomAccessibleInterval<ARGBType> targetScreenImage,
				final int numThreads,
				final ExecutorService executorService) {

			final CompositeProjectorPreMultiply projector = new CompositeProjectorPreMultiply(
					sourceProjectors,
					sourceScreenImages,
					targetScreenImage
			);

			final ArrayList<Composite<ARGBType, ARGBType>> activeComposites = new ArrayList<>();
			for (final var activeSource : sources) {
				activeComposites.add(composites.get(activeSource.getSpimSource()));
			}

			projector.setComposites(activeComposites);

			return projector;
		}
	}

	final protected ArrayList<Composite<ARGBType, ARGBType>> composites = new ArrayList<>();

	public CompositeProjectorPreMultiply(
			final List<VolatileProjector> sourceProjectors,
			final List<? extends RandomAccessible<? extends ARGBType>> sources,
			final RandomAccessibleInterval<ARGBType> target) {

		super(sourceProjectors, sources, target);
		LOG.debug("Creating {}", this.getClass().getName());
	}

	public void setComposites(final List<Composite<ARGBType, ARGBType>> composites) {

		this.composites.clear();
		this.composites.addAll(composites);
	}

	@Override
	protected void accumulate(final Cursor<? extends ARGBType>[] accesses, final ARGBType t) {

		t.set(0);
		for (int i = 0; i < composites.size(); ++i) {
			composites.get(i).compose(t, accesses[i].get());
		}
		t.set(PixelUtils.NonPretoPre(t.get()));
	}
}
