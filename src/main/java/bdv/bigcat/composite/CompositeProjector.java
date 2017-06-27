package bdv.bigcat.composite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import bdv.viewer.Source;
import bdv.viewer.render.AccumulateProjector;
import bdv.viewer.render.AccumulateProjectorFactory;
import bdv.viewer.render.VolatileProjector;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.Type;

/**
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class CompositeProjector< A extends Type< A > > extends AccumulateProjector< A, A >
{
	public static class CompositeProjectorFactory< A extends Type< A > > implements AccumulateProjectorFactory< A >
	{
		final private Map< Source< ? extends A >, Composite< A, A > > composites;

		/**
		 * Constructor with a map that associates sources and {@link Composite Composites}.
		 *
		 * @param composites
		 */
		public CompositeProjectorFactory( final Map< Source< ? extends A >, Composite< A, A > > composites )
		{
			this.composites = composites;
		}

		@Override
		public VolatileProjector createAccumulateProjector(
				final ArrayList< VolatileProjector > sourceProjectors,
				final ArrayList< Source< ? > > sources,
				final ArrayList< ? extends RandomAccessible< ? extends A > > sourceScreenImages,
				final RandomAccessibleInterval< A > targetScreenImage,
				final int numThreads,
				final ExecutorService executorService )
		{
			final CompositeProjector< A > projector = new CompositeProjector< A >(
					sourceProjectors,
					sourceScreenImages,
					targetScreenImage,
					numThreads,
					executorService );

			final ArrayList< Composite< A, A > > activeComposites = new ArrayList< Composite< A, A > >();
			for ( final Source< ? > activeSource : sources )
				activeComposites.add( composites.get( activeSource ) );

			projector.setComposites( activeComposites );

			return projector;
		}
	}

	final protected ArrayList< Composite< A, A > > composites = new ArrayList< Composite< A, A > >();

	public CompositeProjector(
			final ArrayList< VolatileProjector > sourceProjectors,
			final ArrayList< ? extends RandomAccessible< ? extends A > > sources,
			final RandomAccessibleInterval< A > target,
			final int numThreads,
			final ExecutorService executorService )
	{
		super( sourceProjectors, sources, target, numThreads, executorService );
	}

	public void setComposites( final List< Composite< A, A > > composites )
	{
		this.composites.clear();
		this.composites.addAll( composites );
	}

	@Override
	protected void accumulate( final Cursor< ? extends A >[] accesses, final A t )
	{
		for ( int i = 0; i < composites.size(); ++i )
			composites.get( i ).compose( t, accesses[ i ].get() );
	}
}
