package org.janelia.saalfeldlab.paintera.state;

import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.THashSet;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import net.imglib2.Interval;
import net.imglib2.Volatile;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.cache.CacheLoader;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.BooleanType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.view.Views;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.PredicateDataSource;
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.MeshViewUpdateQueue;
import org.janelia.saalfeldlab.paintera.meshes.PainteraTriangleMesh;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.janelia.saalfeldlab.paintera.meshes.cache.GenericMeshCacheLoader;
import org.janelia.saalfeldlab.paintera.meshes.managed.GetBlockListFor;
import org.janelia.saalfeldlab.paintera.meshes.managed.GetMeshFor;
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithSingleMesh;
import org.janelia.saalfeldlab.paintera.state.ThresholdingSourceState.Threshold;
import org.janelia.saalfeldlab.paintera.state.ThresholdingSourceState.VolatileMaskConverter;
import org.janelia.saalfeldlab.paintera.state.predicate.threshold.Bounds;
import org.janelia.saalfeldlab.util.Colors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.function.Predicate;

public class ThresholdingSourceState<D extends RealType<D>, T extends AbstractVolatileRealType<D, T>>
		extends MinimalSourceState<BoolType, Volatile<BoolType>, PredicateDataSource<D, T, Threshold<D>>, VolatileMaskConverter<BoolType, Volatile<BoolType>>>
		implements IntersectableSourceState<BoolType, Volatile<BoolType>, ThresholdingSourceState.ThresholdMeshCacheKey> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final boolean DEFAULT_MESHES_ENABLED = false;

	private final ObjectProperty<Color> color = new SimpleObjectProperty<>(Color.WHITE);

	private final ObjectProperty<Color> backgroundColor = new SimpleObjectProperty<>(Color.BLACK);

	private final DoubleProperty alpha = new SimpleDoubleProperty(1.0);

	private final Threshold<D> threshold;

	private final SourceState<D, T> underlyingSource;

	private final DoubleProperty min = new SimpleDoubleProperty();

	private final DoubleProperty max = new SimpleDoubleProperty();

	private final MeshManagerWithSingleMesh<Bounds> meshes;

	private final ObjectBinding<ThresholdMeshCacheKey> meshCacheKeyProperty = Bindings.createObjectBinding(() -> {
		final var bounds = getThresholdBounds();
		return new ThresholdMeshCacheKey(bounds);
	}, min, max);

	private final int[] blockSize = new int[]{32, 32, 32};

	private final TIntObjectHashMap<Interval[]> affectedBlocskByLevel = new TIntObjectHashMap<>(getDataSource().getNumMipmapLevels());

	public ThresholdingSourceState(final String name, final SourceState<D, T> toBeThresholded, PainteraBaseView viewer) {

		super(
				threshold(toBeThresholded.getDataSource(), name),
				new VolatileMaskConverter<>(),
				new ARGBCompositeAlphaAdd(),
				name,
				toBeThresholded);
		this.threshold = getDataSource().getPredicate();
		this.underlyingSource = toBeThresholded;
		this.color.addListener((obs, oldv, newv) -> converter().setMasked(Colors.toARGBType(newv)));
		this.backgroundColor.addListener((obs, oldv, newv) -> converter().setNotMasked(Colors.toARGBType(newv)));
		threshold.minSupplier.bind(min);
		threshold.maxSupplier.bind(max);

		final D d = underlyingSource.getDataSource().getDataType();
		if (d instanceof IntegerType<?>) {
			this.min.set(d.getMinValue());
			this.max.set(d.getMaxValue());
		} else {
			this.min.set(0.0);
			this.max.set(1.0);
		}

		min.addListener((obs, oldv, newv) -> updateThreshold());
		max.addListener((obs, oldv, newv) -> updateThreshold());

		final AffineTransform3D[] transforms = getDataSource().getSourceTransformCopies(0);
		CacheLoader<ShapeKey<Bounds>, PainteraTriangleMesh> loader = new GenericMeshCacheLoader<>(
				level -> getDataSource().getDataSource(0, level),
				level -> transforms[level]);
		final GetBlockListFor<Bounds> getBlockListFor = (level, bounds) -> getBlockList(level);

		this.meshes = new MeshManagerWithSingleMesh<>(
				getDataSource(),
				getBlockListFor,
				GetMeshFor.FromCache.fromLoader(loader),
				viewer.viewer3D().getViewFrustumProperty(),
				viewer.viewer3D().getEyeToWorldTransformProperty(),
				viewer.getMeshManagerExecutorService(),
				viewer.getMeshWorkerExecutorService(),
				new MeshViewUpdateQueue<>());

		/*Threshold Meshes turned off by default */
		this.meshes.getManagedSettings().getMeshesEnabledProperty().set(false);
	}

	private void updateThreshold() {
		/* These need to be regenerated, if we are changing the threshold */
		affectedBlocskByLevel.clear();
		setMeshId();
	}

	private Interval[] getBlockList(int level) {

		if (affectedBlocskByLevel.get(level) != null) {
			return affectedBlocskByLevel.get(level);
		}

		final THashSet<Interval> growingIntervalSet = new THashSet<>();

		final var grid = Grids.collectAllContainedIntervals(getDataSource().getDataSource(0, level).dimensionsAsLongArray(), blockSize);
		for (Interval interval : grid) {
			final var view = Views.interval(getDataSource().getDataSource(0, level), interval);
			final var cursor = view.cursor();
			while (cursor.hasNext()) {
				if (cursor.next().get()) {
					growingIntervalSet.add(interval);
					break;
				}
			}
		}

		affectedBlocskByLevel.put(level, growingIntervalSet.toArray(new Interval[0]));
		return affectedBlocskByLevel.get(level);
	}

	private GetBlockListFor<ThresholdMeshCacheKey> getGetBlockListForMeshCaheKey() {

		return (level, meshCacheKey) -> getBlockList(level);
	}

	// could remove this and just expose actualMin, actualMax
	public Threshold<D> getThreshold() {

		return this.threshold;
	}

	@Override
	public ObjectBinding<ThresholdMeshCacheKey> getMeshCacheKeyBinding() {

		return meshCacheKeyProperty;
	}

	@Override
	public GetBlockListFor<ThresholdMeshCacheKey> getGetBlockListFor() {

		return getGetBlockListForMeshCaheKey();
	}

	@Override
	public DataSource<BoolType, Volatile<BoolType>> getIntersectableMask() {

		return getDataSource();
	}

	public ObjectProperty<Color> colorProperty() {

		return this.color;
	}

	public ObjectProperty<Color> backgroundColorProperty() {

		return this.backgroundColor;
	}

	public DoubleProperty alphaProperty() {

		return this.alpha;
	}

	public MeshManagerWithSingleMesh<Bounds> getMeshManager() {

		return this.meshes;
	}

	private SourceState<D, T> getUnderlyingSource() {

		return this.underlyingSource;
	}

	public DoubleProperty minProperty() {

		return min;
	}

	public DoubleProperty maxProperty() {

		return max;
	}

	public MeshSettings getMeshSettings() {

		return this.meshes.getGlobalSettings();
	}

	public void refreshMeshes() {

		if (meshes != null)
			meshes.refreshMeshes();
	}

	@Override public void onRemoval(SourceInfo sourceInfo) {

		getMeshManager().removeAllMeshes();
	}

	@Override
	public void onAdd(final PainteraBaseView paintera) {

		color.addListener(obs -> paintera.orthogonalViews().requestRepaint());
		backgroundColor.addListener(obs -> paintera.orthogonalViews().requestRepaint());
		min.addListener(obs -> paintera.orthogonalViews().requestRepaint());
		max.addListener(obs -> paintera.orthogonalViews().requestRepaint());

		// add meshes to viewer
		// this could happen in the constructor to avoid null check
		// but then the deserializer would have to be stateful
		// and know about the mesh managers and workers
		paintera.viewer3D().getMeshesGroup().getChildren().add(this.meshes.getMeshesGroup());

		this.meshes.getViewerEnabledProperty().bind(paintera.viewer3D().getMeshesEnabled());
		this.meshes.getRendererSettings().getShowBlockBoundariesProperty().bind(paintera.viewer3D().getShowBlockBoundaries());
		this.meshes.getRendererSettings().getBlockSizeProperty().bind(paintera.viewer3D().getRendererBlockSize());
		this.meshes.getRendererSettings().getNumElementsPerFrameProperty().bind(paintera.viewer3D().getNumElementsPerFrame());
		this.meshes.getRendererSettings().getFrameDelayMsecProperty().bind(paintera.viewer3D().getFrameDelayMsec());
		this.meshes.getRendererSettings().getSceneUpdateDelayMsecProperty().bind(paintera.viewer3D().getSceneUpdateDelayMsec());
		this.meshes.getColorProperty().bind(this.color);

		setMeshId();
		refreshMeshes();
	}

	@Override
	public Node preferencePaneNode() {

		return new ThresholdingSourceStatePreferencePaneNode(this).getNode();
	}

	private void setMeshId() {

		if (this.meshes != null)
			setMeshId(this.meshes);
	}

	private void setMeshId(final MeshManagerWithSingleMesh<Bounds> meshes) {

		try {
			BuildersKt.runBlocking(
					EmptyCoroutineContext.INSTANCE,
					(scope, continuation) -> meshes.createMeshFor(getThresholdBounds(), continuation)
			);
		} catch (InterruptedException e) {
			LOG.debug("create mesh interrupted");
		}
	}

	public Bounds getThresholdBounds() {

		return new Bounds(min.doubleValue(), max.doubleValue());
	}

	private static <D extends RealType<D>, T extends AbstractVolatileRealType<D, T>> PredicateDataSource<D, T, Threshold<D>>
	threshold(final DataSource<D, T> source, final String name) {

		return new PredicateDataSource<>(source, new Threshold<>(), name);
	}

	public static final class ThresholdMeshCacheKey implements MeshCacheKey {

		private final Bounds bounds;

		public ThresholdMeshCacheKey(final Bounds bounds) {

			this.bounds = bounds;
		}

		@Override
		public int hashCode() {

			return new HashCodeBuilder().append(bounds).toHashCode();
		}

		@Override
		public boolean equals(Object obj) {

			return obj instanceof ThresholdMeshCacheKey && ((ThresholdMeshCacheKey) obj).bounds.equals(this.bounds);
		}

		@Override
		public String toString() {

			return "ThresholdMeshCacheKey: (" + bounds.toString() + ")";
		}

	}

	public static class MaskConverter<B extends BooleanType<B>> implements Converter<B, ARGBType> {

		private final ARGBType masked = Colors.toARGBType(Color.rgb(255, 255, 255, 1.0));

		private final ARGBType notMasked = Colors.toARGBType(Color.rgb(0, 0, 0, 0.0));

		@Override
		public void convert(final B mask, final ARGBType color) {

			color.set(mask.get() ? masked : notMasked);
		}

		public void setMasked(final ARGBType masked) {

			this.masked.set(masked);
		}

		public void setNotMasked(final ARGBType notMasked) {

			this.notMasked.set(masked);
		}

	}

	public static class VolatileMaskConverter<B extends BooleanType<B>, V extends Volatile<B>>
			implements Converter<V, ARGBType> {

		private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

		private final ARGBType masked = Colors.toARGBType(Color.rgb(255, 255, 255, 1.0));

		private final ARGBType notMasked = Colors.toARGBType(Color.rgb(0, 0, 0, 0.0));

		@Override
		public void convert(final V mask, final ARGBType color) {

			color.set(mask.get().get() ? masked : notMasked);
		}

		public void setMasked(final ARGBType masked) {

			this.masked.set(masked);
		}

		public void setNotMasked(final ARGBType notMasked) {

			this.notMasked.set(notMasked);
		}

		public ARGBType getMasked() {

			return masked;
		}

		public ARGBType getNotMasked() {

			return notMasked;
		}
	}

	public static class Threshold<T extends RealType<T>> implements Predicate<T> {

		private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

		private double min;

		private double max;

		private final DoubleProperty minSupplier = new SimpleDoubleProperty();

		private final DoubleProperty maxSupplier = new SimpleDoubleProperty();

		public Threshold() {

			super();
			this.minSupplier.addListener((obs, oldv, newv) -> update());
			this.maxSupplier.addListener((obs, oldv, newv) -> update());
			update();
		}

		@Override
		public boolean test(final T t) {

			final double val = t.getRealDouble();
			final boolean isWithinMinMax = val < this.max && val > this.min;
			return isWithinMinMax;
		}

		private void update() {

			final double m = this.minSupplier.get();
			final double M = this.maxSupplier.get();

			if (m < M) {
				this.min = m;
				this.max = M;
			} else {
				this.min = M;
				this.max = m;
			}
		}

		public ObservableDoubleValue minValue() {

			return this.minSupplier;
		}

		public ObservableDoubleValue maxValue() {

			return this.maxSupplier;
		}

	}

}

