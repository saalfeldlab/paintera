package org.janelia.saalfeldlab.paintera.meshes;

import com.pivovarit.function.ThrowingFunction;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.scene.Group;
import javafx.scene.Node;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.cache.Invalidate;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.converter.Converter;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.labels.blocks.CachedLabelBlockLookup;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookupKey;
import org.janelia.saalfeldlab.paintera.cache.NoOpInvalidate;
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

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Philipp Hanslovsky
 */
public class MeshManagerWithAssignmentForSegments implements MeshManager<Long, TLongHashSet>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static class BlockTreeParametersKey
	{
		public final int preferredScaleLevel, highestScaleLevel;

		public BlockTreeParametersKey(final int preferredScaleLevel, final int highestScaleLevel)
		{
			this.preferredScaleLevel = preferredScaleLevel;
			this.highestScaleLevel = highestScaleLevel;
		}

		@Override
		public int hashCode()
		{
			return 31 * preferredScaleLevel + highestScaleLevel;
		}

		@Override
		public boolean equals(final Object obj)
		{
			if (super.equals(obj))
				return true;
			if (obj instanceof BlockTreeParametersKey)
			{
				final BlockTreeParametersKey other = (BlockTreeParametersKey) obj;
				return preferredScaleLevel == other.preferredScaleLevel && highestScaleLevel == other.highestScaleLevel;
			}
			return false;
		}
	}

	private final DataSource<?, ?> source;

	private final LabelBlockLookup labelBlockLookup;

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

	private final BooleanProperty areMeshesEnabledProperty = new SimpleBooleanProperty(true);

	private final BooleanProperty showBlockBoundariesProperty = new SimpleBooleanProperty(false);

	private final IntegerProperty rendererBlockSizeProperty = new SimpleIntegerProperty(Viewer3DConfig.RENDERER_BLOCK_SIZE_DEFAULT_VALUE);

	private final IntegerProperty numElementsPerFrameProperty = new SimpleIntegerProperty(Viewer3DConfig.NUM_ELEMENTS_PER_FRAME_DEFAULT_VALUE);

	private final LongProperty frameDelayMsecProperty = new SimpleLongProperty(Viewer3DConfig.FRAME_DELAY_MSEC_DEFAULT_VALUE);

	private final LongProperty sceneUpdateDelayMsecProperty = new SimpleLongProperty(Viewer3DConfig.SCENE_UPDATE_DELAY_MSEC_DEFAULT_VALUE);

	private final MeshViewUpdateQueue<TLongHashSet> meshViewUpdateQueue = new MeshViewUpdateQueue<>();

	private final SceneUpdateHandler sceneUpdateHandler;

	private final InvalidationListener sceneUpdateInvalidationListener;

	private final Map<BlockTreeParametersKey, BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>>> sceneBlockTrees = new HashMap<>();

	private CellGrid[] rendererGrids;

	public MeshManagerWithAssignmentForSegments(
			final DataSource<?, ?> source,
			final LabelBlockLookup labelBlockLookup,
			final Pair<? extends InterruptibleFunction<TLongHashSet, Interval[]>, Invalidate<TLongHashSet>>[] blockListCacheAndInvalidate,
			final Pair<? extends InterruptibleFunction<ShapeKey<TLongHashSet>, Pair<float[], float[]>>, ? extends Invalidate<ShapeKey<TLongHashSet>>>[] meshCacheAndInvalidate,
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
		this.labelBlockLookup = labelBlockLookup;
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

		this.sceneUpdateInvalidationListener = obs -> update();

		this.unshiftedWorldTransforms = DataSource.getUnshiftedWorldTransforms(source, 0);
		root.getChildren().add(this.root);

		this.selectedSegments.addListener(sceneUpdateInvalidationListener);
		this.viewFrustumProperty.addListener(sceneUpdateInvalidationListener);

		this.areMeshesEnabledProperty.addListener((obs, oldv, newv) -> { if (newv) update(); else removeAllMeshes(); });

		this.rendererBlockSizeProperty.addListener(obs -> {
				this.rendererGrids = RendererBlockSizes.getRendererGrids(this.source, this.rendererBlockSizeProperty.get());
				update();
			});

		preferredScaleLevelProperty().addListener(sceneUpdateInvalidationListener);
		highestScaleLevelProperty().addListener(sceneUpdateInvalidationListener);

		this.sceneUpdateHandler = new SceneUpdateHandler(() -> InvokeOnJavaFXApplicationThread.invoke(this::update));
		this.sceneUpdateDelayMsecProperty.addListener(obs -> this.sceneUpdateHandler.update(this.sceneUpdateDelayMsecProperty.get()));
		this.eyeToWorldTransformProperty.addListener(this.sceneUpdateHandler);

		final InvalidationListener meshViewUpdateQueueListener = obs -> meshViewUpdateQueue.update(numElementsPerFrameProperty.get(), frameDelayMsecProperty.get());
		this.numElementsPerFrameProperty.addListener(meshViewUpdateQueueListener);
		this.frameDelayMsecProperty.addListener(meshViewUpdateQueueListener);
	}

	private void update()
	{
		if (this.rendererGrids == null)
			return;

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

		sceneBlockTrees.clear();

		LOG.debug("Selection count: {}", selectedSegments.length);
		LOG.debug("To be removed count: {}", toBeRemoved.size());
		Arrays
				.stream(selectedSegments)
				.forEach(this::generateMesh);

		System.out.println("Updated " + neurons.size() + " labels");
	}


	@Override
	public void generateMesh(final Long segmentId)
	{
		if (!areMeshesEnabledProperty.get())
		{
			LOG.debug("Meshes not enabled -- will return without creating mesh");
			return;
		}

		if (!this.selectedSegments.isSegmentSelected(segmentId))
		{
			LOG.debug("Id {} not selected -- will return without creating mesh", segmentId);
			return;
		}

		if (this.rendererGrids == null)
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
					blockListCache,
					meshCache,
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
					rendererBlockSizeProperty.get(),
					managers,
					workers,
					showBlockBoundariesProperty
			);
			final BooleanProperty isManaged = this.meshSettings.isManagedProperty(segmentId);
			isManaged.addListener((obs, oldv, newv) -> {
				if (newv) {
					meshGenerator.preferredScaleLevelProperty().removeListener(sceneUpdateInvalidationListener);
					meshGenerator.highestScaleLevelProperty().removeListener(sceneUpdateInvalidationListener);
					meshGenerator.bindTo(this.meshSettings.getGlobalSettings());
				} else {
					meshGenerator.bindTo(meshSettings);
					meshGenerator.preferredScaleLevelProperty().addListener(sceneUpdateInvalidationListener);
					meshGenerator.highestScaleLevelProperty().addListener(sceneUpdateInvalidationListener);
				}
				update();
			});
			meshGenerator.bindTo(isManaged.get() ? this.meshSettings.getGlobalSettings() : meshSettings);
			neurons.put(segmentId, meshGenerator);
			this.root.getChildren().add(meshGenerator.getRoot());
		}

		final BlockTreeParametersKey blockTreeParametersKey = new BlockTreeParametersKey(
				meshGenerator.preferredScaleLevelProperty().get(),
				meshGenerator.highestScaleLevelProperty().get()
			);

		if (!sceneBlockTrees.containsKey(blockTreeParametersKey))
		{
			sceneBlockTrees.put(blockTreeParametersKey, SceneBlockTree.createSceneBlockTree(
					source,
					viewFrustumProperty.get(),
					eyeToWorldTransformProperty.get(),
					blockTreeParametersKey.preferredScaleLevel,
					blockTreeParametersKey.highestScaleLevel,
					rendererGrids
			));
		}

		meshGenerator.update(sceneBlockTrees.get(blockTreeParametersKey), rendererGrids);
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
		removeMeshes(new HashMap<>(neurons));
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
		LOG.debug("Refreshing meshes!");
		invalidateCaches();
		final long[] selection     = selectedSegments.getSelectedIds().getActiveIds();
		final long   lastSelection = selectedSegments.getSelectedIds().getLastSelection();
		selectedSegments.getSelectedIds().deactivateAll();
		selectedSegments.getSelectedIds().activate(selection);
		selectedSegments.getSelectedIds().activateAlso(lastSelection);
	}

	@Override
	public BooleanProperty areMeshesEnabledProperty()
	{
		return this.areMeshesEnabledProperty;
	}

	@Override
	public BooleanProperty showBlockBoundariesProperty()
	{
		return this.showBlockBoundariesProperty;
	}

	@Override
	public IntegerProperty rendererBlockSizeProperty()
	{
		return this.rendererBlockSizeProperty;
	}

	@Override
	public IntegerProperty numElementsPerFrameProperty()
	{
		return this.numElementsPerFrameProperty;
	}

	@Override
	public LongProperty frameDelayMsecProperty()
	{
		return this.frameDelayMsecProperty;
	}

	@Override
	public LongProperty sceneUpdateDelayMsecProperty()
	{
		return this.sceneUpdateDelayMsecProperty;
	}

	@Override
	public ManagedMeshSettings managedMeshSettings()
	{
		return this.meshSettings;
	}

	@Override
	public void invalidateCaches()
	{
		Stream.of(this.blockListCacheInvalidate).forEach(Invalidate::invalidateAll);
		Stream.of(this.meshCacheInvalidate).forEach(Invalidate::invalidateAll);
	}

	public static <D extends IntegerType<D>> MeshManagerWithAssignmentForSegments fromBlockLookup(
			final DataSource<D, ?> dataSource,
			final SelectedSegments selectedSegments,
			final AbstractHighlightingARGBStream stream,
			final Group meshesGroup,
			final ObjectProperty<ViewFrustum> viewFrustumProperty,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
			final LabelBlockLookup labelBlockLookup,
			final ExecutorService meshManagerExecutors,
			final PriorityExecutorService<MeshWorkerPriority> meshWorkersExecutors)
	{
		LOG.debug("Data source is type {}", dataSource.getClass());

		// Set up block lists for a given label. Block lists are requested for individual label IDs,
		// so some extra boilerplate is required to provide conversion between TLongHashSet and a collection of Longs.
		final Function<Long, Interval[]>[] blockLists = new Function[dataSource.getNumMipmapLevels()];
		Arrays.setAll(blockLists, i -> ThrowingFunction.unchecked(id -> labelBlockLookup.read(new LabelBlockLookupKey(i, (long) id))));

		final Function<TLongHashSet, Long[]> segmentToIdArray = ids -> Arrays.stream(ids.toArray()).boxed().toArray(Long[]::new);
		final InterruptibleFunction<TLongHashSet, Interval[]>[] segmentBlockLists = BlocksForLabelDelegate.delegate(
				InterruptibleFunction.fromFunction(blockLists),
				segmentToIdArray
			);

		final InterruptibleFunction<TLongHashSet, Interval[]>[] segmentBlockCachesAndAffectedBlocks;
		if (dataSource instanceof MaskedSource<?, ?>)
		{
			final Function<Long, Interval[]>[] affectedBlocksForLabel = affectedBlocksForLabel((MaskedSource<?, ?>) dataSource);

			final InterruptibleFunction<TLongHashSet, Interval[]>[] affectedBlocksForSegment = BlocksForLabelDelegate.delegate(
					InterruptibleFunction.fromFunction(affectedBlocksForLabel),
					segmentToIdArray
				);

			segmentBlockCachesAndAffectedBlocks = combineInterruptibleFunctions(
					segmentBlockLists,
					affectedBlocksForSegment,
					new IntervalsCombiner()
				);
		}
		else
		{
			segmentBlockCachesAndAffectedBlocks = segmentBlockLists;
		}

		final Pair<? extends InterruptibleFunction<TLongHashSet, Interval[]>, Invalidate<TLongHashSet>>[] blockCachesAndInvalidate = new Pair[dataSource.getNumMipmapLevels()];
		for (int i = 0; i < blockCachesAndInvalidate.length; ++i)
		{
			final int scaleLevel = i;
			final Invalidate<TLongHashSet> blockCacheInvalidate;
			if (labelBlockLookup instanceof CachedLabelBlockLookup)
			{
				final CachedLabelBlockLookup cachedLabelBlockLookup = (CachedLabelBlockLookup) labelBlockLookup;
				blockCacheInvalidate = new Invalidate<TLongHashSet>()
				{
					@Override
					public void invalidate(final TLongHashSet key)
					{
						Arrays.stream(key.toArray()).forEach(
								id -> cachedLabelBlockLookup.invalidate(new LabelBlockLookupKey(scaleLevel, id))
							);
					}

					@Override
					public void invalidateAll(final long parallelismThreshold)
					{
						cachedLabelBlockLookup.invalidateIf(
								parallelismThreshold,
								lookupKey -> lookupKey.getLevel() == scaleLevel
							);
					}

					@Override
					public void invalidateIf(final long parallelismThreshold, final Predicate<TLongHashSet> condition)
					{
						throw new UnsupportedOperationException("Impossible to map Long keys in the actual cache back into TLongHashSet");
					}
				};
			}
			else
			{
				blockCacheInvalidate = new NoOpInvalidate<>();
			}
			blockCachesAndInvalidate[i] = new ValuePair<>(segmentBlockCachesAndAffectedBlocks[i], blockCacheInvalidate);
		}


		// Set up mesh caches
		final D d = dataSource.getDataType();
		final Function<TLongHashSet, Converter<D, BoolType>> segmentMaskGenerator = SegmentMaskGenerators.forType(d);

		final InterruptibleFunctionAndCache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>[] meshCaches = CacheUtils.segmentMeshCacheLoaders(
				dataSource,
				segmentMaskGenerator,
				loader -> new SoftRefLoaderCache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>().withLoader(loader)
			);

		final Pair<? extends InterruptibleFunctionAndCache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>, ? extends Invalidate<ShapeKey<TLongHashSet>>>[]
				meshCachesAndInvalidate = new Pair[meshCaches.length];
		Arrays.setAll(meshCachesAndInvalidate, i -> new ValuePair<>(meshCaches[i], meshCaches[i]));

		final MeshManagerWithAssignmentForSegments manager = new MeshManagerWithAssignmentForSegments(
				dataSource,
				labelBlockLookup,
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
