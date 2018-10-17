package org.janelia.saalfeldlab.paintera.state;

import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.UncheckedCache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.volatiles.VolatileCache;
import net.imglib2.converter.ARGBColorConverter;
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
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Triple;
import net.imglib2.util.Util;
import net.imglib2.util.ValueTriple;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.cache.Invalidate;
import org.janelia.saalfeldlab.paintera.cache.InvalidateAll;
import org.janelia.saalfeldlab.paintera.cache.global.GlobalCache;
import org.janelia.saalfeldlab.paintera.cache.global.InvalidAccessException;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentsInSelectedSegments;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.Interpolations;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrderNotSupported;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunctionAndCache;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerSimple;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.util.Colors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tmp.bdv.img.cache.VolatileCachedCellImg;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class IntersectingSourceState
		extends
		MinimalSourceState<UnsignedByteType, VolatileUnsignedByteType, DataSource<UnsignedByteType,
				VolatileUnsignedByteType>, ARGBColorConverter<VolatileUnsignedByteType>>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final MeshManagerSimple<TLongHashSet, TLongHashSet> meshManager;

	public <D extends IntegerType<D>, T extends Type<T>, B extends BooleanType<B>> IntersectingSourceState(
			final ThresholdingSourceState<?, ?> thresholded,
			final LabelSourceState<D, T> labels,
			final Composite<ARGBType, ARGBType> composite,
			final String name,
			final GlobalCache globalCache,
			final int priority,
			final Group meshesGroup,
			final ExecutorService manager,
			final ExecutorService workers) throws InvalidAccessException {
		// TODO use better converter
		super(
				makeIntersect(thresholded, labels, globalCache, priority, name),
				new ARGBColorConverter.Imp0<>(0, 1),
				composite,
				name,
				// dependsOn:
				thresholded,
				labels
		     );
		final DataSource<UnsignedByteType, VolatileUnsignedByteType> source = getDataSource();

		this.axisOrderProperty().bindBidirectional(thresholded.axisOrderProperty());
		this.axisOrderProperty().bindBidirectional(labels.axisOrderProperty());

		final MeshManager<Long, TLongHashSet> meshManager = labels.meshManager();

		final SelectedIds selectedIds = labels.selectedIds();
		final Pair<InterruptibleFunctionAndCache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>, Invalidate<ShapeKey<TLongHashSet>>>[] meshCaches = CacheUtils
				.segmentMeshCacheLoaders(
				source,
				l -> (s, t) -> t.set(s.get() > 0),
				globalCache::createNewCache);

		final FragmentSegmentAssignmentState assignment                  = labels.assignment();
		final SelectedSegments               selectedSegments            = new SelectedSegments(
				selectedIds,
				assignment
		);
		final FragmentsInSelectedSegments    fragmentsInSelectedSegments = new FragmentsInSelectedSegments(
				selectedSegments,
				assignment
		);

		this.meshManager = new MeshManagerSimple<>(
				meshManager.blockListCache(),
				// BlocksForLabelDelegate.delegate(
				// meshManager.blockListCache(), key -> Arrays.stream(
				// fragmentsInSelectedSegments.getFragments() ).mapToObj( l -> l
				// ).toArray( Long[]::new ) ),
				Stream.of(meshCaches).map(Pair::getA).toArray(InterruptibleFunctionAndCache[]::new),
				meshesGroup,
				new SimpleIntegerProperty(),
				new SimpleDoubleProperty(),
				new SimpleIntegerProperty(),
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
		this.meshManager.scaleLevelProperty().bind(meshManager.scaleLevelProperty());
		this.meshManager.areMeshesEnabledProperty().bind(meshManager.areMeshesEnabledProperty());
		this.meshManager.meshSimplificationIterationsProperty().bind(meshManager.meshSimplificationIterationsProperty
				());
		this.meshManager.smoothingIterationsProperty().bind(meshManager.smoothingIterationsProperty());
		this.meshManager.smoothingLambdaProperty().bind(meshManager.smoothingLambdaProperty());

		thresholded.getThreshold().minValue().addListener((obs, oldv, newv) -> {
			Arrays.stream(meshCaches).map(Pair::getB).forEach(InvalidateAll::invalidateAll);
			update(source, fragmentsInSelectedSegments);
		});
		thresholded.getThreshold().maxValue().addListener((obs, oldv, newv) -> {
			Arrays.stream(meshCaches).map(Pair::getB).forEach(InvalidateAll::invalidateAll);
			update(source, fragmentsInSelectedSegments);
		});

		//		selectedIds.addListener( obs -> update( source, fragmentsInSelectedSegments ) );
		//		assignment.addListener( obs -> update( source, fragmentsInSelectedSegments ) );
		fragmentsInSelectedSegments.addListener(obs -> update(source, fragmentsInSelectedSegments));
	}

	private void update(
			final DataSource<?, ?> source,
			final FragmentsInSelectedSegments fragmentsInSelectedSegments)
	{
		for (int level = 0; level < source.getNumMipmapLevels(); ++level)
		{
			final DataSource<?, ?>            dsource = source instanceof MaskedSource<?, ?>
			                                            ? ((MaskedSource<?, ?>) source).underlyingSource()
			                                            : source;
			final RandomAccessibleInterval<?> data    = dsource.getDataSource(0, level);
			if (data instanceof CachedCellImg<?, ?>)
			{
				LOG.debug("Invalidating: data type={}", data.getClass().getName());
				((CachedCellImg<?, ?>) data).getCache().invalidateAll();
			}

			final RandomAccessibleInterval<?> vdata = dsource.getSource(0, level);
			if (vdata instanceof VolatileCachedCellImg)
			{
				LOG.debug("Invalidating: vdata type={}", vdata.getClass().getName());
				((VolatileCachedCellImg<?, ?>) vdata).getInvalidateAll().run();
			}

		}

		this.meshManager.removeAllMeshes();
		if (Optional.ofNullable(fragmentsInSelectedSegments.getFragments()).map(sel -> sel.length).orElse(0) > 0)
		{
			this.meshManager.generateMesh(new TLongHashSet(fragmentsInSelectedSegments.getFragments()));
		}
	}

	public MeshManager<TLongHashSet, TLongHashSet> meshManager()
	{
		return this.meshManager;
	}

	private static <D extends IntegerType<D>, T extends Type<T>, B extends BooleanType<B>>
	DataSource<UnsignedByteType, VolatileUnsignedByteType> makeIntersect(
			final SourceState<B, Volatile<B>> thresholded,
			final LabelSourceState<D, T> labels,
			final GlobalCache globalCache,
			final int priority,
			final String name) throws InvalidAccessException {
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
				selectedSegments,
				assignment
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

			final Pair<CachedCellImg<UnsignedByteType, VolatileByteArray>, Invalidate<Long>> imgAndInvalidate =
					globalCache.createVolatileImg(grid, loader, new UnsignedByteType());
			final Triple<RandomAccessibleInterval<VolatileUnsignedByteType>, VolatileCache<Long, Cell<VolatileByteArray>>, Invalidate<Long>> vimgAndInvalidate =
					globalCache.wrapAsVolatile(imgAndInvalidate.getA(), imgAndInvalidate.getB(), priority);
			data[level] = imgAndInvalidate.getA();
			vdata[level] = vimgAndInvalidate.getA();
			invalidate[level] = imgAndInvalidate.getB();
			vinvalidate[level] = vimgAndInvalidate.getC();
			transforms[level] = tf1;
		}

		return new RandomAccessibleIntervalDataSource<>(
				new ValueTriple<>(data, vdata, transforms),
				() -> {Stream.of(invalidate).forEach(InvalidateAll::invalidateAll); Stream.of(vinvalidate).forEach(InvalidateAll::invalidateAll);},
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
