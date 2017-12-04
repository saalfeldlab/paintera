package bdv.bigcat.viewer.atlas.data;

import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import bdv.bigcat.viewer.atlas.solver.action.Action;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentWithHistory;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.bigcat.viewer.stream.AbstractHighlightingARGBStream;
import bdv.bigcat.viewer.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset.Entry;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.viewer.Interpolation;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import gnu.trove.map.hash.TLongLongHashMap;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.view.Views;

public class HDF5LabelMultisetDataSource implements LabelDataSource< LabelMultisetType, VolatileLabelMultisetType >
{

	private final AbstractHighlightingARGBStream stream;

	private final H5LabelMultisetSetupImageLoader loader;

	private final SelectedIds selectedIds = new SelectedIds();

	private final FragmentSegmentAssignmentState< ? > assignment;

	private final String name;

	private final String uri;

	private final int setupId;

	public HDF5LabelMultisetDataSource(
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

	public HDF5LabelMultisetDataSource(
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
		final IHDF5Reader h5reader = HDF5Factory.openForReading( path );
		// TODO Use better value for number of threads of shared queue
		this.loader = new H5LabelMultisetSetupImageLoader( h5reader, null, dataset, setupId, cellSize, cellCache );
		this.assignment = new FragmentSegmentAssignmentWithHistory( initialSolution.get(), actionBroadcaster, solutionFetcher );
		this.stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( selectedIds, assignment );
		this.uri = "h5://" + path + "/dataset";
		this.name = name;
		this.setupId = setupId;
	}

	public HDF5LabelMultisetDataSource(
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

	public HDF5LabelMultisetDataSource(
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
			// TODO this needs to use all LabelMultisetType, not just first
			// entry
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

	public Optional< String > uri()
	{
		return Optional.of( uri );
	}

	@Override
	public RandomAccessibleInterval< LabelMultisetType > getDataSource( final int t, final int level )
	{
		return loader.getImage( t, level );
	}

	@Override
	public RealRandomAccessible< LabelMultisetType > getInterpolatedDataSource( final int t, final int level, final Interpolation method )
	{
		return Views.interpolate( Views.extendValue( getDataSource( t, level ), new LabelMultisetType() ), new NearestNeighborInterpolatorFactory<>() );
	}

	@Override
	public LabelMultisetType getDataType()
	{
		return new LabelMultisetType();
	}

	@Override
	public boolean isPresent( final int t )
	{
		return true;
	}

	@Override
	public RandomAccessibleInterval< VolatileLabelMultisetType > getSource( final int t, final int level )
	{
		return loader.getVolatileImage( t, level );
	}

	@Override
	public RealRandomAccessible< VolatileLabelMultisetType > getInterpolatedSource( final int t, final int level, final Interpolation method )
	{
		return Views.interpolate( Views.extendValue( getSource( t, level ), new VolatileLabelMultisetType() ), new NearestNeighborInterpolatorFactory<>() );
	}

	@Override
	public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
	{
		transform.set( loader.getMipmapTransforms()[ level ] );
	}

	@Override
	public VolatileLabelMultisetType getType()
	{
		return new VolatileLabelMultisetType();
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		return null;
	}

	@Override
	public int getNumMipmapLevels()
	{
		return loader.getMipmapTransforms().length;
	}

}
