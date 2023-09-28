package org.janelia.saalfeldlab.paintera.control.paint;

import bdv.fx.viewer.ViewerPanelFX;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;
import javafx.animation.AnimationTimer;
import javafx.beans.value.ObservableValue;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.AccessBoxRandomAccessible;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.fx.Tasks;
import org.janelia.saalfeldlab.fx.UtilityTask;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment;
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.data.mask.SourceMask;
import org.janelia.saalfeldlab.paintera.data.mask.exception.MaskInUse;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FloodFill<T extends IntegerType<T>> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


	private static ExecutorService floodFillExector = newFloodFillExecutor();

	private static ExecutorService newFloodFillExecutor() {
		return Executors.newFixedThreadPool(Math.min(Runtime.getRuntime().availableProcessors() - 1, 1), new NamedThreadFactory("flood-fill-3d", true, 8));
	}

	private final ObservableValue<ViewerPanelFX> activeViewerProperty;

	private final MaskedSource<T, ?> source;

	private final FragmentSegmentAssignment assignment;

	private final Consumer<Interval> requestRepaint;

	private final BooleanSupplier isVisible;

	public FloodFill(
			final ObservableValue<ViewerPanelFX> activeViewerProperty,
			final MaskedSource<T, ?> source,
			final FragmentSegmentAssignment assignment,
			final Consumer<Interval> requestRepaint,
			final BooleanSupplier isVisible) {

		super();
		Objects.requireNonNull(activeViewerProperty);
		Objects.requireNonNull(source);
		Objects.requireNonNull(assignment);
		Objects.requireNonNull(requestRepaint);
		Objects.requireNonNull(isVisible);

		this.activeViewerProperty = activeViewerProperty;
		this.source = source;
		this.assignment = assignment;
		this.requestRepaint = requestRepaint;
		this.isVisible = isVisible;
	}

	public UtilityTask<?> fillAt(final double x, final double y, final Supplier<Long> fillSupplier) {

		final Long fill = fillSupplier.get();
		if (fill == null) {
			LOG.info("Received invalid label {} -- will not fill.", fill);
			return null;
		}
		return fillAt(x, y, fill);
	}

	private UtilityTask<?> fillAt(final double x, final double y, final long fill) {

		// TODO should this check happen outside?
		if (!isVisible.getAsBoolean()) {
			LOG.info("Selected source is not visible -- will not fill");
			return null;
		}

		final int level = 0;
		final AffineTransform3D labelTransform = new AffineTransform3D();
		// TODO What to do for time series?
		final int time = 0;
		source.getSourceTransform(time, level, labelTransform);

		final RealPoint realSourceSeed = viewerToSourceCoordinates(x, y, activeViewerProperty.getValue(), labelTransform);
		final Point sourceSeed = new Point(realSourceSeed.numDimensions());
		for (int d = 0; d < sourceSeed.numDimensions(); ++d) {
			sourceSeed.setPosition(Math.round(realSourceSeed.getDoublePosition(d)), d);
		}

		LOG.debug("Filling source {} with label {} at {}", source, fill, sourceSeed);
		try {
			return fill(time, level, fill, sourceSeed, assignment);
		} catch (final MaskInUse e) {
			LOG.info(e.getMessage());
			return null;
		}

	}

	private static RealPoint viewerToSourceCoordinates(
			final double x,
			final double y,
			final ViewerPanelFX viewer,
			final AffineTransform3D labelTransform) {

		return viewerToSourceCoordinates(x, y, new RealPoint(labelTransform.numDimensions()), viewer, labelTransform);
	}

	private static <P extends RealLocalizable & RealPositionable> P viewerToSourceCoordinates(
			final double x,
			final double y,
			final P location,
			final ViewerPanelFX viewer,
			final AffineTransform3D labelTransform) {

		location.setPosition(x, 0);
		location.setPosition(y, 1);
		location.setPosition(0, 2);

		viewer.displayToGlobalCoordinates(location);
		labelTransform.applyInverse(location, location);

		return location;
	}

	private UtilityTask<?> fill(
			final int time,
			final int level,
			final long fill,
			final Localizable seed,
			final FragmentSegmentAssignment assignment) throws MaskInUse {

		final RandomAccessibleInterval<T> data = source.getDataSource(time, level);
		final RandomAccess<T> dataAccess = data.randomAccess();
		dataAccess.setPosition(seed);
		final T seedValue = dataAccess.get();
		final long seedLabel = assignment != null ? assignment.getSegment(seedValue.getIntegerLong()) : seedValue.getIntegerLong();
		if (!Label.regular(seedLabel)) {
			LOG.info("Trying to fill at irregular label: {} ({})", seedLabel, new Point(seed));
			return null;
		}

		final MaskInfo maskInfo = new MaskInfo(
				time,
				level
		);
		final SourceMask mask = source.generateMask(maskInfo, MaskedSource.VALID_LABEL_CHECK);
		final AffineTransform3D globalToSource = source.getSourceTransformForMask(maskInfo).inverse();

		final List<RealInterval> visibleSourceIntervals = Paintera.getPaintera().getBaseView().orthogonalViews().views().stream()
				.filter(it -> it.isVisible() && it.getWidth() > 0.0 && it.getHeight() > 0.0)
				.map(ViewerMask::getGlobalViewerInterval)
				.map(globalToSource::estimateBounds)
				.map(Intervals::smallestContainingInterval)
				.collect(Collectors.toList());

		final AtomicBoolean triggerRefresh = new AtomicBoolean(false);
		final AccessBoxRandomAccessible<UnsignedLongType> accessTracker = new AccessBoxRandomAccessible<>(Views.extendValue(mask.getRai(), new UnsignedLongType(1))) {

			final Point position = new Point(sourceAccess.numDimensions());

			@Override
			public UnsignedLongType get() {
				if (Thread.currentThread().isInterrupted())
					throw new RuntimeException("Flood Fill Interrupted");
				synchronized (this) {
					updateAccessBox();
				}
				sourceAccess.localize(position);
				if (!triggerRefresh.get()) {
					for (RealInterval interval : visibleSourceIntervals) {
						if (Intervals.contains(interval, position)) {
							triggerRefresh.set(true);
							break;
						}
					}
				}
				return sourceAccess.get();
			}
		};

		final UtilityTask<?> floodFillTask = Tasks.createTask(task -> {
					if (seedValue instanceof LabelMultisetType) {
						fillMultisetType((RandomAccessibleInterval<LabelMultisetType>) data, accessTracker, seed, seedLabel, fill, assignment);
					} else {
						fillPrimitiveType(data, accessTracker, seed, seedLabel, fill, assignment);
					}
				}
		).onCancelled((state, task) -> {
					try {
						source.resetMasks();
					} catch (final MaskInUse e) {
						e.printStackTrace();
					}
				}
		).onFailed((event, task) -> {
					if (!Thread.currentThread().isInterrupted() && task.getException() != null && !(task.getException() instanceof CancellationException)) {
						throw new RuntimeException(task.getException());
					}
				}
		).onSuccess((state, task) -> {
					LOG.debug(Thread.currentThread().isInterrupted() ? "FloodFill has been interrupted" : "FloodFill has been completed");

					final Interval interval = accessTracker.createAccessInterval();
					LOG.debug(
							"Applying mask for interval {} {}",
							Arrays.toString(Intervals.minAsLongArray(interval)),
							Arrays.toString(Intervals.maxAsLongArray(interval))
					);
					source.applyMask(mask, interval, MaskedSource.VALID_LABEL_CHECK);
				}
		);

		final var refreshAnimation = new AnimationTimer() {

			final static long delay = 2_000_000_000; // 2 second delay before refreshes start
			final static long REFRESH_RATE = 1_000_000_000;
			final long start = System.nanoTime();
			long before = start;

			@Override
			public void handle(long now) {
				if (now - start < delay || now - before < REFRESH_RATE) return;
				if (!floodFillTask.isCancelled() && triggerRefresh.get()) {
					requestRepaint.accept(accessTracker.createAccessInterval());
					before = now;
					triggerRefresh.set(false);
				}
			}
		};


		floodFillTask.onEnd(task -> {
			refreshAnimation.stop();
			requestRepaint.accept(accessTracker.createAccessInterval());
		});

		if (floodFillExector.isShutdown()) {
			floodFillExector = newFloodFillExecutor();
		}
		refreshAnimation.start();
		floodFillTask.submit(floodFillExector);
		return floodFillTask;
	}

	private static void fillMultisetType(
			final RandomAccessibleInterval<LabelMultisetType> input,
			final RandomAccessible<UnsignedLongType> output,
			final Localizable seed,
			final long seedLabel,
			final long fillLabel,
			final FragmentSegmentAssignment assignment) {

		net.imglib2.algorithm.fill.FloodFill.fill(
				Views.extendValue(input, new LabelMultisetType()),
				output,
				seed,
				new UnsignedLongType(fillLabel),
				new DiamondShape(1),
				makePredicate(seedLabel, assignment)
		);
	}

	private static <T extends IntegerType<T>> void fillPrimitiveType(
			final RandomAccessibleInterval<T> input,
			final RandomAccessible<UnsignedLongType> output,
			final Localizable seed,
			final long seedLabel,
			final long fillLabel,
			final FragmentSegmentAssignment assignment) {

		final T extension = Util.getTypeFromInterval(input).createVariable();
		extension.setInteger(Label.OUTSIDE);

		net.imglib2.algorithm.fill.FloodFill.fill(
				Views.extendValue(input, extension),
				output,
				seed,
				new UnsignedLongType(fillLabel),
				new DiamondShape(1),
				makePredicate(seedLabel, assignment)
		);
	}

	private static <T extends IntegerType<T>> BiPredicate<T, UnsignedLongType> makePredicate(final long seedLabel, final FragmentSegmentAssignment assignment) {

		final Long singleFragment;
		final TLongHashSet seedFragments;
		if (assignment != null) {
			seedFragments = assignment.getFragments(seedLabel);
			singleFragment = seedFragments.size() == 1 ? seedFragments.toArray()[0] : null;
		} else {
			singleFragment = seedLabel;
			seedFragments = null;
		}

		return (sourceVal, targetVal) -> {
			if (Thread.currentThread().isInterrupted()) return false;
			/* true if sourceFragment is a seedFragment */
			final long sourceFragment = sourceVal.getIntegerLong();
			final var shouldFill = singleFragment != null ? singleFragment == sourceFragment : seedFragments.contains(sourceFragment);
			/* Most target vals are typically invalid, so this is rarely not passed; The sourceMatch filter is likely to
			 * shortcircuit more often */
			return shouldFill && targetVal.getInteger() == Label.INVALID;
		};

	}

	public static class RunAll implements Runnable {

		private final List<Runnable> runnables;

		public RunAll(final Runnable... runnables) {

			this(Arrays.asList(runnables));
		}

		public RunAll(final Collection<Runnable> runnables) {

			super();
			this.runnables = new ArrayList<>(runnables);
		}

		@Override
		public void run() {

			this.runnables.forEach(Runnable::run);
		}

	}

	/**
	 * Iterative n-dimensional flood fill for arbitrary neighborhoods: Starting
	 * at seed location, write fillLabel into target at current location and
	 * continue for each pixel in neighborhood defined by shape if neighborhood
	 * pixel is in the same connected component and fillLabel has not been
	 * written into that location yet.
	 *
	 * @param source input
	 * @param target {@link RandomAccessible} to be written into. May be the same
	 *               as input.
	 * @param seed   Start flood fill at this location.
	 * @param shape  Defines neighborhood that is considered for connected
	 *               components, e.g.
	 *               {@link net.imglib2.algorithm.neighborhood.DiamondShape}
	 * @param filter Returns true if pixel has not been visited yet and should be
	 *               written into. Returns false if target pixel has been visited
	 *               or source pixel is not part of the same connected component.
	 * @param writer Defines how fill label is written into target at current
	 *               location.
	 * @param <B>    input pixel type
	 * @param <U>    fill label type
	 */
	public static <B, U> Interval trackedFill(
			final RandomAccessible<B> source,
			final RandomAccessible<U> target,
			final Localizable seed,
			final Shape shape,
			final BiPredicate<B, U> filter,
			final Consumer<U> writer) {

		Interval interval = null;
		final int n = source.numDimensions();

		final RandomAccessible<Pair<B, U>> paired = Views.pair(source, target);

		TLongList coordinates = new TLongArrayList();
		for (int d = 0; d < n; ++d) {
			coordinates.add(seed.getLongPosition(d));
		}

		final int cleanupThreshold = n * (int) 1e5;

		final RandomAccessible<Neighborhood<Pair<B, U>>> neighborhood = shape.neighborhoodsRandomAccessible(paired);
		final RandomAccess<Neighborhood<Pair<B, U>>> neighborhoodAccess = neighborhood.randomAccess();

		final RandomAccess<U> targetAccess = target.randomAccess();
		targetAccess.setPosition(seed);
		writer.accept(targetAccess.get());

		for (int i = 0; i < coordinates.size(); i += n) {
			for (int d = 0; d < n; ++d) {
				neighborhoodAccess.setPosition(coordinates.get(i + d), d);
			}

			final Cursor<Pair<B, U>> neighborhoodCursor = neighborhoodAccess.get().cursor();

			while (neighborhoodCursor.hasNext()) {
				final Pair<B, U> p = neighborhoodCursor.next();
				if (filter.test(p.getA(), p.getB())) {
					writer.accept(p.getB());
					if (interval == null) {
						interval = new FinalInterval(neighborhoodCursor.positionAsLongArray(), neighborhoodCursor.positionAsLongArray());
					} else {
						if (!Intervals.contains(interval, neighborhoodCursor.positionAsPoint())) {
							interval = Intervals.union(interval,
									new FinalInterval(neighborhoodCursor.positionAsLongArray(), neighborhoodCursor.positionAsLongArray()));
						}
					}
					for (int d = 0; d < n; ++d) {
						coordinates.add(neighborhoodCursor.getLongPosition(d));
					}
				}
			}

			if (i > cleanupThreshold) {
				// TODO should it start from i + n?
				coordinates = coordinates.subList(i, coordinates.size());
				i = 0;
			}

		}
		return interval;

	}

}

