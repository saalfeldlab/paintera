package bdv.bigcat.viewer.atlas.data;

import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import bdv.bigcat.ui.LabelMultisetSource;
import bdv.bigcat.ui.VolatileLabelMultisetSource;
import bdv.bigcat.viewer.atlas.solver.action.Action;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentWithHistory;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.bigcat.viewer.stream.AbstractHighlightingARGBStream;
import bdv.bigcat.viewer.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import bdv.bigcat.viewer.viewer3d.marchingCubes.ForegroundCheck;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset.Entry;
import bdv.labels.labelset.VolatileLabelMultisetType;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import gnu.trove.map.hash.TLongLongHashMap;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

public class HDF5LabelMultisetSourceSpec implements RenderableLabelSpec< LabelMultisetType, VolatileLabelMultisetType >
{

	private final AbstractHighlightingARGBStream stream;

	private final H5LabelMultisetSetupImageLoader loader;

	private final SelectedIds selectedIds = new SelectedIds();

	private final FragmentSegmentAssignmentState< ? > assignment;

	private final String name;

	private final String uri;

	private final int setupId;

	public HDF5LabelMultisetSourceSpec(
			final String path,
			final String dataset,
			final int[] cellSize,
			final String name,
			final VolatileGlobalCellCache cellCache,
			final int setupId ) throws IOException
	{
		this( path, dataset, cellSize, action -> {}, () -> {
			try
			{
				Thread.sleep( 1000 );
			}
			catch ( final InterruptedException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}, TLongLongHashMap::new, name, cellCache, setupId );
	}

	public HDF5LabelMultisetSourceSpec(
			final String path,
			final String dataset,
			final int[] cellSize,
			final Consumer< Action > actionBroadcaster,
			final Supplier< TLongLongHashMap > solutionFetcher,
			final Supplier< TLongLongHashMap > initialSolution,
			final String name,
			final VolatileGlobalCellCache cellCache,
			final int setupId ) throws IOException
	{
		super();
		final IHDF5Reader h5reader = HDF5Factory.open( path );
		// TODO Use better value for number of threads of shared queue
		this.loader = new H5LabelMultisetSetupImageLoader( h5reader, null, dataset, setupId, cellSize, cellCache );
		this.assignment = new FragmentSegmentAssignmentWithHistory( initialSolution.get(), actionBroadcaster, solutionFetcher );
		this.stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( selectedIds, assignment );
		this.uri = "h5://" + path + "/dataset";
		this.name = name;
		this.setupId = setupId;
	}

	public HDF5LabelMultisetSourceSpec(
			final String path,
			final String dataset,
			final int[] cellSize,
			final double[] resolution,
			final double[] offset,
			final String name,
			final VolatileGlobalCellCache cellCache,
			final int setupId ) throws IOException
	{
		this( path, dataset, cellSize, action -> {}, () -> {
			try
			{
				Thread.sleep( 1000 );
			}
			catch ( final InterruptedException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}, resolution, offset, name, cellCache, setupId );
	}

	public HDF5LabelMultisetSourceSpec(
			final String path,
			final String dataset,
			final int[] cellSize,
			final Consumer< Action > actionBroadcaster,
			final Supplier< TLongLongHashMap > solutionFetcher,
			final double[] resolution,
			final double[] offset,
			final String name,
			final VolatileGlobalCellCache cellCache,
			final int setupId ) throws IOException
	{
		super();
		final IHDF5Reader h5reader = HDF5Factory.open( path );
		this.loader = new H5LabelMultisetSetupImageLoader( h5reader, null, dataset, setupId, cellSize, resolution, offset, cellCache );
		this.assignment = new FragmentSegmentAssignmentWithHistory( actionBroadcaster, solutionFetcher );
		this.stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( selectedIds, assignment );
		this.name = name;
		this.uri = "h5:/" + path + "/dataset";
		this.setupId = setupId;
	}

	public SelectedIds getSelectedIds()
	{
		return selectedIds;
	}

	@Override
	public LabelMultisetSource getSource()
	{
		final LabelMultisetSource source = new LabelMultisetSource( setupId, loader, stream );
		return source;
	}

	@Override
	public VolatileLabelMultisetSource getViewerSource()
	{
		final VolatileLabelMultisetSource source = new VolatileLabelMultisetSource( setupId, loader, stream );
		return source;
	}

	public static class HighlightingStreamConverter implements Converter< VolatileLabelMultisetType, ARGBType >
	{

		private final AbstractHighlightingARGBStream stream;

		public HighlightingStreamConverter( final AbstractHighlightingARGBStream stream )
		{
			super();
			this.stream = stream;
		}

		@Override
		public void convert( final VolatileLabelMultisetType input, final ARGBType output )
		{
			final Iterator< Entry< Label > > it = input.get().entrySet().iterator();
			output.set( stream.argb( it.hasNext() ? considerMaxUnsignedInt( it.next().getElement().id() ) : Label.INVALID ) );
		}

		public void setAlpha( final int alpha )
		{
			stream.setAlpha( alpha );
		}

		public void setHighlightAlpha( final int alpha )
		{
			stream.setActiveSegmentAlpha( alpha );
		}

		public void setInvalidSegmentAlpha( final int alpha )
		{
			stream.setInvalidSegmentAlpha( alpha );
		}

		public int getAlpha()
		{
			return stream.getAlpha();
		}

		public int getHighlightAlpha()
		{
			return stream.getActiveSegmentAlpha();
		}

		public int getInvalidSegmentAlpha()
		{
			return stream.getInvalidSegmentAlpha();
		}

		private static long considerMaxUnsignedInt( final long val )
		{
			return val >= Integer.MAX_VALUE ? Label.INVALID : val;
		}

	}

	@Override
	public FragmentSegmentAssignmentState< ? > getAssignment()
	{
		return assignment;
	}

	@Override
	public String name()
	{
		return name;
	}

	@Override
	public Optional< String > uri()
	{
		return Optional.of( uri );
	}

	@Override
	public ForegroundCheck< LabelMultisetType > foregroundCheck( final LabelMultisetType selection )
	{
		final long id = maxCountId( selection );
		final FragmentSegmentAssignmentState< ? > assignment = getAssignment();
		final long segmentId = assignment.getSegment( id );
		return t -> assignment.getSegment( maxCountId( t ) ) == segmentId ? 1 : 0;
	}

	public static long maxCountId( final LabelMultisetType t )
	{
		long argMaxLabel = Label.INVALID;
		long argMaxCount = 0;
		for ( final Entry< Label > entry : t.entrySet() )
		{
			final int count = entry.getCount();
			if ( count > argMaxCount )
			{
				argMaxCount = count;
				argMaxLabel = entry.getElement().id();
			}
		}
		return argMaxLabel;
	}

}
