package org.janelia.saalfeldlab.paintera.state;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongFunction;

import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.ToIdConverter;
import org.janelia.saalfeldlab.paintera.meshes.Interruptible;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunctionAndCache;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerWithAssignmentForSegments;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.janelia.saalfeldlab.paintera.meshes.cache.BlocksForLabelDelegate;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.paintera.meshes.cache.SegmentMaskGenerators;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Group;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.cache.UncheckedCache;
import net.imglib2.converter.Converter;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Pair;

public class LabelSourceState< D, T >
		extends
		MinimalSourceState< D, T, DataSource< D, T >, HighlightingStreamConverter< T > >
		implements
		HasMeshes< TLongHashSet >,
		HasMeshCache< TLongHashSet >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final LongFunction< Converter< D, BoolType > > maskForLabel;

	private final Function< TLongHashSet, Converter< D, BoolType > > segmentMaskGenerator;

	private final FragmentSegmentAssignmentState assignment;

	private final ToIdConverter toIdConverter;

	private final SelectedIds selectedIds;

	private final IdService idService;

	private final MeshManager< TLongHashSet > meshManager;

	private final MeshInfos< TLongHashSet > meshInfos;

	private final Runnable clearBlockCaches;

	private final InterruptibleFunctionAndCache< ShapeKey< TLongHashSet >, Pair< float[], float[] > >[] meshCaches;

	public LabelSourceState(
			final DataSource< D, T > dataSource,
			final HighlightingStreamConverter< T > converter,
			final Composite< ARGBType, ARGBType > composite,
			final String name,
			final FragmentSegmentAssignmentState assignment,
			final IdService idService,
			final SelectedIds selectedIds,
			final Group meshesGroup,
			final ExecutorService meshManagerExecutors,
			final ExecutorService meshWorkersExecutors )
	{
		super( dataSource, converter, composite, name );
		final D d = dataSource.getDataType();
		this.maskForLabel = PainteraBaseView.equalsMaskForType( d );
		this.segmentMaskGenerator = SegmentMaskGenerators.forType( d );
		this.assignment = assignment;
		this.toIdConverter = ToIdConverter.fromType( d );
		this.selectedIds = selectedIds;
		this.idService = idService;

		final SelectedSegments selectedSegments = new SelectedSegments( selectedIds, assignment );

		final InterruptibleFunctionAndCache< Long, Interval[] >[] backgroundBlockCaches = PainteraBaseView.generateLabelBlocksForLabelCache( dataSource );
		this.clearBlockCaches = () -> Arrays.stream( backgroundBlockCaches ).forEach( UncheckedCache::invalidateAll );

		final InterruptibleFunction< Long, Interval[] >[] blockCaches = dataSource instanceof MaskedSource< ?, ? >
				? combineInterruptibleFunctions( backgroundBlockCaches, InterruptibleFunction.fromFunction( blockCacheForMaskedSource( ( MaskedSource< ?, ? > ) dataSource ) ), new IntervalsCombiner() )
				: backgroundBlockCaches;

		final BlocksForLabelDelegate< TLongHashSet, Long >[] delegateBlockCaches = BlocksForLabelDelegate.delegate( blockCaches, ids -> Arrays.stream( ids.toArray() ).mapToObj( id -> id ).toArray( Long[]::new ), meshWorkersExecutors );

		final InterruptibleFunctionAndCache< ShapeKey< TLongHashSet >, Pair< float[], float[] > >[] meshCaches = CacheUtils.segmentMeshCacheLoaders(
				dataSource,
				segmentMaskGenerator,
				CacheUtils::toCacheSoftRefLoaderCache );
		this.meshCaches = meshCaches;

		final MeshManagerWithAssignmentForSegments meshManager = new MeshManagerWithAssignmentForSegments(
				dataSource,
				delegateBlockCaches,
				meshCaches,
				meshesGroup,
				assignment,
				selectedSegments,
				converter.getStream(),
				new SimpleIntegerProperty(),
				new SimpleDoubleProperty(),
				new SimpleIntegerProperty(),
				meshManagerExecutors,
				meshWorkersExecutors );
		final MeshInfos< TLongHashSet > meshInfos = new MeshInfos<>( selectedSegments, assignment, meshManager, assignment::getFragments, dataSource.getNumMipmapLevels() );

		this.meshManager = meshManager;
		this.meshInfos = meshInfos;

		assignment.addListener( obs -> stain() );
		selectedIds.addListener( obs -> stain() );
	}

	public ToIdConverter toIdConverter()
	{
		return this.toIdConverter;
	}

	public LongFunction< Converter< D, BoolType > > maskForLabel()
	{
		return this.maskForLabel;
	}

	@Override
	public MeshManager< TLongHashSet > meshManager()
	{
		return this.meshManager;
	}

	@Override
	public MeshInfos< TLongHashSet > meshInfos()
	{
		return this.meshInfos;
	}

	public FragmentSegmentAssignmentState assignment()
	{
		return this.assignment;
	}

	public IdService idService()
	{
		return this.idService;
	}

	public SelectedIds selectedIds()
	{
		return this.selectedIds;
	}

	@Override
	public void invalidateAll()
	{
		Arrays
				.stream( this.meshCaches )
				.forEach( UncheckedCache::invalidateAll );
		this.clearBlockCaches.run();
	}

	private static Function< Long, Interval[] >[] blockCacheForMaskedSource(
			final MaskedSource< ?, ? > source )
	{

		final int numLevels = source.getNumMipmapLevels();

		@SuppressWarnings( "unchecked" )
		final Function< Long, Interval[] >[] functions = new Function[ numLevels ];

		for ( int level = 0; level < numLevels; ++level )
		{
			final int fLevel = level;
			final CellGrid grid = source.getCellGrid( 0, level );
			final long[] imgDim = grid.getImgDimensions();
			final int[] blockSize = new int[ imgDim.length ];
			grid.cellDimensions( blockSize );
			functions[ level ] = id -> {
				LOG.warn( "Getting blocks at level={} for id={}", fLevel, id );
				final long[] blockMin = new long[ grid.numDimensions() ];
				final long[] blockMax = new long[ grid.numDimensions() ];
				final TLongSet indexedBlocks = source.getModifiedBlocks( fLevel, id );
				LOG.warn( "Received modified blocks at level={} for id={}: {}", fLevel, id, indexedBlocks );
				final Interval[] intervals = new Interval[ indexedBlocks.size() ];
				final TLongIterator blockIt = indexedBlocks.iterator();
				for ( int i = 0; blockIt.hasNext(); ++i )
				{
					final long blockIndex = blockIt.next();
					grid.getCellGridPositionFlat( blockIndex, blockMin );
					Arrays.setAll( blockMin, d -> blockMin[ d ] * blockSize[ d ] );
					Arrays.setAll( blockMax, d -> Math.min( blockMin[ d ] + blockSize[ d ], imgDim[ d ] ) - 1 );
					intervals[ i ] = new FinalInterval( blockMin, blockMax );
				}
				LOG.warn( "Returning {} intervals", intervals.length );
				return intervals;
			};
		}

		return functions;
	}

	private static < T, U, V, W > InterruptibleFunction< T, U >[] combineInterruptibleFunctions(
			final InterruptibleFunction< T, V >[] f1,
			final InterruptibleFunction< T, W >[] f2,
			final BiFunction< V, W, U > combiner )
	{
		assert f1.length == f2.length;

		LOG.warn( "Combining two functions {} and {}", f1, f2 );

		@SuppressWarnings( "unchecked" )
		final InterruptibleFunction< T, U >[] f = new InterruptibleFunction[ f1.length ];
		for ( int i = 0; i < f.length; ++i )
		{
			final InterruptibleFunction< T, V > ff1 = f1[ i ];
			final InterruptibleFunction< T, W > ff2 = f2[ i ];
			final List< Interruptible< T > > interrupts = Arrays.asList( ff1, ff2 );
			f[ i ] = InterruptibleFunction.fromFunctionAndInterruptible(
					t -> combiner.apply( ff1.apply( t ), ff2.apply( t ) ),
					t -> interrupts.forEach( interrupt -> interrupt.interruptFor( t ) ) );
		}
		return f;
	}

	private static class IntervalsCombiner implements BiFunction< Interval[], Interval[], Interval[] >
	{

		private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

		@Override
		public Interval[] apply( final Interval[] t, final Interval[] u )
		{
			final Set< HashWrapper< Interval > > intervals = new HashSet<>();
			Arrays.stream( t ).map( HashWrapper::interval ).forEach( intervals::add );
			Arrays.stream( u ).map( HashWrapper::interval ).forEach( intervals::add );
			LOG.warn( "Combined {} and {} to {}", t, u, intervals );
			return intervals.stream().map( HashWrapper::getData ).toArray( Interval[]::new );
		}

	}
}
