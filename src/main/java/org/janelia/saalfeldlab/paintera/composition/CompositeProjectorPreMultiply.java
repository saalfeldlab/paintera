package org.janelia.saalfeldlab.paintera.composition;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import bdv.viewer.Source;
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

/**
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 * @author Philipp Hanslovsky
 */
@SuppressWarnings("restriction")
public class CompositeProjectorPreMultiply extends AccumulateProjector<ARGBType, ARGBType>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static class CompositeProjectorFactory implements AccumulateProjectorFactory<ARGBType>
	{
		final private Map<Source<?>, Composite<ARGBType, ARGBType>> composites;

		/**
		 * Constructor with a map that associates sources and {@link Composite Composites}.
		 *
		 * @param composites
		 */
		public CompositeProjectorFactory(final Map<Source<?>, Composite<ARGBType, ARGBType>> composites)
		{
			this.composites = composites;
		}

		@Override
		public VolatileProjector createAccumulateProjector(
				final ArrayList<VolatileProjector> sourceProjectors,
				final ArrayList<Source<?>> sources,
				final ArrayList<? extends RandomAccessible<? extends ARGBType>> sourceScreenImages,
				final RandomAccessibleInterval<ARGBType> targetScreenImage,
				final int numThreads,
				final ExecutorService executorService)
		{
			final CompositeProjectorPreMultiply projector = new CompositeProjectorPreMultiply(
					sourceProjectors,
					sourceScreenImages,
					targetScreenImage,
					numThreads,
					executorService
			);

			final ArrayList<Composite<ARGBType, ARGBType>> activeComposites = new ArrayList<>();
			for (final Source<?> activeSource : sources)
				activeComposites.add(composites.get(activeSource));

			projector.setComposites(activeComposites);

			return projector;
		}
	}

	final protected ArrayList<Composite<ARGBType, ARGBType>> composites = new ArrayList<>();

	public CompositeProjectorPreMultiply(
			final ArrayList<VolatileProjector> sourceProjectors,
			final ArrayList<? extends RandomAccessible<? extends ARGBType>> sources,
			final RandomAccessibleInterval<ARGBType> target,
			final int numThreads,
			final ExecutorService executorService)
	{
		super(sourceProjectors, sources, target, numThreads, executorService);
		LOG.debug("Creating {}", this.getClass().getName());
	}

	public void setComposites(final List<Composite<ARGBType, ARGBType>> composites)
	{
		this.composites.clear();
		this.composites.addAll(composites);
	}

	@Override
	protected void accumulate(final Cursor<? extends ARGBType>[] accesses, final ARGBType t)
	{
		t.set(0);
		for (int i = 0; i < composites.size(); ++i)
			composites.get(i).compose(t, accesses[i].get());
		final int nonpre = t.get();
		t.set(PixelUtils.NonPretoPre(nonpre));
		//		@SuppressWarnings( "restriction" )
		//		final int pre = PixelUtils.NonPretoPre( nonpre );
		//		final int a = nonpre & 0xff;
		//		if ( a == 0xff )
		//			return;
		//		if ( a == 0x00 )
		//			return;
		//		int r = nonpre >> 24 & 0xff;
		//		int g = nonpre >> 16 & 0xff;
		//		int b = nonpre >> 8 & 0xff;
		//		r = ( r * a + 0x7f ) / 0xff;
		//		g = ( g * a + 0x7f ) / 0xff;
		//		b = ( b * a + 0x7f ) / 0xff;
		//		final int pre = a << 0 | r << 24 | g << 16 | b << 8;
		//		final int pre = PixelUtils.NonPretoPre( nonpre );
		//		t.set( pre );
	}
}
