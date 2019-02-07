package org.janelia.saalfeldlab.paintera.state;

import bdv.util.volatiles.VolatileTypeMatcher;
import gnu.trove.set.hash.TLongHashSet;
import javafx.scene.Group;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.paintera.cache.InvalidateAll;
import org.janelia.saalfeldlab.paintera.cache.global.GlobalCache;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsState;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.LocalIdService;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerWithAssignmentForSegments;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverterIntegerType;
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.touk.throwing.ThrowingFunction;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

public class LabelSourceState<D extends IntegerType<D>, T>
		extends
		MinimalSourceState<D, T, DataSource<D, T>, HighlightingStreamConverter<T>>
		implements
		HasMeshes<TLongHashSet>,
		HasMeshCache<TLongHashSet>,
		HasIdService,
		HasSelectedIds,
		HasHighlightingStreamConverter<T>,
		HasMaskForLabel<D>,
		HasFragmentSegmentAssignments,
		HasLockedSegments
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final LongFunction<Converter<D, BoolType>> maskForLabel;


	private final FragmentSegmentAssignmentState assignment;

	private final SelectedIds selectedIds;

	private final IdService idService;

	private final MeshManager<Long, TLongHashSet> meshManager;

	private final LockedSegmentsState lockedSegments;

	private final LabelBlockLookup labelBlockLookup;

	public LabelSourceState(
			final DataSource<D, T> dataSource,
			final HighlightingStreamConverter<T> converter,
			final Composite<ARGBType, ARGBType> composite,
			final String name,
			final FragmentSegmentAssignmentState assignment,
			final LockedSegmentsState lockedSegments,
			final IdService idService,
			final SelectedIds selectedIds,
			final MeshManager<Long, TLongHashSet> meshManager,
			final LabelBlockLookup labelBlockLookup)
	{
		super(dataSource, converter, composite, name);
		final D d = dataSource.getDataType();
		this.maskForLabel = equalsMaskForType(d);
		this.assignment = assignment;
		this.lockedSegments = lockedSegments;
		this.selectedIds = selectedIds;
		this.idService = idService;
		this.meshManager = meshManager;
		this.labelBlockLookup = labelBlockLookup;
		assignment.addListener(obs -> stain());
		selectedIds.addListener(obs -> stain());
		lockedSegments.addListener(obs -> stain());
	}

	public LabelBlockLookup labelBlockLookup() {
		return this.labelBlockLookup;
	}

	public LongFunction<Converter<D, BoolType>> maskForLabel()
	{
		return this.maskForLabel;
	}

	@Override
	public MeshManager<Long, TLongHashSet> meshManager()
	{
		return this.meshManager;
	}

	@Override
	public ManagedMeshSettings managedMeshSettings()
	{
		return this.meshManager.managedMeshSettings();
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
		invalidateAllMeshCaches();
		invalidateAllBlockCaches();
	}

	public void invalidateAllMeshCaches()
	{
		this.meshManager.invalidateMeshCaches();
	}

	public LockedSegmentsState lockedSegments()
	{
		return this.lockedSegments;
	}


	public void invalidateAllBlockCaches()
	{
//		this.clearBlockCaches.run();
	}

	@Override
	public void refreshMeshes()
	{
		this.invalidateAll();
		final long[] selection     = this.selectedIds.getActiveIds();
		final long   lastSelection = this.selectedIds.getLastSelection();
		this.selectedIds.deactivateAll();
		this.selectedIds.activate(selection);
		this.selectedIds.activateAlso(lastSelection);
	}

	@Override
	public HighlightingStreamConverter<T> highlightingStreamConverter() {
		return converter();
	}

	public static <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & IntegerType<T>>
	LabelSourceState<D, T> simpleSourceFromSingleRAI(
			final RandomAccessibleInterval<D> data,
			final double[] resolution,
			final double[] offset,
			final AxisOrder axisOrder,
			final long maxId,
			final String name,
			final GlobalCache globalCache,
			final Group meshesGroup,
			final ExecutorService meshManagerExecutors,
			final ExecutorService meshWorkersExecutors) {
		return simpleSourceFromSingleRAI(data, resolution, offset, () -> {}, axisOrder, maxId, name, globalCache, meshesGroup, meshManagerExecutors, meshWorkersExecutors);
	}

	public static <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & IntegerType<T>>
	LabelSourceState<D, T> simpleSourceFromSingleRAI(
			final RandomAccessibleInterval<D> data,
			final double[] resolution,
			final double[] offset,
			final InvalidateAll invalidateAll,
			final AxisOrder axisOrder,
			final long maxId,
			final String name,
			final GlobalCache globalCache,
			final Group meshesGroup,
			final ExecutorService meshManagerExecutors,
			final ExecutorService meshWorkersExecutors) {

		final int[] blockSize;
		if (data instanceof AbstractCellImg<?, ?, ?, ?>)
		{
			final CellGrid grid = ((AbstractCellImg<?, ?, ?, ?>) data).getCellGrid();
			blockSize = new int[grid.numDimensions()];
			Arrays.setAll(blockSize, grid::cellDimension);
		}
		else
		{
			blockSize = new int[] {64, 64, 64};
		}

		final Interval[] intervals = Grids.collectAllContainedIntervals(
				Intervals.dimensionsAsLongArray(data),
				blockSize
		                                                               )
				.stream()
				.toArray(Interval[]::new);

		@SuppressWarnings("unchecked") final InterruptibleFunction<Long, Interval[]>[] backgroundBlockCaches = new
				InterruptibleFunction[] {
				InterruptibleFunction.fromFunction(id -> intervals)
		};
		final LabelBlockLookup labelBlockLookup = new LabelBlockLookupNoBlocks();
		return simpleSourceFromSingleRAI(
				data,
				resolution,
				offset,
				invalidateAll,
				axisOrder,
				maxId,
				name,
				labelBlockLookup,
				globalCache,
				meshesGroup,
				meshManagerExecutors,
				meshWorkersExecutors);
	}

	public static <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & IntegerType<T>>
	LabelSourceState<D, T> simpleSourceFromSingleRAI(
			final RandomAccessibleInterval<D> data,
			final double[] resolution,
			final double[] offset,
			final AxisOrder axisOrder,
			final long maxId,
			final String name,
			final LabelBlockLookup labelBlockLookup,
			final GlobalCache globalCache,
			final Group meshesGroup,
			final ExecutorService meshManagerExecutors,
			final ExecutorService meshWorkersExecutors) {
		return simpleSourceFromSingleRAI(data, resolution, offset, () -> {}, axisOrder, maxId, name, labelBlockLookup, globalCache, meshesGroup, meshManagerExecutors, meshWorkersExecutors);
	}

	public static <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & IntegerType<T>>
	LabelSourceState<D, T> simpleSourceFromSingleRAI(
			final RandomAccessibleInterval<D> data,
			final double[] resolution,
			final double[] offset,
			final InvalidateAll invalidateAll,
			final AxisOrder axisOrder,
			final long maxId,
			final String name,
			final LabelBlockLookup labelBlockLookup,
			final GlobalCache globalCache,
			final Group meshesGroup,
			final ExecutorService meshManagerExecutors,
			final ExecutorService meshWorkersExecutors) {

		if (!Views.isZeroMin(data))
		{
			return simpleSourceFromSingleRAI(
					Views.zeroMin(data),
					resolution,
					offset,
					invalidateAll,
					axisOrder,
					maxId,
					name,
					labelBlockLookup,
					globalCache,
					meshesGroup,
					meshManagerExecutors,
					meshWorkersExecutors
			                                );
		}

		final AffineTransform3D mipmapTransform = new AffineTransform3D();
		mipmapTransform.set(
				resolution[0], 0, 0, offset[0],
				0, resolution[1], 0, offset[1],
				0, 0, resolution[2], offset[2]
		                   );

		final T vt = (T) VolatileTypeMatcher.getVolatileTypeForType(Util.getTypeFromInterval(data)).createVariable();
		vt.setValid(true);
		final RandomAccessibleInterval<T> vdata = Converters.convert(data, (s, t) -> t.get().set(s), vt);

		final RandomAccessibleIntervalDataSource<D, T> dataSource = new RandomAccessibleIntervalDataSource<>(
				data,
				vdata,
				mipmapTransform,
				invalidateAll,
				i -> new NearestNeighborInterpolatorFactory<>(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				name
		);

		final SelectedIds                        selectedIds    = new SelectedIds();
		final FragmentSegmentAssignmentOnlyLocal assignment     = new FragmentSegmentAssignmentOnlyLocal(new FragmentSegmentAssignmentOnlyLocal.DoesNotPersist());
		final LockedSegmentsOnlyLocal            lockedSegments = new LockedSegmentsOnlyLocal(seg -> {});
		final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new
				ModalGoldenAngleSaturatedHighlightingARGBStream(
				selectedIds,
				assignment,
				lockedSegments
		);


		final ToLongFunction<T> toLong = integer -> {
			final long val = integer.get().getIntegerLong();
			return val;
		};

		final Function<Long, Interval[]> f = ThrowingFunction.unchecked(id -> labelBlockLookup.read(0, id));
		final InterruptibleFunction<Long, Interval[]>[] backgroundBlockCaches = InterruptibleFunction.fromFunction(new Function[]{f});

		final MeshManagerWithAssignmentForSegments meshManager = MeshManagerWithAssignmentForSegments.fromBlockLookup(
				dataSource,
				selectedIds,
				assignment,
				stream,
				meshesGroup,
				backgroundBlockCaches,
				globalCache::createNewCache,
				meshManagerExecutors,
				meshWorkersExecutors);

		return new LabelSourceState<>(
				dataSource,
				new HighlightingStreamConverterIntegerType<>(stream),
				new ARGBCompositeAlphaYCbCr(),
				name,
				assignment,
				lockedSegments,
				new LocalIdService(maxId),
				selectedIds,
				meshManager,
				labelBlockLookup
		);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <D> LongFunction<Converter<D, BoolType>> equalsMaskForType(final D d)
	{
		if (d instanceof LabelMultisetType) { return (LongFunction) equalMaskForLabelMultisetType(); }

		if (d instanceof IntegerType<?>) { return (LongFunction) equalMaskForIntegerType(); }

		if (d instanceof RealType<?>) { return (LongFunction) equalMaskForRealType(); }

		return null;
	}

	private static LongFunction<Converter<LabelMultisetType, BoolType>> equalMaskForLabelMultisetType()
	{
		return id -> (s, t) -> t.set(s.contains(id));
	}

	private static <D extends IntegerType<D>> LongFunction<Converter<D, BoolType>> equalMaskForIntegerType()
	{
		return id -> (s, t) -> t.set(s.getIntegerLong() == id);
	}

	private static <D extends RealType<D>> LongFunction<Converter<D, BoolType>> equalMaskForRealType()
	{
		return id -> (s, t) -> t.set(s.getRealDouble() == id);
	}
}
