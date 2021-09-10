package org.janelia.saalfeldlab.paintera.state;

import bdv.util.volatiles.SharedQueue;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccessible;
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
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.BooleanType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.util.Intervals;
import net.imglib2.util.ValueTriple;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
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
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority;
import org.janelia.saalfeldlab.paintera.meshes.PainteraTriangleMesh;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.janelia.saalfeldlab.paintera.meshes.cache.GenericMeshCacheLoader;
import org.janelia.saalfeldlab.paintera.meshes.managed.GetBlockListFor;
import org.janelia.saalfeldlab.paintera.meshes.managed.GetMeshFor;
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithSingleMesh;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.util.Colors;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.janelia.saalfeldlab.util.TmpVolatileHelpers;
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tmp.net.imglib2.converter.read.ConvertedRandomAccessibleInterval;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class IntersectingSourceState<K1 extends MeshCacheKey, K2 extends MeshCacheKey>
		extends MinimalSourceState<UnsignedByteType, VolatileUnsignedByteType, DataSource<UnsignedByteType, VolatileUnsignedByteType>, ARGBColorConverter<VolatileUnsignedByteType>>
		implements IntersectableSourceState<UnsignedByteType, VolatileUnsignedByteType, IntersectingSourceStateMeshCacheKey<K1, K2>> {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final boolean DEFAULT_MESHES_ENABLED = true;

  private final ObjectProperty<K1> firstSourceMeshCacheKeyProperty = new SimpleObjectProperty<>(null);

  private final ObjectProperty<K2> secondSourceMeshCacheKeyProperty = new SimpleObjectProperty<>(null);

  private final ObjectBinding<IntersectingSourceStateMeshCacheKey<K1, K2>> intersectionMeshCacheKeyBinding = Bindings.createObjectBinding(
		  () -> {
			final var firstSourceMeschCacheKey = firstSourceMeshCacheKeyProperty.getValue();
			final var secondSourceMeshCacheKey = secondSourceMeshCacheKeyProperty.getValue();
			return new IntersectingSourceStateMeshCacheKey<>(firstSourceMeschCacheKey, secondSourceMeshCacheKey);
		  },
		  // dependsOn
		  firstSourceMeshCacheKeyProperty, secondSourceMeshCacheKeyProperty);

  private final ObjectBinding<Color> colorProperty = Bindings.createObjectBinding(() -> Colors.toColor(converter().getColor()), converter().colorProperty());

  private final MeshManagerWithSingleMesh<IntersectingSourceStateMeshCacheKey<K1, K2>> meshManager;

  /* TODO: Is there a better way to do this so we don't have an internal predicate source intermediate?
   *   	Would be better if we could just use BoolType for `getDataSource` but BoolType is not a `NativeType` */
  private final PredicateDataSource<UnsignedByteType, VolatileUnsignedByteType, Predicate<UnsignedByteType>> predicateDataSource = new PredicateDataSource<>(
		  getDataSource(),
		  b -> b.get() > 0,
		  "internal_predicateIntersectionSource"
  );

  private final GetUnionBlockListFor<K1, K2> getGetUnionBlockListFor;

  public static final ExecutorService INTERSECTION_FILL_SERVICE = Executors.newCachedThreadPool(new NamedThreadFactory("intersection-floodfill-%s", true));

  public IntersectingSourceState(
		  final IntersectableSourceState<?, ?, K1> source1,
		  final IntersectableSourceState<?, ?, K2> source2,
		  final Composite<ARGBType, ARGBType> composite,
		  final String name,
		  final int priority,
		  final PainteraBaseView viewer) {

	this(
			source1,
			source2,
			makeIntersect(source1.getIntersectableMask(), source2.getIntersectableMask(), viewer.getQueue(), priority, name),
			composite,
			name,
			viewer.viewer3D().meshesGroup(),
			viewer.viewer3D().viewFrustumProperty(),
			viewer.viewer3D().eyeToWorldTransformProperty(),
			viewer.getMeshManagerExecutorService(),
			viewer.getMeshWorkerExecutorService(),
			source1.getMeshCacheKeyBinding(),
			source2.getMeshCacheKeyBinding(),
			source1.getGetBlockListFor(),
			source2.getGetBlockListFor());
  }

  public IntersectingSourceState(
		  final IntersectableSourceState<?, ?, K1> source1,
		  final IntersectableSourceState<?, ?, K2> source2,
		  final Composite<ARGBType, ARGBType> composite,
		  final String name,
		  final SharedQueue queue,
		  final int priority,
		  final Group meshesGroup,
		  final ObjectProperty<ViewFrustum> viewFrustumProperty,
		  final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
		  final ExecutorService manager,
		  final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> workers) {

	this(
			source1,
			source2,
			makeIntersect(source1.getIntersectableMask(), source2.getIntersectableMask(), queue, priority, name),
			composite,
			name,
			meshesGroup,
			viewFrustumProperty,
			eyeToWorldTransformProperty,
			manager,
			workers,
			source1.getMeshCacheKeyBinding(),
			source2.getMeshCacheKeyBinding(),
			source1.getGetBlockListFor(),
			source2.getGetBlockListFor()
	);

  }

  private IntersectingSourceState(
		  final SourceState<?, ?> firstSource,
		  final SourceState<?, ?> secondSource,
		  final DataSource<UnsignedByteType, VolatileUnsignedByteType> intersectSource,
		  final Composite<ARGBType, ARGBType> composite,
		  final String name,
		  final Group meshesGroup, //TODO do we need this? It's currently unused.
		  final ObjectProperty<ViewFrustum> viewFrustumProperty,
		  final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
		  final ExecutorService manager,
		  final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> workers,
		  final ObservableValue<K1> firstSourceChangeListener,
		  final ObservableValue<K2> secondSourceChangeListener,
		  final GetBlockListFor<K1> firstGetBlockListFor,
		  final GetBlockListFor<K2> secondGetBlockListFor
  ) {
	// TODO use better converter
	super(
			intersectSource,
			new ARGBColorConverter.Imp0<>(0, 1),
			composite,
			name,
			// dependsOn:
			firstSource,
			secondSource);

	this.firstSourceMeshCacheKeyProperty.bind(firstSourceChangeListener);
	this.secondSourceMeshCacheKeyProperty.bind(secondSourceChangeListener);
	this.getGetUnionBlockListFor = getGetUnionBlockListFor(firstGetBlockListFor, secondGetBlockListFor);
	this.meshManager = createMeshManager(viewFrustumProperty, eyeToWorldTransformProperty, manager, workers, getGetUnionBlockListFor);
	this.intersectionMeshCacheKeyBinding.addListener((obs, oldv, newv) -> {
	  if (newv != null && oldv != newv)
		refreshMeshes();
	});

	this.meshManager.getRendererSettings().getMeshesEnabledProperty().addListener((obs, oldv, newv) -> {
	  if (newv) {
		refreshMeshes();
	  }
	});
  }

  private MeshManagerWithSingleMesh<IntersectingSourceStateMeshCacheKey<K1, K2>> createMeshManager(
		  final ObjectProperty<ViewFrustum> viewFrustumProperty,
		  final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
		  final ExecutorService manager,
		  final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> workers,
		  final GetUnionBlockListFor<K1, K2> getUnionBlockListFor) {

	CacheLoader<ShapeKey<IntersectingSourceStateMeshCacheKey<K1, K2>>, PainteraTriangleMesh> loader = getCacheLoader();
	GetMeshFor.FromCache<IntersectingSourceStateMeshCacheKey<K1, K2>> getMeshFor = GetMeshFor.FromCache.fromLoader(loader);

	return new MeshManagerWithSingleMesh<>(
			getDataSource(),
			getUnionBlockListFor,
			new WrappedGetMeshFromMeshCacheKey<>(getMeshFor),
			viewFrustumProperty,
			eyeToWorldTransformProperty,
			manager,
			workers,
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

  @Override public DataSource<BoolType, Volatile<BoolType>> getIntersectableMask() {

	return predicateDataSource;
  }

  @Override public ObjectBinding<IntersectingSourceStateMeshCacheKey<K1, K2>> getMeshCacheKeyBinding() {

	return this.intersectionMeshCacheKeyBinding;
  }

  @Override public GetBlockListFor<IntersectingSourceStateMeshCacheKey<K1, K2>> getGetBlockListFor() {

	return this.getGetUnionBlockListFor;
  }

  private static class GetUnionBlockListFor<K1 extends MeshCacheKey, K2 extends MeshCacheKey> implements GetBlockListFor<IntersectingSourceStateMeshCacheKey<K1, K2>> {

	final GetBlockListFor<K1> first;
	final GetBlockListFor<K2> second;

	private GetUnionBlockListFor(GetBlockListFor<K1> first, GetBlockListFor<K2> second) {

	  this.first = first;
	  this.second = second;
	}

	@Override
	public Interval[] getBlocksFor(int level, IntersectingSourceStateMeshCacheKey<K1, K2> key) {

	  final var firstKey = key.getFirstKey();
	  final var secondKey = key.getSecondKey();
	  final var firstBlocks = Arrays.asList(first.getBlocksFor(level, firstKey));
	  final var secondBlocks = Arrays.asList(second.getBlocksFor(level, secondKey));
	  Interval[] intervals = Stream.concat(firstBlocks.stream(), secondBlocks.stream()).distinct().toArray(Interval[]::new);
	  return intervals;
	}
  }

  @Override public void onAdd(PainteraBaseView paintera) {

	paintera.viewer3D().meshesGroup().getChildren().add(meshManager.getMeshesGroup());

	meshManager.getViewerEnabledProperty().bind(paintera.viewer3D().meshesEnabledProperty());
	meshManager.getRendererSettings().getShowBlockBoundariesProperty().bind(paintera.viewer3D().showBlockBoundariesProperty());
	meshManager.getRendererSettings().getBlockSizeProperty().bind(paintera.viewer3D().rendererBlockSizeProperty());
	meshManager.getRendererSettings().getNumElementsPerFrameProperty().bind(paintera.viewer3D().numElementsPerFrameProperty());
	meshManager.getRendererSettings().getFrameDelayMsecProperty().bind(paintera.viewer3D().frameDelayMsecProperty());
	meshManager.getRendererSettings().getSceneUpdateDelayMsecProperty().bind(paintera.viewer3D().sceneUpdateDelayMsecProperty());
	meshManager.getColorProperty().bind(colorProperty);
	colorProperty.addListener((obs, old, newv) -> paintera.orthogonalViews().requestRepaint());
	refreshMeshes();
  }

  @Override public void onRemoval(SourceInfo paintera) {

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

	getDataSource().invalidateAll();
	final var getMeshFor = getMeshManager().getGetMeshFor();
	if (getMeshFor instanceof Invalidate<?>) {
	  ((Invalidate<?>)getMeshFor).invalidateAll();
	}
	this.meshManager.createMeshFor(intersectionMeshCacheKeyBinding.get());
  }

  public MeshManagerWithSingleMesh<IntersectingSourceStateMeshCacheKey<K1, K2>> getMeshManager() {

	return this.meshManager;
  }

  /**
   * Intersect two sources to create a new source as the result of the intersection.
   * <p>
   * Note: The resultant data source is not a strict intersection. Instead, it takes a seed point from the strict intersection
   * of the two sourcecs, and runs a flood fill algorithm with respect to the second source.
   *
   * @param dataSource2Intersect First source to intersect against
   * @param dataSource1FillInto  Second source to intersect against. Used for intersection Fill
   * @param queue
   * @param priority
   * @param name                 of the resultant source
   * @param <B>                  data type of the input sources
   * @return
   */
  private static <B extends BooleanType<B>>
  DataSource<UnsignedByteType, VolatileUnsignedByteType> makeIntersect(
		  final DataSource<B, Volatile<B>> dataSource1FillInto,
		  final DataSource<B, Volatile<B>> dataSource2Intersect,
		  final SharedQueue queue,
		  final int priority,
		  final String name) {

	LOG.debug("Number of mipmap labels: source 1={} source 2={}",
			dataSource1FillInto.getNumMipmapLevels(),
			dataSource2Intersect.getNumMipmapLevels()
	);
	if (dataSource1FillInto.getNumMipmapLevels() != dataSource2Intersect.getNumMipmapLevels()) {
	  throw new RuntimeException("Incompatible sources (num mip map levels )");
	}

	final AffineTransform3D[] transforms = new AffineTransform3D[dataSource1FillInto.getNumMipmapLevels()];

	final RandomAccessibleInterval<UnsignedByteType>[] data = new RandomAccessibleInterval[transforms.length];
	final RandomAccessibleInterval<VolatileUnsignedByteType>[] vdata = new RandomAccessibleInterval[transforms.length];
	final Invalidate<Long>[] invalidate = new Invalidate[transforms.length];
	final Invalidate<Long>[] vinvalidate = new Invalidate[transforms.length];

	for (int level = 0; level < dataSource1FillInto.getNumMipmapLevels(); ++level) {
	  final AffineTransform3D tf1 = dataSource1FillInto.getSourceTransformCopy(0, level);
	  final AffineTransform3D tf2 = dataSource2Intersect.getSourceTransformCopy(0, level);
	  if (!Arrays.equals(tf1.getRowPackedCopy(), tf2.getRowPackedCopy()))
		throw new RuntimeException("Incompatible sources ( transforms )");

	  final var fillRAI = dataSource1FillInto.getDataSource(0, level);
	  final var seedRAI = dataSource2Intersect.getDataSource(0, level);

	  final int[] cellDimensions;
	  if (seedRAI instanceof ConvertedRandomAccessibleInterval && ((ConvertedRandomAccessibleInterval<?, ?>)seedRAI).getSource() instanceof CachedCellImg) {
		var crai2 = (ConvertedRandomAccessibleInterval<?, ?>)seedRAI;
		var cachedCellImg = (CachedCellImg<?, ?>)crai2.getSource();
		var grid = cachedCellImg.getCellGrid();
		cellDimensions = new int[grid.numDimensions()];
		grid.cellDimensions(cellDimensions);
	  } else {
		cellDimensions = Arrays.stream(Intervals.dimensionsAsLongArray(fillRAI)).mapToInt(l -> (int)l / 10).toArray();
	  }

	  LOG.debug("Making intersect for level={} with block size={}", level, cellDimensions);

	  final var fillExtendedRAI = Views.extendZero(fillRAI);
	  final BooleanProperty intersectionPointsUpdated = new SimpleBooleanProperty(false);
	  final HashSet<Point> intersectionPoints = new HashSet<>();

	  final var img = Lazy.generate(fillRAI, cellDimensions, new UnsignedByteType(), AccessFlags.setOf(AccessFlags.VOLATILE), cell -> {
		LOG.debug("Updating Intersection Points");
		final var newIntersectionPoints = detectIntersectionPoints(Views.extendZero(seedRAI), fillExtendedRAI, cell);
		synchronized (intersectionPoints) {
		  intersectionPoints.addAll(newIntersectionPoints);
		  intersectionPointsUpdated.set(true);
		}
	  });

	  intersectionPointsUpdated.addListener((obs, oldv, newv) -> {
		if (newv) {
		  final Point[] seedPointsCopy;
		  synchronized (intersectionPoints) {
			seedPointsCopy = intersectionPoints.toArray(Point[]::new);
			intersectionPoints.clear();
			intersectionPointsUpdated.set(false);
		  }
		  INTERSECTION_FILL_SERVICE.submit(() -> fillFromSeedPoints(fillExtendedRAI, img, seedPointsCopy));
		}
	  });

	  // TODO cannot use VolatileViews because we need access to cache
	  final TmpVolatileHelpers.RaiWithInvalidate<VolatileUnsignedByteType> vimg = TmpVolatileHelpers.createVolatileCachedCellImgWithInvalidate(
			  (CachedCellImg<UnsignedByteType, VolatileByteArray>)img,
			  queue,
			  new CacheHints(LoadingStrategy.VOLATILE, priority, true));

	  data[level] = img;
	  vdata[level] = vimg.getRai();
	  invalidate[level] = img.getCache();
	  vinvalidate[level] = vimg.getInvalidate();
	  transforms[level] = tf1;
	}

	return new RandomAccessibleIntervalDataSource<>(
			new ValueTriple<>(data, vdata, transforms),
			new InvalidateDelegates<>(Arrays.asList(new InvalidateDelegates<>(invalidate), new InvalidateDelegates<>(vinvalidate))),
			Interpolations.nearestNeighbor(),
			Interpolations.nearestNeighbor(),
			name);
  }

  private static <B extends BooleanType<B>> void fillFromSeedPoints(ExtendedRandomAccessibleInterval<B, RandomAccessibleInterval<B>> fillExtendedRAI, CachedCellImg<UnsignedByteType, ?> img, Point[] seedPointsCopy) {

	LOG.debug("Filling from seed points");
	for (Point seed : seedPointsCopy) {
	  if (img.getAt(seed).get() == 0) {
		LOG.trace("Intersection Floodfill at seed:  {}", seed);
		FloodFill.fill(
				fillExtendedRAI,
				img,
				seed,
				new UnsignedByteType(1),
				new DiamondShape(1),
				(BiPredicate<B, UnsignedByteType>)(source, target) -> source.get() && target.get() == 0
		);
	  }
	}
  }

  private static <B extends BooleanType<B>> HashSet<Point> detectIntersectionPoints(RandomAccessible<B> seedRAI, RandomAccessible<B> fillExtendedRAI, RandomAccessibleInterval<UnsignedByteType> cell) {

	LOG.trace(
			"Detecting seed point in cell {} {}",
			Intervals.minAsLongArray(cell),
			Intervals.maxAsLongArray(cell)
	);
	final IntervalView<B> intersectInterval = Views.interval(seedRAI, cell);
	final IntervalView<B> fillInterval = Views.interval(fillExtendedRAI, cell);
	final Cursor<B> intersectCursor = Views.flatIterable(intersectInterval).cursor();
	final Cursor<B> fillCursor = Views.flatIterable(fillInterval).cursor();
	final Cursor<UnsignedByteType> targetCursor = Views.flatIterable(cell).localizingCursor();

	final var intersectionSet = new HashSet<Point>();
	while (targetCursor.hasNext()) {
	  final UnsignedByteType targetType = targetCursor.next();
	  final B intersectType = intersectCursor.next();
	  final B fillType = fillCursor.next();
	  if (targetType.get() == 0) {
		if (fillType.get() && intersectType.get()) {
		  intersectionSet.add(targetCursor.positionAsPoint());
		}
	  }
	}
	return intersectionSet;
  }

  @Override
  public Node preferencePaneNode() {

	return new IntersectingSourceStatePreferencePaneNode(this).getNode();
  }
}
