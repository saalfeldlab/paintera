package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Group;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.cache.Cache;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.UncheckedCache;
import net.imglib2.converter.Converter;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.util.Pair;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.cache.Invalidate;
import org.janelia.saalfeldlab.paintera.cache.InvalidateAll;
import org.janelia.saalfeldlab.paintera.cache.global.GlobalCache;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentsInSelectedSegments;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.meshes.cache.BlocksForLabelDelegate;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.paintera.meshes.cache.SegmentMaskGenerators;
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Philipp Hanslovsky
 */
public class MeshManagerWithAssignmentForSegments implements MeshManager<Long, TLongHashSet>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final DataSource<?, ?> source;

	private final InterruptibleFunction<TLongHashSet, Interval[]>[] blockListCache;

	private final InterruptibleFunction<ShapeKey<TLongHashSet>, Pair<float[], float[]>>[] meshCache;

	private final Invalidate<ShapeKey<TLongHashSet>>[] invalidateMeshCaches;

	private final FragmentSegmentAssignmentState assignment;

	private final AbstractHighlightingARGBStream stream;

	private final Map<Long, MeshGenerator<TLongHashSet>> neurons = Collections.synchronizedMap(new HashMap<>());

	private final Group root;

	private final SelectedSegments selectedSegments;

	private final FragmentsInSelectedSegments fragmentsInSelectedSegments;

	private final ManagedMeshSettings meshSettings;

	private final ExecutorService managers;

	private final ExecutorService workers;

	private final List<Runnable> refreshMeshes = new ArrayList<>();

	private final BooleanProperty areMeshesEnabled = new SimpleBooleanProperty(true);

	public MeshManagerWithAssignmentForSegments(
			final DataSource<?, ?> source,
			final InterruptibleFunction<TLongHashSet, Interval[]>[] blockListCacheForFragments,
			final InterruptibleFunction<ShapeKey<TLongHashSet>, Pair<float[], float[]>>[] meshCache,
			final Invalidate<ShapeKey<TLongHashSet>>[] invalidateMeshCaches,
			final Group root,
			final ManagedMeshSettings meshSettings,
			final FragmentSegmentAssignmentState assignment,
			final SelectedSegments selectedSegments,
			final AbstractHighlightingARGBStream stream,
			final ExecutorService managers,
			final ExecutorService workers)
	{
		super();
		this.source = source;
		this.blockListCache = blockListCacheForFragments;
		this.meshCache = meshCache;
		this.invalidateMeshCaches = invalidateMeshCaches;
		this.root = root;
		this.assignment = assignment;
		this.selectedSegments = selectedSegments;
		this.fragmentsInSelectedSegments = new FragmentsInSelectedSegments(selectedSegments, assignment);
		this.stream = stream;

		this.meshSettings = meshSettings;

		this.managers = managers;
		this.workers = workers;

		this.assignment.addListener(obs -> this.update());
		this.selectedSegments.addListener(obs -> this.update());
		this.areMeshesEnabled.addListener((obs, oldv, newv) -> {
			if (newv)
			{
				update();
			}
			else
			{
				removeAllMeshes();
			}
		});

	}

	public void addRefreshMeshesListener(Runnable listener)
	{
		this.refreshMeshes.add(listener);
	}

	private void update()
	{
		LOG.debug("Updating");
		synchronized (neurons)
		{
			final long[]                                         selectedSegments    = this.selectedSegments
					.getSelectedSegments();
			final TLongHashSet                                   selectedSegmentsSet = new TLongHashSet
					(selectedSegments);
			final Set<Long>                                      currentlyShowing    = new HashSet<>();
			final List<Entry<Long, MeshGenerator<TLongHashSet>>> toBeRemoved         = new ArrayList<>();
			neurons.keySet().forEach(currentlyShowing::add);
			for (final Entry<Long, MeshGenerator<TLongHashSet>> neuron : neurons.entrySet())
			{
				final long         segment            = neuron.getKey();
				final TLongHashSet fragmentsInSegment = assignment.getFragments(segment);
				final boolean      isSelected         = selectedSegmentsSet.contains(segment);
				final boolean      isConsistent       = neuron.getValue().getId().equals(fragmentsInSegment);
				LOG.debug("Fragments in segment {}: {}", segment, fragmentsInSegment);
				LOG.debug("Segment {} is selected? {}  Is consistent? {}", neuron.getKey(), isSelected, isConsistent);
				if (!isSelected || !isConsistent)
				{
					currentlyShowing.remove(neuron.getKey());
					toBeRemoved.add(neuron);
				}

			}
			toBeRemoved.stream().map(e -> e.getValue()).forEach(this::removeMesh);
			LOG.debug("Currently showing {} ", currentlyShowing);
			LOG.debug("Selection {}", selectedSegments);
			LOG.debug("To be removed {}", toBeRemoved);
			Arrays
					.stream(selectedSegments)
					.filter(id -> !currentlyShowing.contains(id))
					.forEach(this::generateMesh);
		}
	}

	@Override
	public void generateMesh(final Long idObject)
	{
		if (!areMeshesEnabled.get())
		{
			LOG.debug("Meshes not enabled -- will return without creating mesh");
			return;
		}
		final long id = idObject;

		if (!this.selectedSegments.isSegmentSelected(id))
		{
			LOG.debug("Id {} not selected -- will return without creating mesh", id);
			return;
		}

		final TLongHashSet fragments = this.assignment.getFragments(id);

		final IntegerProperty color = new SimpleIntegerProperty(stream.argb(id));
		stream.addListener(obs -> color.set(stream.argb(id)));
		assignment.addListener(obs -> color.set(stream.argb(id)));

		final Boolean isPresentAndValid = Optional.ofNullable(neurons.get(idObject)).map(MeshGenerator::getId).map(
				fragments::equals).orElse(false);
		if (isPresentAndValid)
		{
			LOG.debug("Id {} already present with valid selection {}", id, fragments);
		}

		LOG.debug("Adding mesh for segment {}.", id);
		final MeshSettings meshSettings = this.meshSettings.getOrAddMesh(idObject);
		final MeshGenerator<TLongHashSet> nfx = new MeshGenerator<>(
				this.root,
				fragments,
				blockListCache,
				meshCache,
				color,
				meshSettings.scaleLevelProperty().get(),
				meshSettings.simplificationIterationsProperty().get(),
				meshSettings.smoothingLambdaProperty().get(),
				meshSettings.smoothingIterationsProperty().get(),
				managers,
				workers
		);
		final BooleanProperty isManaged = this.meshSettings.isManagedProperty(id);
		isManaged.addListener((obs, oldv, newv) -> nfx.bindTo(newv
		                                                      ? this.meshSettings.getGlobalSettings()
		                                                      : meshSettings));
		nfx.bindTo(isManaged.get() ? this.meshSettings.getGlobalSettings() : meshSettings);

		neurons.put(idObject, nfx);

	}

	@Override
	public void removeMesh(final Long id)
	{
		Optional.ofNullable(unmodifiableMeshMap().get(id)).ifPresent(this::removeMesh);
	}

	private void removeMesh(final MeshGenerator<TLongHashSet> mesh)
	{
		mesh.isEnabledProperty().set(false);
		mesh.unbind();
		final List<Long> toRemove = this.neurons
				.entrySet()
				.stream()
				.filter(e -> e.getValue().getId().equals(mesh.getId()))
				.map(Entry::getKey)
				.collect(Collectors.toList());
		toRemove
				.stream()
				.map(this.neurons::remove)
				.filter(n -> n != null)
				.forEach(MeshGenerator::interrupt);
	}

	@Override
	public Map<Long, MeshGenerator<TLongHashSet>> unmodifiableMeshMap()
	{
		return Collections.unmodifiableMap(neurons);
	}

	@Override
	public IntegerProperty scaleLevelProperty()
	{
		return this.meshSettings.getGlobalSettings().scaleLevelProperty();
	}

	@Override
	public IntegerProperty meshSimplificationIterationsProperty()
	{
		return this.meshSettings.getGlobalSettings().simplificationIterationsProperty();
	}

	@Override
	public void removeAllMeshes()
	{
		final ArrayList<MeshGenerator<TLongHashSet>> generatorsCopy = new ArrayList<>(unmodifiableMeshMap().values());
		generatorsCopy.forEach(this::removeMesh);
	}

	@Override
	public DoubleProperty smoothingLambdaProperty()
	{

		return this.meshSettings.getGlobalSettings().smoothingLambdaProperty();
	}

	@Override
	public IntegerProperty smoothingIterationsProperty()
	{

		return this.meshSettings.getGlobalSettings().smoothingIterationsProperty();
	}

	@Override
	public InterruptibleFunction<TLongHashSet, Interval[]>[] blockListCache()
	{
		return this.blockListCache;
	}

	@Override
	public InterruptibleFunction<ShapeKey<TLongHashSet>, Pair<float[], float[]>>[] meshCache()
	{
		return this.meshCache;
	}

	@Override
	public DoubleProperty opacityProperty()
	{
		return this.meshSettings.getGlobalSettings().opacityProperty();
	}

	@Override
	public long[] containedFragments(final Long t)
	{
		return this.assignment.getFragments(t).toArray();
	}

	@Override
	public void refreshMeshes()
	{
		LOG.debug("Refreshing meshes. {} Listeners", refreshMeshes.size());
		this.refreshMeshes.forEach(Runnable::run);
	}

	@Override
	public BooleanProperty areMeshesEnabledProperty()
	{
		return this.areMeshesEnabled;
	}

	@Override
	public ManagedMeshSettings managedMeshSettings()
	{
		return this.meshSettings;
	}

	@Override
	public void invalidateMeshCaches()
	{
		Stream.of(this.invalidateMeshCaches).forEach(InvalidateAll::invalidateAll);
	}

	public static <D extends IntegerType<D>> MeshManagerWithAssignmentForSegments fromBlockLookup(
			DataSource<D, ?> dataSource,
			final SelectedIds selectedIds,
			final FragmentSegmentAssignmentState assignment,
			final AbstractHighlightingARGBStream stream,
			final Group meshesGroup,
			final InterruptibleFunction<Long, Interval[]>[] backgroundBlockCaches,
			final Function<CacheLoader<ShapeKey<TLongHashSet>, Pair<float[], float[]>>, Pair<Cache<ShapeKey<TLongHashSet>,
					Pair<float[], float[]>>, Invalidate<ShapeKey<TLongHashSet>>>> makeCache,
			final ExecutorService meshManagerExecutors,
			final ExecutorService meshWorkersExecutors
			)
	{
		LOG.debug("Data source is type {}", dataSource.getClass());
		final SelectedSegments selectedSegments = new SelectedSegments(selectedIds, assignment);

		final boolean isMaskedSource = dataSource instanceof MaskedSource<?, ?>;
		final InterruptibleFunction<Long, Interval[]>[] blockCaches = isMaskedSource
		                                                              ? combineInterruptibleFunctions(
				backgroundBlockCaches,
				InterruptibleFunction.fromFunction(blockCacheForMaskedSource((MaskedSource<?, ?>) dataSource)),
				new IntervalsCombiner())
		                                                              : backgroundBlockCaches;

		final BlocksForLabelDelegate<TLongHashSet, Long>[] delegateBlockCaches = BlocksForLabelDelegate.delegate(
				blockCaches,
				ids -> Arrays.stream(ids.toArray()).boxed().toArray(Long[]::new));


		final D d = dataSource.getDataType();
		final Function<TLongHashSet, Converter<D, BoolType>> segmentMaskGenerator = SegmentMaskGenerators.forType(d);

		final Pair<InterruptibleFunctionAndCache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>, Invalidate<ShapeKey<TLongHashSet>>>[] meshCaches = CacheUtils
				.segmentMeshCacheLoaders(
						dataSource,
						segmentMaskGenerator,
						makeCache);

		final MeshManagerWithAssignmentForSegments manager = new MeshManagerWithAssignmentForSegments(
				dataSource,
				delegateBlockCaches,
				Stream.of(meshCaches).map(Pair::getA).toArray(InterruptibleFunctionAndCache[]::new),
				Stream.of(meshCaches).map(Pair::getB).toArray(Invalidate[]::new),
				meshesGroup,
				new ManagedMeshSettings(dataSource.getNumMipmapLevels()),
				assignment,
				selectedSegments,
				stream,
				meshManagerExecutors,
				meshWorkersExecutors);
		manager.addRefreshMeshesListener(() -> {
			LOG.debug("Refreshing meshes!");
			Stream
					.of(meshCaches)
					.map(Pair::getB)
					.forEach(InvalidateAll::invalidateAll);
			final long[] selection     = selectedIds.getActiveIds();
			final long   lastSelection = selectedIds.getLastSelection();
			selectedIds.deactivateAll();
			selectedIds.activate(selection);
			selectedIds.activateAlso(lastSelection);
		});
		return manager;
	}

	private static <T, U, V, W> InterruptibleFunction<T, U>[] combineInterruptibleFunctions(
			final InterruptibleFunction<T, V>[] f1,
			final InterruptibleFunction<T, W>[] f2,
			final BiFunction<V, W, U> combiner)
	{
		assert f1.length == f2.length;

		LOG.debug("Combining two functions {} and {}", f1, f2);

		@SuppressWarnings("unchecked") final InterruptibleFunction<T, U>[] f = new InterruptibleFunction[f1.length];
		for (int i = 0; i < f.length; ++i)
		{
			final InterruptibleFunction<T, V> ff1        = f1[i];
			final InterruptibleFunction<T, W> ff2        = f2[i];
			final List<Interruptible<T>>      interrupts = Arrays.asList(ff1, ff2);
			f[i] = InterruptibleFunction.fromFunctionAndInterruptible(
					t -> combiner.apply(ff1.apply(t), ff2.apply(t)),
					t -> interrupts.forEach(interrupt -> interrupt.interruptFor(t))
			);
		}
		return f;
	}

	private static Function<Long, Interval[]>[] blockCacheForMaskedSource(
			final MaskedSource<?, ?> source)
	{

		final int numLevels = source.getNumMipmapLevels();

		@SuppressWarnings("unchecked") final Function<Long, Interval[]>[] functions = new Function[numLevels];

		for (int level = 0; level < numLevels; ++level)
		{
			final int      fLevel    = level;
			final CellGrid grid      = source.getCellGrid(0, level);
			final long[]   imgDim    = grid.getImgDimensions();
			final int[]    blockSize = new int[imgDim.length];
			grid.cellDimensions(blockSize);
			functions[level] = id -> {
				LOG.debug("Getting blocks at level={} for id={}", fLevel, id);
				final long[]   blockMin      = new long[grid.numDimensions()];
				final long[]   blockMax      = new long[grid.numDimensions()];
				final TLongSet indexedBlocks = source.getModifiedBlocks(fLevel, id);
				LOG.debug("Received modified blocks at level={} for id={}: {}", fLevel, id, indexedBlocks);
				final Interval[]    intervals = new Interval[indexedBlocks.size()];
				final TLongIterator blockIt   = indexedBlocks.iterator();
				for (int i = 0; blockIt.hasNext(); ++i)
				{
					final long blockIndex = blockIt.next();
					grid.getCellGridPositionFlat(blockIndex, blockMin);
					Arrays.setAll(blockMin, d -> blockMin[d] * blockSize[d]);
					Arrays.setAll(blockMax, d -> Math.min(blockMin[d] + blockSize[d], imgDim[d]) - 1);
					intervals[i] = new FinalInterval(blockMin, blockMax);
				}
				LOG.debug("Returning {} intervals", intervals.length);
				return intervals;
			};
		}

		return functions;
	}

	private static class IntervalsCombiner implements BiFunction<Interval[], Interval[], Interval[]>
	{

		private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

		@Override
		public Interval[] apply(final Interval[] t, final Interval[] u)
		{
			final Set<HashWrapper<Interval>> intervals = new HashSet<>();
			Arrays.stream(t).map(HashWrapper::interval).forEach(intervals::add);
			Arrays.stream(u).map(HashWrapper::interval).forEach(intervals::add);
			LOG.debug("Combined {} and {} to {}", t, u, intervals);
			return intervals.stream().map(HashWrapper::getData).toArray(Interval[]::new);
		}

	}

}
