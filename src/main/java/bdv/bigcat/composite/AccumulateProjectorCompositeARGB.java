package bdv.bigcat.composite;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import bdv.viewer.Source;
import bdv.viewer.render.AccumulateProjector;
import bdv.viewer.render.AccumulateProjectorFactory;
import bdv.viewer.render.VolatileProjector;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.ARGBType;

public class AccumulateProjectorCompositeARGB extends AccumulateProjector< ARGBType, ARGBType >
{
	public static AccumulateProjectorFactory< ARGBType > factory = new AccumulateProjectorFactory< ARGBType >()
	{
		@Override
		public AccumulateProjectorCompositeARGB createAccumulateProjector(
				ArrayList< VolatileProjector > sourceProjectors,
				ArrayList< Source< ? > > sources,
				ArrayList< ? extends RandomAccessible< ? extends ARGBType > > sourceScreenImages,
				RandomAccessibleInterval< ARGBType > targetScreenImage,
				int numThreads,
				ExecutorService executorService )
		{
			return new AccumulateProjectorCompositeARGB(
					sourceProjectors,
					sourceScreenImages,
					targetScreenImage,
					numThreads,
					executorService );
		}
	};

	final static private ARGBCompositeAlphaAdd composite = new ARGBCompositeAlphaAdd();

	public AccumulateProjectorCompositeARGB(
			final ArrayList< VolatileProjector > sourceProjectors,
			final ArrayList< ? extends RandomAccessible< ? extends ARGBType > > sources,
			final RandomAccessibleInterval< ARGBType > target,
			final int numThreads,
			final ExecutorService executorService )
	{
		super( sourceProjectors, sources, target, numThreads, executorService );
	}

	@Override
	protected void accumulate( final Cursor< ? extends ARGBType >[] accesses, final ARGBType target )
	{
		// TODO This is bad, we want to be able to paint on top of arbitrary background, e.g.
		// the sky line of NY or some sort of eye pleasing gradient, the outside code should
		// take care for clearing/ preparing the background and not leave it as is.
		target.set( 0xff000000 );

		for ( final Cursor< ? extends ARGBType > access : accesses )
			composite.compose( target, access.get() );
	}
}
