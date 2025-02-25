package org.janelia.saalfeldlab.paintera.state;

import bdv.cache.SharedQueue;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.algorithm.fill.FloodFill;
import net.imglib2.algorithm.lazy.Lazy;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.Invalidate;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import org.janelia.saalfeldlab.net.imglib2.converter.ARGBColorConverter;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.BooleanType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.util.Intervals;
import org.janelia.saalfeldlab.net.imglib2.util.ValueTriple;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.cache.InvalidateDelegates;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.Interpolations;
import org.janelia.saalfeldlab.paintera.data.PredicateDataSource;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.meshes.MeshViewUpdateQueue;
import org.janelia.saalfeldlab.paintera.meshes.PainteraTriangleMesh;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.janelia.saalfeldlab.paintera.meshes.cache.GenericMeshCacheLoader;
import org.janelia.saalfeldlab.paintera.meshes.managed.GetBlockListFor;
import org.janelia.saalfeldlab.paintera.meshes.managed.GetMeshFor;
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithSingleMesh;
import org.janelia.saalfeldlab.util.Colors;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.janelia.saalfeldlab.util.TmpVolatileHelpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.janelia.saalfeldlab.net.imglib2.converter.read.read.ConvertedRandomAccessibleInterval;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class IntersectingSourceState<K1 extends MeshCacheKey, K2 extends MeshCacheKey>
		extends
		MinimalSourceState<UnsignedByteType, VolatileUnsignedByteType, DataSource<UnsignedByteType, VolatileUnsignedByteType>, ARGBColorConverter<VolatileUnsignedByteType>>
		implements IntersectableSourceState<UnsignedByteType, VolatileUnsignedByteType, IntersectingSourceStateMeshCacheKey<K1, K2>> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final boolean DEFAULT_MESHES_ENABLED = true;

	public static final ExecutorService INTERSECTION_FILL_SERVICE = Executors.newFixedThreadPool(Math.min(16, Runtime.getRuntime().availableProcessors() - 1),
			new NamedThreadFactory("intersection-floodfill-%s", true));

	private final ObjectProperty<K1> fillSourceMeshCacheKeyProperty = new SimpleObjectProperty<>(null);

	private final ObjectProperty<K2> seedSourceMeshCacheKeyProperty = new SimpleObjectProperty<>(null);

	private final ObjectBinding<IntersectingSourceStateMeshCacheKey<K1, K2>> intersectionMeshCacheKeyBinding = Bindings.createObjectBinding(
			() -> {
				final var fillSourceMeschCacheKey = fillSourceMeshCacheKeyProperty.getValue();
				final var seedSourceMeshCacheKey = seedSourceMeshCacheKeyProperty.getValue();
				return new IntersectingSourceStateMeshCacheKey<>(fillSourceMeschCacheKey, seedSourceMeshCacheKey);
			},
			// dependsOn
			fillSourceMeshCacheKeyProperty, seedSourceMeshCacheKeyProperty);

	private final ObjectBinding<Color> colorProperty = Bindings.createObjectBinding(() -> Colors.toColor(converter().getColor()), converter().colorProperty());

	private final SimpleBooleanProperty requestRepaintProperty = new SimpleBooleanProperty(false);

	private final MeshManagerWithSingleMesh<IntersectingSourceStateMeshCacheKey<K1, K2>> meshManager;

	/* TODO: Is there a better way to do this so we don't have an internal predicate source intermediate?
	 *   	Would be better if we could just use BoolType for `getDataSource` but BoolType is not a `NativeType` */
	private final PredicateDataSource<UnsignedByteType, VolatileUnsignedByteType, Predicate<UnsignedByteType>> predicateDataSource = new PredicateDataSource<>(
			getDataSource(),
			b -> b.get() > 0,
			"internal_predicateIntersectionSource"
	);

	private final GetUnionBlockListFor<K1, K2> getGetUnionBlockListFor;

	public IntersectingSourceState(
			final IntersectableSourceState<?, ?, K1> fillSource,
			final IntersectableSourceState<?, ?, K2> seedSource,
			final Composite<ARGBType, ARGBType> composite,
			final String name,
			final int priority,
			final PainteraBaseView viewer) {

		this(
				fillSource,
				seedSource,
				createIntersectionFilledDataSource(fillSource.getIntersectableMask(), seedSource.getIntersectableMask(), viewer.getQueue(), priority, name),
				composite,
				name,
				viewer,
				fillSource.getMeshCacheKeyBinding(),
				seedSource.getMeshCacheKeyBinding(),
				fillSource.getGetBlockListFor(),
				seedSource.getGetBlockListFor());
	}

	public IntersectingSourceState(
			final IntersectableSourceState<?, ?, K1> fillSource,
			final IntersectableSourceState<?, ?, K2> seedSource,
			final Composite<ARGBType, ARGBType> composite,
			final String name,
			final SharedQueue queue,
			final int priority,
			final PainteraBaseView viewer) {

		this(
				fillSource,
				seedSource,
				createIntersectionFilledDataSource(fillSource.getIntersectableMask(), seedSource.getIntersectableMask(), queue, priority, name),
				composite,
				name,
				viewer,
				fillSource.getMeshCacheKeyBinding(),
				seedSource.getMeshCacheKeyBinding(),
				fillSource.getGetBlockListFor(),
				seedSource.getGetBlockListFor()
		);

	}

	private IntersectingSourceState(
			final IntersectableSourceState<?, ?, K1> fillSource,
			final IntersectableSourceState<?, ?, K2> seedSource,
			final ObservableDataSource<UnsignedByteType, VolatileUnsignedByteType> intersectSource,
			final Composite<ARGBType, ARGBType> composite,
			final String name,
			final PainteraBaseView viewer,
			final ObservableValue<K1> fillSourceChangeListener,
			final ObservableValue<K2> seedSourceChangeListener,
			final GetBlockListFor<K1> fillBlockListFor,
			final GetBlockListFor<K2> seedBlockListFor
	) {
		// TODO use better converter
		super(
				intersectSource.getDataSource(),
				new ARGBColorConverter.Imp0<>(0, 1),
				composite,
				name,
				// dependsOn:
				fillSource,
				seedSource);

		this.fillSourceMeshCacheKeyProperty.bind(fillSourceChangeListener);
		this.seedSourceMeshCacheKeyProperty.bind(seedSourceChangeListener);
		this.getGetUnionBlockListFor = getGetUnionBlockListFor(fillBlockListFor, seedBlockListFor);
		this.meshManager = createMeshManager(viewer, getGetUnionBlockListFor);
		this.intersectionMeshCacheKeyBinding.subscribe(key -> {
			if (key == null)
				return;
			INTERSECTION_FILL_SERVICE.submit(() -> {
				getDataSource().invalidateAll();
				requestRepaint();
				getMeshManager().submitMeshJob(key);
			});
		});

		this.meshManager.getRendererSettings().getMeshesEnabledProperty().subscribe(enabled -> {
			if (enabled)
				refreshMeshes();
		});
		intersectSource.getProperty().subscribe(intersect -> {
			if (intersect) {
				requestRepaint();
				intersectSource.getProperty().set(false);
			}
		});
	}

	private MeshManagerWithSingleMesh<IntersectingSourceStateMeshCacheKey<K1, K2>> createMeshManager(
			final PainteraBaseView viewer,
			final GetUnionBlockListFor<K1, K2> getUnionBlockListFor) {

		CacheLoader<ShapeKey<IntersectingSourceStateMeshCacheKey<K1, K2>>, PainteraTriangleMesh> loader = getCacheLoader();
		GetMeshFor.FromCache<IntersectingSourceStateMeshCacheKey<K1, K2>> getMeshFor = GetMeshFor.FromCache.fromLoader(loader);

		return new MeshManagerWithSingleMesh<>(
				getDataSource(),
				getUnionBlockListFor,
				new WrappedGetMeshFromMeshCacheKey<>(getMeshFor),
				viewer.viewer3D().getViewFrustumProperty(),
				viewer.viewer3D().getEyeToWorldTransformProperty(),
				viewer.getMeshManagerExecutorService(),
				viewer.getMeshWorkerExecutorService(),
				new MeshViewUpdateQueue<>());
	}

	private static <K1 extends MeshCacheKey, K2 extends MeshCacheKey>
	GetUnionBlockListFor<K1, K2> getGetUnionBlockListFor(GetBlockListFor<K1> firstGetBlockListFor, GetBlockListFor<K2> secondGetBlockListFor) {

		return new GetUnionBlockListFor<>(firstGetBlockListFor, secondGetBlockListFor);
	}

	private CacheLoader<ShapeKey<IntersectingSourceStateMeshCacheKey<K1, K2>>, PainteraTriangleMesh> getCacheLoader() {

		return new GenericMeshCacheLoader<>(
				level -> predicateDataSource.getDataSource(0, level),
				level -> predicateDataSource.getSourceTransformCopy(0, level)
		);
	}

	@Override
	public DataSource<BoolType, Volatile<BoolType>> getIntersectableMask() {

		return predicateDataSource;
	}

	@Override
	public ObjectBinding<IntersectingSourceStateMeshCacheKey<K1, K2>> getMeshCacheKeyBinding() {

		return this.intersectionMeshCacheKeyBinding;
	}

	@Override
	public GetBlockListFor<IntersectingSourceStateMeshCacheKey<K1, K2>> getGetBlockListFor() {

		return this.getGetUnionBlockListFor;
	}

	private static class GetUnionBlockListFor<K1 extends MeshCacheKey, K2 extends MeshCacheKey>
			implements GetBlockListFor<IntersectingSourceStateMeshCacheKey<K1, K2>> {

		final GetBlockListFor<K1> firstGetBlockListFor;
		final GetBlockListFor<K2> secondGetBlockListFor;

		private GetUnionBlockListFor(GetBlockListFor<K1> firstGetBlockListFor, GetBlockListFor<K2> secondGetBlockListFor) {

			this.firstGetBlockListFor = firstGetBlockListFor;
			this.secondGetBlockListFor = secondGetBlockListFor;
		}

		@Override
		public Interval[] getBlocksFor(int level, IntersectingSourceStateMeshCacheKey<K1, K2> key) {

			final var firstKey = key.getFirstKey();
			final var secondKey = key.getSecondKey();
			final var firstBlocks = firstGetBlockListFor.getBlocksFor(level, firstKey);
			final var secondBlocks = secondGetBlockListFor.getBlocksFor(level, secondKey);
			final var intersectionSet = new HashSet<Interval>();

			for (Interval firstBlock : firstBlocks) {
				for (Interval secondBlock : secondBlocks) {
					final FinalInterval intersection = Intervals.intersect(firstBlock, secondBlock);
					if (!Intervals.isEmpty(intersection)) {
						intersectionSet.add(firstBlock);
						intersectionSet.add(secondBlock);
						break;
					}
				}
			}
			return intersectionSet.toArray(Interval[]::new);
		}
	}

	@Override
	public void onAdd(PainteraBaseView paintera) {

		paintera.viewer3D().getMeshesGroup().getChildren().add(meshManager.getMeshesGroup());

		meshManager.getViewerEnabledProperty().bind(paintera.viewer3D().getMeshesEnabled());
		meshManager.getRendererSettings().getShowBlockBoundariesProperty().bind(paintera.viewer3D().getShowBlockBoundaries());
		meshManager.getRendererSettings().getBlockSizeProperty().bind(paintera.viewer3D().getRendererBlockSize());
		meshManager.getRendererSettings().getNumElementsPerFrameProperty().bind(paintera.viewer3D().getNumElementsPerFrame());
		meshManager.getRendererSettings().getFrameDelayMsecProperty().bind(paintera.viewer3D().getFrameDelayMsec());
		meshManager.getRendererSettings().getSceneUpdateDelayMsecProperty().bind(paintera.viewer3D().getSceneUpdateDelayMsec());
		meshManager.getColorProperty().bind(colorProperty);

		requestRepaintProperty.addListener((obs, oldv, newv) -> {
			if (newv) {
				paintera.orthogonalViews().requestRepaint();
				requestRepaintProperty.set(false);
			}
		});

		colorProperty.addListener((obs, old, newv) -> this.requestRepaint());
		try {
			BuildersKt.runBlocking(
					EmptyCoroutineContext.INSTANCE,
					(scope, continuation) -> getMeshManager().createMeshFor(getMeshCacheKeyBinding().get(), continuation)
			);
		} catch (InterruptedException e) {
			LOG.debug("create mesh interrupted");
		}
	}

	private void requestRepaint() {

		requestRepaintProperty.set(true);
	}

	@Override
	public void onRemoval(SourceInfo sourceInfo) {

		LOG.info("Removed IntersectingSourceState {}", nameProperty().get());
		meshManager.removeAllMeshes();
	}

	public boolean areMeshesEnabled() {

		return this.meshManager.getManagedSettings().getMeshesEnabledProperty().get();
	}

	public void setMeshesEnabled(final boolean enabled) {

		this.meshManager.getManagedSettings().getMeshesEnabledProperty().set(enabled);
	}

	public void refreshMeshes() {

		meshManager.refreshMeshes();
	}

	public MeshManagerWithSingleMesh<IntersectingSourceStateMeshCacheKey<K1, K2>> getMeshManager() {

		return this.meshManager;
	}

	/**
	 * Intersect two sources to create a new source as the result of the intersection.
	 * <p>
	 * Note: The resultant data source is not a strict intersection. Instead, it takes all seed points from the strict intersection
	 * of the two sourcecs, and runs a flood fill algorithm into the fillDataSource, starting at each of the seed points.
	 *
	 * @param seedDataSource First source to intersect against
	 * @param fillDataSource Second source to intersect against. Used for intersection Fill
	 * @param queue
	 * @param priority
	 * @param name           of the resultant source
	 * @param <B>            data type of the input sources
	 * @return
	 */
	private static <B extends BooleanType<B>> ObservableDataSource<UnsignedByteType, VolatileUnsignedByteType> createIntersectionFilledDataSource(
			final DataSource<B, Volatile<B>> fillDataSource,
			final DataSource<B, Volatile<B>> seedDataSource,
			final SharedQueue queue,
			final int priority,
			final String name) {

		LOG.debug("Number of mipmap labels: source 1={} source 2={}",
				fillDataSource.getNumMipmapLevels(),
				seedDataSource.getNumMipmapLevels()
		);
		if (fillDataSource.getNumMipmapLevels() != seedDataSource.getNumMipmapLevels()) {
			throw new RuntimeException("Incompatible sources (num mip map levels )");
		}

		final AffineTransform3D[] transforms = new AffineTransform3D[fillDataSource.getNumMipmapLevels()];

		final RandomAccessibleInterval<UnsignedByteType>[] data = new RandomAccessibleInterval[transforms.length];
		final RandomAccessibleInterval<VolatileUnsignedByteType>[] vdata = new RandomAccessibleInterval[transforms.length];
		final Invalidate<Long>[] invalidate = new Invalidate[transforms.length];
		final Invalidate<Long>[] vinvalidate = new Invalidate[transforms.length];

		final var fillUpdateListener = new SimpleBooleanProperty(false);

		for (int level = fillDataSource.getNumMipmapLevels() - 1; level >= 0; level--) {
			final AffineTransform3D tf1 = fillDataSource.getSourceTransformCopy(0, level);
			final AffineTransform3D tf2 = seedDataSource.getSourceTransformCopy(0, level);
			if (!Arrays.equals(tf1.getRowPackedCopy(), tf2.getRowPackedCopy()))
				throw new RuntimeException("Incompatible sources ( transforms )");

			final var fillRAI = fillDataSource.getDataSource(0, level);
			final var seedRAI = seedDataSource.getDataSource(0, level);

			final int[] cellDimensions;
			if (seedRAI instanceof ConvertedRandomAccessibleInterval
					&& ((ConvertedRandomAccessibleInterval<?, ?>) seedRAI).getSource() instanceof CachedCellImg) {
				var crai2 = (ConvertedRandomAccessibleInterval<?, ?>) seedRAI;
				var cachedCellImg = (CachedCellImg<?, ?>) crai2.getSource();
				var grid = cachedCellImg.getCellGrid();
				cellDimensions = new int[grid.numDimensions()];
				grid.cellDimensions(cellDimensions);
			} else {
				cellDimensions = new int[]{64, 64, 64};
			}

			LOG.debug("Making intersect for level={} with block size={}", level, cellDimensions);

			final BooleanProperty seedPointsUpdated = new SimpleBooleanProperty(false);
			final HashSet<Point> seedPoints = new HashSet<>();

			final CachedCellImg<UnsignedByteType, ?> img = generateLazyImgWithSeedIntersectionDetection(fillDataSource, seedDataSource, level, cellDimensions,
					seedPointsUpdated, seedPoints);

			addFillFromSeedsListener(fillDataSource, level, seedPointsUpdated, seedPoints, img, fillUpdateListener);

			// TODO cannot use VolatileViews because we need access to cache
			final TmpVolatileHelpers.RaiWithInvalidate<VolatileUnsignedByteType> vimg = TmpVolatileHelpers.createVolatileCachedCellImgWithInvalidate(
					(CachedCellImg<UnsignedByteType, VolatileByteArray>) img,
					queue,
					new CacheHints(LoadingStrategy.VOLATILE, priority, true));

			data[level] = img;
			vdata[level] = vimg.getRai();
			invalidate[level] = img.getCache();
			vinvalidate[level] = vimg.getInvalidate();
			transforms[level] = tf1;
		}

		return new ObservableDataSource<>(fillUpdateListener, new RandomAccessibleIntervalDataSource<>(
				new ValueTriple<>(data, vdata, transforms),
				new InvalidateDelegates<>(Arrays.asList(new InvalidateDelegates<>(invalidate), new InvalidateDelegates<>(vinvalidate))),
				Interpolations.nearestNeighbor(),
				Interpolations.nearestNeighbor(),
				name));
	}

	private static <B extends BooleanType<B>> void addFillFromSeedsListener(
			final DataSource<B, Volatile<B>> fillSource,
			final int level,
			final BooleanProperty seedPointsUpdated,
			final HashSet<Point> seedPoints,
			final RandomAccessibleInterval<UnsignedByteType> img,
			final BooleanProperty fillUpdateProp) {

		seedPointsUpdated.addListener((obs, oldv, newv) -> {
			if (newv) {
				final Point[] seedPointsCopy;
				synchronized (seedPoints) {
					seedPointsCopy = seedPoints.toArray(Point[]::new);
					seedPoints.clear();
					seedPointsUpdated.set(false);
				}
				INTERSECTION_FILL_SERVICE.submit(() -> {
					final var filledFromSeeds = fillFromSeedPoints(fillSource.getDataSource(0, level), img, seedPointsCopy);
					fillUpdateProp.set(filledFromSeeds);
				});
			}
		});
	}

	private static <B extends BooleanType<B>> CachedCellImg<UnsignedByteType, ?> generateLazyImgWithSeedIntersectionDetection(
			DataSource<B, Volatile<B>> fillDataSource, DataSource<B, Volatile<B>> seedDataSource,
			int level, int[] cellDimensions,
			BooleanProperty seedPointsUpdated, HashSet<Point> seedPoints) {

		final var initFillRAI = fillDataSource.getDataSource(0, level);
		//noinspection CodeBlock2Expr
		return Lazy.generate(initFillRAI, cellDimensions, new UnsignedByteType(), AccessFlags.setOf(AccessFlags.VOLATILE), cell -> {
			INTERSECTION_FILL_SERVICE.submit(() -> {
				final var fillRAI = fillDataSource.getDataSource(0, level);
				final var seedRAI = seedDataSource.getDataSource(0, level);
				LOG.debug("Updating Intersection Points");
				final var newIntersectionPoints = detectIntersectionPoints(seedRAI, fillRAI, cell);
				if (!newIntersectionPoints.isEmpty()) {
					synchronized (seedPoints) {
						seedPoints.addAll(newIntersectionPoints);
						seedPointsUpdated.set(true);
					}
				}
			});
		});
	}

	private static <B extends BooleanType<B>> boolean fillFromSeedPoints(RandomAccessibleInterval<B> fillRAI, RandomAccessibleInterval<UnsignedByteType> img,
	                                                                     Point[] seedPointsCopy) {

		LOG.debug("Filling from seed points");
		boolean filledFromSeed = false;
		for (Point seed : seedPointsCopy) {
			if (img.getAt(seed).get() == 0) {
				LOG.trace("Intersection Floodfill at seed:  {}", seed);
				FloodFill.fill(
						fillRAI,
						img,
						seed,
						new UnsignedByteType(1),
						new DiamondShape(1),
						(BiPredicate<B, UnsignedByteType>) (source, target) -> source.get() && target.get() == 0
				);
				filledFromSeed = true;
			}
		}
		return filledFromSeed;
	}

	private static <B extends BooleanType<B>> HashSet<Point> detectIntersectionPoints(RandomAccessibleInterval<B> seedRAI, RandomAccessibleInterval<B> fillRAI,
	                                                                                  RandomAccessibleInterval<UnsignedByteType> cell) {

		LOG.trace(
				"Detecting seed point in cell {} {}",
				Intervals.minAsLongArray(cell),
				Intervals.maxAsLongArray(cell)
		);
		final IntervalView<B> seedInterval = Views.interval(Views.extendZero(seedRAI), cell);
		final IntervalView<B> fillInterval = Views.interval(Views.extendZero(fillRAI), cell);
		final Cursor<B> seedCursor = Views.flatIterable(seedInterval).cursor();
		final Cursor<B> fillCursor = Views.flatIterable(fillInterval).cursor();
		final Cursor<UnsignedByteType> targetCursor = Views.flatIterable(cell).localizingCursor();

		final var seedSet = new HashSet<Point>();
		var moveFwd = 1;
		while (targetCursor.hasNext() && seedCursor.hasNext() && fillCursor.hasNext()) {
			final UnsignedByteType targetType = targetCursor.next();
			if (targetType.get() != 0) {
				moveFwd++;
			} else {
				seedCursor.jumpFwd(moveFwd);
				fillCursor.jumpFwd(moveFwd);
				moveFwd = 1;
				final B seedType = seedCursor.get();
				final B fillType = fillCursor.get();
				if (fillType.get() && seedType.get()) {
					seedSet.add(targetCursor.positionAsPoint());
				}
			}
		}
		return seedSet;
	}

	@Override
	public Node preferencePaneNode() {

		return new IntersectingSourceStatePreferencePaneNode(this).getNode();
	}
}
