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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.pivovarit.function.ThrowingBiFunction;
import com.pivovarit.function.ThrowingFunction;
import net.imglib2.cache.util.Caches;
import net.imglib2.util.ValuePair;
import org.janelia.saalfeldlab.fx.ObservableWithListenersList;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.paintera.cache.Invalidate;
import org.janelia.saalfeldlab.paintera.cache.InvalidateAll;
import org.janelia.saalfeldlab.paintera.cache.global.GlobalCache;
import org.janelia.saalfeldlab.paintera.config.Viewer3DConfig;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.meshes.cache.BlocksForLabelDelegate;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.paintera.meshes.cache.SegmentMaskGenerators;
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.janelia.saalfeldlab.util.concurrent.PriorityExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.scene.Group;
import javafx.scene.Node;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.cache.Cache;
import net.imglib2.cache.CacheLoader;
import net.imglib2.converter.Converter;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.util.Pair;

/**
 * @author Philipp Hanslovsky
 */
public class MeshManagerWithAssignmentForSegments extends ObservableWithListenersList implements MeshManager<Long, TLongHashSet>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final DataSource<?, ?> source;

	/**
	 * For each scale level, returns a set of blocks containing the given label ID.
	 */
	private final InterruptibleFunction<TLongHashSet, Interval[]>[] blockListCache;

	private final Invalidate<TLongHashSet>[] blockListCacheInvalidate;

	/**
	 * For each scale level, returns a mesh for a specified shape key (includes label ID, interval, scale index, and more).
	 */
	private final InterruptibleFunction<ShapeKey<TLongHashSet>, Pair<float[], float[]>>[] meshCache;

	private final Invalidate<ShapeKey<TLongHashSet>>[] meshCacheInvalidate;

	private final AbstractHighlightingARGBStream stream;

	private final Map<Long, MeshGenerator<TLongHashSet>> neurons = Collections.synchronizedMap(new HashMap<>());

	private final Group root = new Group();

	private final ObjectProperty<ViewFrustum> viewFrustumProperty;

	private final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty;

	private final AffineTransform3D[] unshiftedWorldTransforms;

	private final SelectedSegments selectedSegments;

	private final ManagedMeshSettings meshSettings;

	private final ExecutorService managers;

	private final PriorityExecutorService<MeshWorkerPriority> workers;

	private final List<Runnable> refreshMeshes = new ArrayList<>();

	private final BooleanProperty areMeshesEnabled = new SimpleBooleanProperty(true);

	private final BooleanProperty showBlockBoundaries = new SimpleBooleanProperty(false);

	private final IntegerProperty rendererBlockSize = new SimpleIntegerProperty(Viewer3DConfig.RENDERER_BLOCK_SIZE_DEFAULT_VALUE);

	private final IntegerProperty numElementsPerFrame = new SimpleIntegerProperty(Viewer3DConfig.NUM_ELEMENTS_PER_FRAME_DEFAULT_VALUE);

	private final LongProperty frameDelayMsec = new SimpleLongProperty(Viewer3DConfig.FRAME_DELAY_MSEC_DEFAULT_VALUE);

	private final LongProperty sceneUpdateDelayMsec = new SimpleLongProperty(Viewer3DConfig.SCENE_UPDATE_DELAY_MSEC_DEFAULT_VALUE);

	private final AtomicBoolean bulkUpdate = new AtomicBoolean();

	private final MeshViewUpdateQueue<TLongHashSet> meshViewUpdateQueue = new MeshViewUpdateQueue<>();

	private final SceneUpdateHandler sceneUpdateHandler;

	public MeshManagerWithAssignmentForSegments(
			final DataSource<?, ?> source,
			final Pair<? extends InterruptibleFunction<TLongHashSet, Interval[]>, Invalidate<TLongHashSet>>[] blockListCacheAndInvalidate,
			final Pair<? extends InterruptibleFunction<ShapeKey<TLongHashSet>, Pair<float[], float[]>>, Invalidate<ShapeKey<TLongHashSet>>>[] meshCacheAndInvalidate,
			final Group root,
			final ObjectProperty<ViewFrustum> viewFrustumProperty,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
			final ManagedMeshSettings meshSettings,
			final SelectedSegments selectedSegments,
			final AbstractHighlightingARGBStream stream,
			final ExecutorService managers,
			final PriorityExecutorService<MeshWorkerPriority> workers)
	{
		super();
		this.source = source;
		this.blockListCache = Arrays.stream(blockListCacheAndInvalidate).map(Pair::getA).toArray(InterruptibleFunction[]::new);
		this.blockListCacheInvalidate = Arrays.stream(blockListCacheAndInvalidate).map(Pair::getB).toArray(Invalidate[]::new);
		this.meshCache = Arrays.stream(meshCacheAndInvalidate).map(Pair::getA).toArray(InterruptibleFunction[]::new);
		this.meshCacheInvalidate = Arrays.stream(meshCacheAndInvalidate).map(Pair::getB).toArray(Invalidate[]::new);
		this.viewFrustumProperty = viewFrustumProperty;
		this.eyeToWorldTransformProperty = eyeToWorldTransformProperty;
		this.meshSettings = meshSettings;
		this.selectedSegments = selectedSegments;
		this.stream = stream;
		this.managers = managers;
		this.workers = workers;

		this.unshiftedWorldTransforms = DataSource.getUnshiftedWorldTransforms(source, 0);
		root.getChildren().add(this.root);

		this.selectedSegments.addListener(obs -> this.update());
		this.viewFrustumProperty.addListener(obs -> this.update());
		this.areMeshesEnabled.addListener((obs, oldv, newv) -> {if (newv) update(); else removeAllMeshes();});

		this.rendererBlockSize.addListener(obs -> {
				bulkUpdate.set(true);
				removeAllMeshes();
				update();
				bulkUpdate.set(false);
				stateChanged();
			});

		this.sceneUpdateHandler = new SceneUpdateHandler(() -> InvokeOnJavaFXApplicationThread.invoke(this::update));
		this.sceneUpdateDelayMsec.addListener(obs -> this.sceneUpdateHandler.update(this.sceneUpdateDelayMsec.get()));
		this.eyeToWorldTransformProperty.addListener(this.sceneUpdateHandler);

		final InvalidationListener meshViewUpdateQueueListener = obs -> meshViewUpdateQueue.update(numElementsPerFrame.get(), frameDelayMsec.get());
		this.numElementsPerFrame.addListener(meshViewUpdateQueueListener);
		this.frameDelayMsec.addListener(meshViewUpdateQueueListener);
	}

	public void addRefreshMeshesListener(final Runnable listener)
	{
		this.refreshMeshes.add(listener);
	}

	private void update()
	{
		final boolean stateChangeNeeded = !bulkUpdate.get();
		if (stateChangeNeeded)
			bulkUpdate.set(true);

		final long[] selectedSegments = this.selectedSegments.getSelectedSegments();
		final Map<Long, MeshGenerator<TLongHashSet>> toBeRemoved = new HashMap<>();
		for (final Entry<Long, MeshGenerator<TLongHashSet>> neuron : neurons.entrySet())
		{
			final long         segment            = neuron.getKey();
			final TLongHashSet fragmentsInSegment = this.selectedSegments.getAssignment().getFragments(segment);
			final boolean      isSelected         = this.selectedSegments.isSegmentSelected(segment);
			final boolean      isConsistent       = neuron.getValue().getId().equals(fragmentsInSegment);
			LOG.debug("Fragments in segment {}: {}", segment, fragmentsInSegment);
			LOG.debug("Segment {} is selected? {}  Is consistent? {}", neuron.getKey(), isSelected, isConsistent);
			if (!isSelected || !isConsistent)
				toBeRemoved.put(segment, neuron.getValue());
		}
		if (toBeRemoved.size() == 1)
			removeMesh(toBeRemoved.entrySet().iterator().next().getKey());
		else if (toBeRemoved.size() > 1)
			removeMeshes(toBeRemoved);

		LOG.debug("Selection count: {}", selectedSegments.length);
		LOG.debug("To be removed count: {}", toBeRemoved.size());
		Arrays
				.stream(selectedSegments)
				.forEach(this::generateMesh);

		if (stateChangeNeeded)
		{
			bulkUpdate.set(false);
			stateChanged();
		}
	}

	@Override
	public void generateMesh(final Long segmentId)
	{
		if (!areMeshesEnabled.get())
		{
			LOG.debug("Meshes not enabled -- will return without creating mesh");
			return;
		}

		if (!this.selectedSegments.isSegmentSelected(segmentId))
		{
			LOG.debug("Id {} not selected -- will return without creating mesh", segmentId);
			return;
		}

		if (rendererBlockSize.get() <= 0)
			return;

		final TLongHashSet fragments = this.selectedSegments.getAssignment().getFragments(segmentId);

		final IntegerProperty color = new SimpleIntegerProperty(stream.argb(segmentId));
		stream.addListener(obs -> color.set(stream.argb(segmentId)));
		this.selectedSegments.getAssignment().addListener(obs -> color.set(stream.argb(segmentId)));

		final Boolean isPresentAndValid = Optional.ofNullable(neurons.get(segmentId)).map(MeshGenerator::getId).map(
				fragments::equals).orElse(false);

		final MeshGenerator<TLongHashSet> meshGenerator;
		if (isPresentAndValid)
		{
			LOG.debug("Id {} already present with valid selection {}", segmentId, fragments);
			meshGenerator = neurons.get(segmentId);
		}
		else
		{
			LOG.debug("Adding mesh for segment {}.", segmentId);
			final MeshSettings meshSettings = this.meshSettings.getOrAddMesh(segmentId);
			meshGenerator = new MeshGenerator<>(
					source,
					fragments,
					blockListCache(),
					meshCache(),
					meshViewUpdateQueue,
					color,
					viewFrustumProperty,
					eyeToWorldTransformProperty,
					unshiftedWorldTransforms,
					meshSettings.preferredScaleLevelProperty().get(),
					meshSettings.highestScaleLevelProperty().get(),
					meshSettings.simplificationIterationsProperty().get(),
					meshSettings.smoothingLambdaProperty().get(),
					meshSettings.smoothingIterationsProperty().get(),
					rendererBlockSize.get(),
					managers,
					workers,
					showBlockBoundaries
			);
			final BooleanProperty isManaged = this.meshSettings.isManagedProperty(segmentId);
			isManaged.addListener((obs, oldv, newv) -> meshGenerator.bindTo(newv
			                                                      ? this.meshSettings.getGlobalSettings()
			                                                      : meshSettings));
			meshGenerator.bindTo(isManaged.get() ? this.meshSettings.getGlobalSettings() : meshSettings);
			neurons.put(segmentId, meshGenerator);
			this.root.getChildren().add(meshGenerator.getRoot());
		}

		meshGenerator.update();

		if (!bulkUpdate.get())
			stateChanged();
	}

	@Override
	public void removeMesh(final Long id)
	{
		final MeshGenerator<TLongHashSet> mesh = neurons.remove(id);
		if (mesh != null)
		{
			InvokeOnJavaFXApplicationThread.invoke(() -> root.getChildren().remove(mesh.getRoot()));
			mesh.interrupt();
			mesh.unbind();
		}
	}


	private void removeMeshes(final Map<Long, MeshGenerator<TLongHashSet>> toBeRemoved)
	{
		toBeRemoved.values().forEach(MeshGenerator::interrupt);

		neurons.keySet().removeAll(toBeRemoved.keySet());
		final List<Node> existingGroups = neurons.values().stream().map(MeshGenerator::getRoot).collect(Collectors.toList());
		InvokeOnJavaFXApplicationThread.invoke(() -> root.getChildren().setAll(existingGroups));

		// unbind() for each mesh here takes way too long for some reason. Do it on a separate thread to avoid app freezing.
		new Thread(() -> toBeRemoved.values().forEach(MeshGenerator::unbind)).start();
	}

	@Override
	public Map<Long, MeshGenerator<TLongHashSet>> unmodifiableMeshMap()
	{
		return Collections.unmodifiableMap(neurons);
	}

	@Override
	public IntegerProperty preferredScaleLevelProperty()
	{
		return this.meshSettings.getGlobalSettings().preferredScaleLevelProperty();
	}

	@Override
	public IntegerProperty highestScaleLevelProperty()
	{
		return this.meshSettings.getGlobalSettings().highestScaleLevelProperty();
	}

	@Override
	public IntegerProperty meshSimplificationIterationsProperty()
	{
		return this.meshSettings.getGlobalSettings().simplificationIterationsProperty();
	}

	@Override
	public void removeAllMeshes()
	{
		final boolean stateChangeNeeded = !bulkUpdate.get();
		if (stateChangeNeeded)
			bulkUpdate.set(true);

		removeMeshes(new HashMap<>(neurons));

		if (stateChangeNeeded)
		{
			bulkUpdate.set(false);
			stateChanged();
		}
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
		return this.selectedSegments.getAssignment().getFragments(t).toArray();
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
	public BooleanProperty showBlockBoundariesProperty()
	{
		return this.showBlockBoundaries;
	}

	@Override
	public IntegerProperty rendererBlockSizeProperty()
	{
		return this.rendererBlockSize;
	}

	@Override
	public IntegerProperty numElementsPerFrameProperty()
	{
		return this.numElementsPerFrame;
	}

	@Override
	public LongProperty frameDelayMsecProperty()
	{
		return this.frameDelayMsec;
	}

	@Override
	public LongProperty sceneUpdateDelayMsecProperty()
	{
		return this.sceneUpdateDelayMsec;
	}

	@Override
	public ManagedMeshSettings managedMeshSettings()
	{
		return this.meshSettings;
	}

	@Override
	public void invalidateCaches()
	{
		Stream.of(this.blockListCacheInvalidate).forEach(InvalidateAll::invalidateAll);
		Stream.of(this.meshCacheInvalidate).forEach(InvalidateAll::invalidateAll);
	}

	public static <D extends IntegerType<D>> MeshManagerWithAssignmentForSegments fromBlockLookup(
			final DataSource<D, ?> dataSource,
			final SelectedSegments selectedSegments,
			final AbstractHighlightingARGBStream stream,
			final Group meshesGroup,
			final ObjectProperty<ViewFrustum> viewFrustumProperty,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
			final LabelBlockLookup labelBlockLookup,
			final GlobalCache globalCache,
			final ExecutorService meshManagerExecutors,
			final PriorityExecutorService<MeshWorkerPriority> meshWorkersExecutors)
	{
		LOG.debug("Data source is type {}", dataSource.getClass());

		final Function<Long, Interval[]>[] blockLists = new Function[dataSource.getNumMipmapLevels()];
		for (int i = 0; i < blockLists.length; ++i)
		{
			final int fi = i;
			blockLists[i] = ThrowingFunction.unchecked(id -> labelBlockLookup.read(fi, id));
		}

		final Function<TLongHashSet, Long[]> segmentToIdArray = ids -> Arrays.stream(ids.toArray()).boxed().toArray(Long[]::new);

		final Function<TLongHashSet, Interval[]>[] segmentBlockLists = BlocksForLabelDelegate.delegate(
				InterruptibleFunction.fromFunction(blockLists),
				segmentToIdArray
			);

		final Pair<InterruptibleFunctionAndCache<TLongHashSet, Interval[]>, Invalidate<TLongHashSet>>[] segmentBlockCaches = CacheUtils.blocksForLabelCaches(
				segmentBlockLists,
				globalCache::createNewCache
			);

		final InterruptibleFunction<TLongHashSet, Interval[]>[] segmentBlockLoaders = Arrays.stream(segmentBlockCaches).map(Pair::getA).toArray(InterruptibleFunction[]::new);
		final InterruptibleFunction<TLongHashSet, Interval[]>[] segmentBlockCachesAndAffectedBlocks;
		if (dataSource instanceof MaskedSource<?, ?>)
		{
			final Function<Long, Interval[]>[] affectedBlocksForLabel = affectedBlocksForLabel((MaskedSource<?, ?>) dataSource);

			final InterruptibleFunction<TLongHashSet, Interval[]>[] affectedBlocksForSegment = BlocksForLabelDelegate.delegate(
					InterruptibleFunction.fromFunction(affectedBlocksForLabel),
					segmentToIdArray
				);

			segmentBlockCachesAndAffectedBlocks = combineInterruptibleFunctions(
					segmentBlockLoaders,
					affectedBlocksForSegment,
					new IntervalsCombiner()
				);
		}
		else
		{
			segmentBlockCachesAndAffectedBlocks = segmentBlockLoaders;
		}

		final Pair<? extends InterruptibleFunction<TLongHashSet, Interval[]>, Invalidate<TLongHashSet>>[] blockCachesAndInvalidate = new Pair[dataSource.getNumMipmapLevels()];
		for (int i = 0; i < blockCachesAndInvalidate.length; ++i)
			blockCachesAndInvalidate[i] = new ValuePair<>(segmentBlockCachesAndAffectedBlocks[i], segmentBlockCaches[i].getB());


		final D d = dataSource.getDataType();
		final Function<TLongHashSet, Converter<D, BoolType>> segmentMaskGenerator = SegmentMaskGenerators.forType(d);

		final Pair<InterruptibleFunctionAndCache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>, Invalidate<ShapeKey<TLongHashSet>>>[] meshCachesAndInvalidate = CacheUtils
				.segmentMeshCacheLoaders(
						dataSource,
						segmentMaskGenerator,
						globalCache::createNewCache);

		final MeshManagerWithAssignmentForSegments manager = new MeshManagerWithAssignmentForSegments(
				dataSource,
				blockCachesAndInvalidate,
				meshCachesAndInvalidate,
				meshesGroup,
				viewFrustumProperty,
				eyeToWorldTransformProperty,
				new ManagedMeshSettings(dataSource.getNumMipmapLevels()),
				selectedSegments,
				stream,
				meshManagerExecutors,
				meshWorkersExecutors
			);
		manager.addRefreshMeshesListener(() -> {
			LOG.debug("Refreshing meshes!");
			Stream
					.of(meshCachesAndInvalidate)
					.map(Pair::getB)
					.forEach(InvalidateAll::invalidateAll);
			final long[] selection     = selectedSegments.getSelectedIds().getActiveIds();
			final long   lastSelection = selectedSegments.getSelectedIds().getLastSelection();
			selectedSegments.getSelectedIds().deactivateAll();
			selectedSegments.getSelectedIds().activate(selection);
			selectedSegments.getSelectedIds().activateAlso(lastSelection);
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

	private static Function<Long, Interval[]>[] affectedBlocksForLabel(final MaskedSource<?, ?> source)
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
