package org.janelia.saalfeldlab.paintera.state;

import bdv.util.volatiles.SharedQueue;
import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ObjectProperty;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.Cache;
import net.imglib2.cache.Invalidate;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.Converter;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.BooleanType;
import net.imglib2.type.Type;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.LabelMultisetType.Entry;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValueTriple;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.cache.InvalidateDelegates;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.selection.FragmentsInSelectedSegments;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.Interpolations;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.meshes.*;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManager;
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.util.Colors;
import org.janelia.saalfeldlab.util.TmpVolatileHelpers;
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.IntStream;

public class IntersectingSourceState
		extends
		MinimalSourceState<UnsignedByteType, VolatileUnsignedByteType, DataSource<UnsignedByteType,
				VolatileUnsignedByteType>, ARGBColorConverter<VolatileUnsignedByteType>>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final MeshManagerSimple<TLongHashSet, TLongHashSet> meshManager;

	public <D extends IntegerType<D>, T extends Type<T>, B extends BooleanType<B>> IntersectingSourceState(
			final ThresholdingSourceState<?, ?> thresholded,
			final ConnectomicsLabelState<D, T> labels,
			final Composite<ARGBType, ARGBType> composite,
			final String name,
			final SharedQueue queue,
			final int priority,
			final Group meshesGroup,
			final ObjectProperty<ViewFrustum> viewFrustumProperty,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
			final ExecutorService manager,
			final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> workers) {
		// TODO use better converter
		super(
				makeIntersect(thresholded, labels, queue, priority, name),
				new ARGBColorConverter.Imp0<>(0, 1),
				composite,
				name,
				// dependsOn:
				thresholded,
				labels);
		final DataSource<UnsignedByteType, VolatileUnsignedByteType> source = getDataSource();

		this.axisOrderProperty().bindBidirectional(thresholded.axisOrderProperty());
		this.axisOrderProperty().bindBidirectional(labels.axisOrderProperty());

		final MeshManager<Long, TLongHashSet> meshManager = labels.getMeshManager();

		final BiFunction<TLongHashSet, Double, Converter<UnsignedByteType, BoolType>> getMaskGenerator = (l, minLabelRatio) -> (s, t) -> t.set(s.get() > 0);
		final InterruptibleFunctionAndCache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>[] meshCaches = CacheUtils.segmentMeshCacheLoaders(
				source,
				IntStream.range(0, source.getNumMipmapLevels()).mapToObj(i -> getMaskGenerator).toArray(BiFunction[]::new),
				loader -> new SoftRefLoaderCache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>().withLoader(loader));

		final FragmentsInSelectedSegments fragmentsInSelectedSegments = new FragmentsInSelectedSegments(labels.getSelectedSegments());

		this.meshManager = new MeshManagerSimple<>(
				source,
				meshManager.blockListCache(),
				// BlocksForLabelDelegate.delegate(
				// meshManager.blockListCache(), key -> Arrays.stream(
				// fragmentsInSelectedSegments.getFragments() ).mapToObj( l -> l
				// ).toArray( Long[]::new ) ),
				meshCaches,
				meshesGroup,
				viewFrustumProperty,
				eyeToWorldTransformProperty,
				new MeshSettings(source.getNumMipmapLevels()),
				manager,
				workers,
				TLongHashSet::toArray,
				hs -> hs
		);
		final ObjectBinding<Color> colorProperty = Bindings.createObjectBinding(
				() -> Colors.toColor(this.converter().getColor()),
				this.converter().colorProperty()
		);
		this.meshManager.colorProperty().bind(colorProperty);
		this.meshManager.levelOfDetailProperty().bind(meshManager.levelOfDetailProperty());
		this.meshManager.coarsestScaleLevelProperty().bind(meshManager.coarsestScaleLevelProperty());
		this.meshManager.finestScaleLevelProperty().bind(meshManager.finestScaleLevelProperty());
		this.meshManager.areMeshesEnabledProperty().bind(meshManager.areMeshesEnabledProperty());
		this.meshManager.showBlockBoundariesProperty().bind(meshManager.showBlockBoundariesProperty());
		this.meshManager.meshSimplificationIterationsProperty().bind(meshManager.meshSimplificationIterationsProperty());
		this.meshManager.smoothingIterationsProperty().bind(meshManager.smoothingIterationsProperty());
		this.meshManager.smoothingLambdaProperty().bind(meshManager.smoothingLambdaProperty());
		this.meshManager.minLabelRatioProperty().bind(meshManager.minLabelRatioProperty());
		this.meshManager.rendererBlockSizeProperty().bind(meshManager.rendererBlockSizeProperty());

		thresholded.getThreshold().minValue().addListener((obs, oldv, newv) -> {
			Arrays.stream(meshCaches).forEach(Invalidate::invalidateAll);
			update(source, fragmentsInSelectedSegments); });
		thresholded.getThreshold().maxValue().addListener((obs, oldv, newv) -> {
			Arrays.stream(meshCaches).forEach(Invalidate::invalidateAll);
			update(source, fragmentsInSelectedSegments); });

		//		selectedIds.addListener( obs -> update( source, fragmentsInSelectedSegments ) );
		//		assignment.addListener( obs -> update( source, fragmentsInSelectedSegments ) );
		fragmentsInSelectedSegments.addListener(obs -> update(source, fragmentsInSelectedSegments));
		this.meshManager.update();
	}

	@Deprecated
	public <D extends IntegerType<D>, T extends Type<T>, B extends BooleanType<B>> IntersectingSourceState(
			final ThresholdingSourceState<?, ?> thresholded,
			final LabelSourceState<D, T> labels,
			final Composite<ARGBType, ARGBType> composite,
			final String name,
			final SharedQueue queue,
			final int priority,
			final Group meshesGroup,
			final ObjectProperty<ViewFrustum> viewFrustumProperty,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
			final ExecutorService manager,
			final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> workers) {
		// TODO use better converter
		super(
				makeIntersect(thresholded, labels, queue, priority, name),
				new ARGBColorConverter.Imp0<>(0, 1),
				composite,
				name,
				// dependsOn:
				thresholded,
				labels);
		final DataSource<UnsignedByteType, VolatileUnsignedByteType> source = getDataSource();

		this.axisOrderProperty().bindBidirectional(thresholded.axisOrderProperty());
		this.axisOrderProperty().bindBidirectional(labels.axisOrderProperty());

		final MeshManager<Long, TLongHashSet> meshManager = labels.meshManager();
		final SelectedIds selectedIds = labels.selectedIds();

		final BiFunction<TLongHashSet, Double, Converter<UnsignedByteType, BoolType>> getMaskGenerator = (l, minLabelRatio) -> (s, t) -> t.set(s.get() > 0);
		final InterruptibleFunctionAndCache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>[] meshCaches = CacheUtils.segmentMeshCacheLoaders(
				source,
				IntStream.range(0, source.getNumMipmapLevels()).mapToObj(i -> getMaskGenerator).toArray(BiFunction[]::new),
				loader -> new SoftRefLoaderCache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>().withLoader(loader));

		final FragmentSegmentAssignmentState assignment                  = labels.assignment();
		final SelectedSegments               selectedSegments            = new SelectedSegments(
				selectedIds,
				assignment
		);
		final FragmentsInSelectedSegments    fragmentsInSelectedSegments = new FragmentsInSelectedSegments(
				selectedSegments
		);

		this.meshManager = new MeshManagerSimple<>(
				source,
				meshManager.blockListCache(),
				// BlocksForLabelDelegate.delegate(
				// meshManager.blockListCache(), key -> Arrays.stream(
				// fragmentsInSelectedSegments.getFragments() ).mapToObj( l -> l
				// ).toArray( Long[]::new ) ),
				meshCaches,
				meshesGroup,
				viewFrustumProperty,
				eyeToWorldTransformProperty,
				new MeshSettings(source.getNumMipmapLevels()),
				manager,
				workers,
				TLongHashSet::toArray,
				hs -> hs
			);
		final ObjectBinding<Color> colorProperty = Bindings.createObjectBinding(
				() -> Colors.toColor(this.converter().getColor()),
				this.converter().colorProperty()
			);
		this.meshManager.colorProperty().bind(colorProperty);
		this.meshManager.levelOfDetailProperty().bind(meshManager.levelOfDetailProperty());
		this.meshManager.coarsestScaleLevelProperty().bind(meshManager.coarsestScaleLevelProperty());
		this.meshManager.finestScaleLevelProperty().bind(meshManager.finestScaleLevelProperty());
		this.meshManager.areMeshesEnabledProperty().bind(meshManager.areMeshesEnabledProperty());
		this.meshManager.showBlockBoundariesProperty().bind(meshManager.showBlockBoundariesProperty());
		this.meshManager.meshSimplificationIterationsProperty().bind(meshManager.meshSimplificationIterationsProperty());
		this.meshManager.smoothingIterationsProperty().bind(meshManager.smoothingIterationsProperty());
		this.meshManager.smoothingLambdaProperty().bind(meshManager.smoothingLambdaProperty());
		this.meshManager.minLabelRatioProperty().bind(meshManager.minLabelRatioProperty());
		this.meshManager.rendererBlockSizeProperty().bind(meshManager.rendererBlockSizeProperty());

		thresholded.getThreshold().minValue().addListener((obs, oldv, newv) -> {
			Arrays.stream(meshCaches).forEach(Invalidate::invalidateAll);
			update(source, fragmentsInSelectedSegments);
		});
		thresholded.getThreshold().maxValue().addListener((obs, oldv, newv) -> {
			Arrays.stream(meshCaches).forEach(Invalidate::invalidateAll);
			update(source, fragmentsInSelectedSegments);
		});

		//		selectedIds.addListener( obs -> update( source, fragmentsInSelectedSegments ) );
		//		assignment.addListener( obs -> update( source, fragmentsInSelectedSegments ) );
		fragmentsInSelectedSegments.addListener(obs -> update(source, fragmentsInSelectedSegments));
		this.meshManager.update();
	}

	private void update(
			final DataSource<?, ?> source,
			final FragmentsInSelectedSegments fragmentsInSelectedSegments)
	{
		source.invalidateAll();
		this.meshManager.removeAllMeshes();
		if (Optional.ofNullable(fragmentsInSelectedSegments.getFragments()).map(sel -> sel.length).orElse(0) > 0)
			this.meshManager.addMesh(new TLongHashSet(fragmentsInSelectedSegments.getFragments()));
		this.meshManager.update();
	}

	public MeshManager<TLongHashSet, TLongHashSet> meshManager()
	{
		return this.meshManager;
	}

	private static <D extends IntegerType<D>, T extends Type<T>, B extends BooleanType<B>>
	DataSource<UnsignedByteType, VolatileUnsignedByteType> makeIntersect(
			final SourceState<B, Volatile<B>> thresholded,
			final ConnectomicsLabelState<D, T> labels,
			final SharedQueue queue,
			final int priority,
			final String name) {
		LOG.debug(
				"Number of mipmap labels: thresholded={} labels={}",
				thresholded.getDataSource().getNumMipmapLevels(),
				labels.getDataSource().getNumMipmapLevels()
		);
		if (thresholded.getDataSource().getNumMipmapLevels() != labels.getDataSource().getNumMipmapLevels()) {
			throw new RuntimeException("Incompatible sources (num mip map levels )");
		}

		final AffineTransform3D[] transforms = new AffineTransform3D[thresholded.getDataSource().getNumMipmapLevels()];
		final RandomAccessibleInterval<UnsignedByteType>[] data = new RandomAccessibleInterval[transforms.length];
		final RandomAccessibleInterval<VolatileUnsignedByteType>[] vdata = new RandomAccessibleInterval[transforms.length];
		final Invalidate<Long>[] invalidate = new Invalidate[transforms.length];
		final Invalidate<Long>[] vinvalidate = new Invalidate[transforms.length];

		final FragmentsInSelectedSegments fragmentsInSelectedSegments = new FragmentsInSelectedSegments(labels.getSelectedSegments());

		for (int level = 0; level < thresholded.getDataSource().getNumMipmapLevels(); ++level) {
			final DataSource<D, T> labelsSource = labels.getDataSource() instanceof MaskedSource<?, ?>
					? ((MaskedSource<D, T>) labels.getDataSource()).underlyingSource()
					: labels.getDataSource();
			final AffineTransform3D tf1 = new AffineTransform3D();
			final AffineTransform3D tf2 = new AffineTransform3D();
			thresholded.getDataSource().getSourceTransform(0, level, tf1);
			labelsSource.getSourceTransform(0, level, tf2);
			if (!Arrays.equals(tf1.getRowPackedCopy(), tf2.getRowPackedCopy()))
				throw new RuntimeException("Incompatible sources ( transforms )");

			final RandomAccessibleInterval<B> thresh = thresholded.getDataSource().getDataSource(0, level);
			final RandomAccessibleInterval<D> label = labelsSource.getDataSource(0, level);

			final CellGrid grid = label instanceof AbstractCellImg<?, ?, ?, ?>
					? ((AbstractCellImg<?, ?, ?, ?>) label).getCellGrid()
					: new CellGrid(
					Intervals.dimensionsAsLongArray(label),
					Arrays.stream(Intervals.dimensionsAsLongArray(label)).mapToInt(l -> (int) l)
							.toArray());

			final B extension = Util.getTypeFromInterval(thresh);
			extension.set(false);
			final LabelIntersectionCellLoader<D, B> loader = new LabelIntersectionCellLoader<>(
					label,
					Views.extendValue(thresh, extension),
					checkForType(labelsSource.getDataType(), fragmentsInSelectedSegments),
					BooleanType::get,
					extension::copy
			);

			LOG.debug("Making intersect for level={} with grid={}", level, grid);


			final LoadedCellCacheLoader<UnsignedByteType, VolatileByteArray> cacheLoader = LoadedCellCacheLoader.get(grid, loader, new UnsignedByteType(), AccessFlags.setOf(AccessFlags.VOLATILE));
			final Cache<Long, Cell<VolatileByteArray>> cache = new SoftRefLoaderCache<Long, Cell<VolatileByteArray>>().withLoader(cacheLoader);
			final CachedCellImg<UnsignedByteType, VolatileByteArray> img = new CachedCellImg<>(grid, new UnsignedByteType(), cache, new VolatileByteArray(1, true));
			// TODO cannot use VolatileViews because we need access to cache
			final TmpVolatileHelpers.RaiWithInvalidate<VolatileUnsignedByteType> vimg = TmpVolatileHelpers.createVolatileCachedCellImgWithInvalidate(
					img,
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

	@Deprecated
	private static <D extends IntegerType<D>, T extends Type<T>, B extends BooleanType<B>>
	DataSource<UnsignedByteType, VolatileUnsignedByteType> makeIntersect(
			final SourceState<B, Volatile<B>> thresholded,
			final LabelSourceState<D, T> labels,
			final SharedQueue queue,
			final int priority,
			final String name) {
		LOG.debug(
				"Number of mipmap labels: thresholded={} labels={}",
				thresholded.getDataSource().getNumMipmapLevels(),
				labels.getDataSource().getNumMipmapLevels()
		         );
		if (thresholded.getDataSource().getNumMipmapLevels() != labels.getDataSource().getNumMipmapLevels())
		{
			throw new RuntimeException("Incompatible sources (num mip map levels )");
		}

		final AffineTransform3D[]                                  transforms = new AffineTransform3D[thresholded.getDataSource().getNumMipmapLevels()];
		final RandomAccessibleInterval<UnsignedByteType>[]         data       = new RandomAccessibleInterval[transforms.length];
		final RandomAccessibleInterval<VolatileUnsignedByteType>[] vdata      = new RandomAccessibleInterval[transforms.length];
		final Invalidate<Long>[] invalidate                                   = new Invalidate[transforms.length];
		final Invalidate<Long>[] vinvalidate                                  = new Invalidate[transforms.length];

		final SelectedIds                    selectedIds                 = labels.selectedIds();
		final FragmentSegmentAssignmentState assignment                  = labels.assignment();
		final SelectedSegments               selectedSegments            = new SelectedSegments(
				selectedIds,
				assignment
		);
		final FragmentsInSelectedSegments    fragmentsInSelectedSegments = new FragmentsInSelectedSegments(
				selectedSegments
		);

		for (int level = 0; level < thresholded.getDataSource().getNumMipmapLevels(); ++level)
		{
			final DataSource<D, T> labelsSource = labels.getDataSource() instanceof MaskedSource<?, ?>
			                                      ? ((MaskedSource<D, T>) labels.getDataSource()).underlyingSource()
			                                      : labels.getDataSource();
			final AffineTransform3D tf1 = new AffineTransform3D();
			final AffineTransform3D tf2 = new AffineTransform3D();
			thresholded.getDataSource().getSourceTransform(0, level, tf1);
			labelsSource.getSourceTransform(0, level, tf2);
			if (!Arrays.equals(tf1.getRowPackedCopy(), tf2.getRowPackedCopy()))
			{
				throw new RuntimeException("Incompatible sources ( transforms )");
			}

			final RandomAccessibleInterval<B> thresh = thresholded.getDataSource().getDataSource(0, level);
			final RandomAccessibleInterval<D> label  = labelsSource.getDataSource(0, level);

			final CellGrid grid = label instanceof AbstractCellImg<?, ?, ?, ?>
			                      ? ((AbstractCellImg<?, ?, ?, ?>) label).getCellGrid()
			                      : new CellGrid(
					                      Intervals.dimensionsAsLongArray(label),
					                      Arrays.stream(Intervals.dimensionsAsLongArray(label)).mapToInt(l -> (int) l)
							                      .toArray()
			                      );

			final B extension = Util.getTypeFromInterval(thresh);
			extension.set(false);
			final LabelIntersectionCellLoader<D, B> loader = new LabelIntersectionCellLoader<>(
					label,
					Views.extendValue(thresh, extension),
					checkForType(labelsSource.getDataType(), fragmentsInSelectedSegments),
					BooleanType::get,
					extension::copy
			);

			LOG.debug("Making intersect for level={} with grid={}", level, grid);


			final LoadedCellCacheLoader<UnsignedByteType, VolatileByteArray> cacheLoader = LoadedCellCacheLoader.get(grid, loader, new UnsignedByteType(), AccessFlags.setOf(AccessFlags.VOLATILE));
			final Cache<Long, Cell<VolatileByteArray>> cache = new SoftRefLoaderCache<Long, Cell<VolatileByteArray>>().withLoader(cacheLoader);
			final CachedCellImg<UnsignedByteType, VolatileByteArray> img = new CachedCellImg<>(grid, new UnsignedByteType(), cache, new VolatileByteArray(1, true));
			// TODO cannot use VolatileViews because we need access to cache
			final TmpVolatileHelpers.RaiWithInvalidate<VolatileUnsignedByteType> vimg = TmpVolatileHelpers.createVolatileCachedCellImgWithInvalidate(
					img,
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
				name
		);
	}

	private static <T> Predicate<T> checkForType(final T t, final FragmentsInSelectedSegments fragmentsInSelectedSegments)
	{
		if (t instanceof LabelMultisetType)
		{
			return (Predicate<T>) checkForLabelMultisetType(fragmentsInSelectedSegments);
		}

		return null;
	}

	private static final Predicate<LabelMultisetType> checkForLabelMultisetType(final FragmentsInSelectedSegments fragmentsInSelectedSegments)
	{
		return lmt -> {
			for (final Entry<Label> entry : lmt.entrySet())
			{
				if (fragmentsInSelectedSegments.contains(entry.getElement().id())) { return true; }
			}
			return false;
		};
	}

}
