package bdv.bigcat.composite;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.ARGBType;
import bdv.viewer.Source;
import bdv.viewer.render.AccumulateProjector;
import bdv.viewer.render.AccumulateProjectorARGB;
import bdv.viewer.render.AccumulateProjectorFactory;
import bdv.viewer.render.VolatileProjector;

public class AccumulateProjectorCompositeARGB extends AccumulateProjector< ARGBType, ARGBType >
{

	public static AccumulateProjectorFactory< ARGBType > factory = new AccumulateProjectorFactory< ARGBType >()
	{
		@Override
		public AccumulateProjectorARGB createAccumulateProjector(
				final ArrayList< VolatileProjector > sourceProjectors,
				final ArrayList< Source< ? > > sour,
				final ArrayList< ? extends RandomAccessible< ARGBType > > sourceScreenImages,
				final RandomAccessibleInterval< ARGBType > targetScreenImages,
				final int numThreads,
				final ExecutorService executorService )
		{
			return new AccumulateProjectorARGB( sourceProjectors, sourceScreenImages, targetScreenImages, numThreads, executorService );
		}
	};

	final static private ARGBCompositeAlphaAdd composite = new ARGBCompositeAlphaAdd();

	public AccumulateProjectorCompositeARGB(
			final ArrayList< VolatileProjector > sourceProjectors,
			final ArrayList< ? extends RandomAccessible< ARGBType > > sources,
			final RandomAccessibleInterval< ARGBType > target,
			final int numThreads,
			final ExecutorService executorService )
	{
		super( sourceProjectors, sources, target, numThreads, executorService );
	}

	@Override
	protected void accumulate( final Cursor< ARGBType >[] accesses, final ARGBType target )
	{
		// TODO This is bad, we want to be able to paint on top of arbitrary background, e.g.
		// the sky line of NY or some sort of eye pleasing gradient, the outside code should
		// take care for clearing/ preparing the background and not leave it as is.
		target.set( 0xff000000 );

		for ( final Cursor< ARGBType > access : accesses )
			composite.compose( target, access.get() );
	}
}
