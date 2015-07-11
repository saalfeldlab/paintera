package bdv.bigcat.composite;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.Type;
import bdv.viewer.render.AccumulateProjector;
import bdv.viewer.render.VolatileProjector;

public class CompositeProjector< A extends Type< A > > extends AccumulateProjector< A, A >
{
	final protected ArrayList< Composite< A, A > > composites = new ArrayList< Composite< A, A > >();

	public CompositeProjector(
			final ArrayList< VolatileProjector > sourceProjectors,
			final ArrayList< ? extends RandomAccessible< A > > sources,
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

	// TODO I do not like that the list of accesses and composites are handled
	// independently, the list of composites being a member of the projector
	// and the list of accesses being passed to this method.  Instead, both
	// lists could be members of the projector, each thread having its own
	// projector or the list of composites would have to be passed to this
	// method.
	@Override
	protected void accumulate( final Cursor< A >[] accesses, final A target )
	{

		for ( int i = 0; i < composites.size(); ++i )
			composites.get( i ).compose( target, accesses[ i ].get() );
	}
}
