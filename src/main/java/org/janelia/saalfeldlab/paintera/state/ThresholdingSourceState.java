package org.janelia.saalfeldlab.paintera.state;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.scene.Node;
import javafx.scene.paint.Color;
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
import net.imglib2.util.Intervals;
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
import java.util.Arrays;
import java.util.function.Predicate;

public class ThresholdingSourceState<D extends RealType<D>, T extends AbstractVolatileRealType<D, T>>
		extends
		MinimalSourceState<BoolType, Volatile<BoolType>, PredicateDataSource<D, T, Threshold<D>>,
				VolatileMaskConverter<BoolType, Volatile<BoolType>>> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final boolean DEFAULT_MESHES_ENABLED = false;

	private final ObjectProperty<Color> color = new SimpleObjectProperty<>(Color.WHITE);

	private final ObjectProperty<Color> backgroundColor = new SimpleObjectProperty<>(Color.BLACK);

	private final DoubleProperty alpha = new SimpleDoubleProperty(1.0);

	private final Threshold<D> threshold;

	private final SourceState<D, T> underlyingSource;

	private final DoubleProperty min = new SimpleDoubleProperty();

	private final DoubleProperty max = new SimpleDoubleProperty();

	private MeshManagerWithSingleMesh<Bounds> meshes = null;

	private final MeshSettings meshSettings;

	private final BooleanProperty meshesEnabled = new SimpleBooleanProperty(DEFAULT_MESHES_ENABLED);

	public ThresholdingSourceState(
			final String name,
			final SourceState<D, T> toBeThresholded)
	{
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

		min.addListener((obs, oldv, newv) -> setMeshId());
		max.addListener((obs, oldv, newv) -> setMeshId());

		this.meshSettings = new MeshSettings(getDataSource().getNumMipmapLevels());

	}

	// could remove this and just expose actualMin, actualMax
	public Threshold<D> getThreshold()
	{
		return this.threshold;
	}

	private static <D extends RealType<D>, T extends AbstractVolatileRealType<D, T>> PredicateDataSource<D, T,
			Threshold<D>> threshold(
			final DataSource<D, T> source,
			final String name)
	{
		return new PredicateDataSource<>(source, new Threshold<>(), name);
	}

	public ObjectProperty<Color> colorProperty()
	{
		return this.color;
	}
	public ObjectProperty<Color> backgroundColorProperty() {
		return this.backgroundColor;
	}

	public DoubleProperty alphaProperty()
	{
		return this.alpha;
	}

	public static class MaskConverter<B extends BooleanType<B>> implements Converter<B, ARGBType>
	{

		private final ARGBType masked = Colors.toARGBType(Color.rgb(255, 255, 255, 1.0));

		private final ARGBType notMasked = Colors.toARGBType(Color.rgb(0, 0, 0, 0.0));

		@Override
		public void convert(final B mask, final ARGBType color)
		{
			color.set(mask.get() ? masked : notMasked);
		}

		public void setMasked(final ARGBType masked)
		{
			this.masked.set(masked);
		}

		public void setNotMasked(final ARGBType notMasked)
		{
			this.notMasked.set(masked);
		}

	}

	public static class VolatileMaskConverter<B extends BooleanType<B>, V extends Volatile<B>>
			implements Converter<V, ARGBType>
	{

		private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

		private final ARGBType masked = Colors.toARGBType(Color.rgb(255, 255, 255, 1.0));

		private final ARGBType notMasked = Colors.toARGBType(Color.rgb(0, 0, 0, 0.0));

		@Override
		public void convert(final V mask, final ARGBType color)
		{
			color.set(mask.get().get() ? masked : notMasked);
		}

		public void setMasked(final ARGBType masked)
		{
			this.masked.set(masked);
		}

		public void setNotMasked(final ARGBType notMasked)
		{
			this.notMasked.set(notMasked);
		}

		public ARGBType getMasked()
		{
			return masked;
		}

		public ARGBType getNotMasked()
		{
			return notMasked;
		}
	}

	public static class Threshold<T extends RealType<T>> implements Predicate<T>
	{
		private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

		private double min;

		private double max;

		private final DoubleProperty minSupplier = new SimpleDoubleProperty();

		private final DoubleProperty maxSupplier = new SimpleDoubleProperty();

		public Threshold()
		{
			super();
			this.minSupplier.addListener((obs, oldv, newv) -> update());
			this.maxSupplier.addListener((obs, oldv, newv) -> update());
			update();
		}

		@Override
		public boolean test(final T t)
		{
			final double  val            = t.getRealDouble();
			final boolean isWithinMinMax = val < this.max && val > this.min;
			return isWithinMinMax;
		}

		private void update()
		{
			final double m = this.minSupplier.get();
			final double M = this.maxSupplier.get();

			if (m < M)
			{
				this.min = m;
				this.max = M;
			}
			else
			{
				this.min = M;
				this.max = m;
			}
		}

		public ObservableDoubleValue minValue()
		{
			return this.minSupplier;
		}

		public ObservableDoubleValue maxValue()
		{
			return this.maxSupplier;
		}


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
		return this.meshSettings;
	}

	public void refreshMeshes() {
		final MeshManagerWithSingleMesh<Bounds> meshes = this.meshes;
		if (meshes != null)
			meshes.refreshMeshes();
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

		// TODO Come up with better scheme for getBlocksFor
		// TODO We could probably just use min(Integer.MAX_VALUE, getDataSource().getDataSource(0, level).dimension)
		// TODO as blockSize because we use the entire volume for generating meshes anyway.
		final int[] blockSize = {32, 32, 32};
		final Interval[][] blockLists = new Interval[getDataSource().getNumMipmapLevels()][];
		final AffineTransform3D[] transforms = getDataSource().getSourceTransformCopies(0);
		Arrays.setAll(blockLists, d -> Grids.collectAllContainedIntervals(Intervals.dimensionsAsLongArray(getDataSource().getDataSource(0, d)), blockSize).stream().toArray(Interval[]::new));
		CacheLoader<ShapeKey<Bounds>, PainteraTriangleMesh> loader = new GenericMeshCacheLoader<>(
				new int[]{1, 1, 1},
				level -> getDataSource().getDataSource(0, level),
				level -> transforms[level]);
		final GetBlockListFor<Bounds> getBlockListFor = (level, bounds) -> {
			final Interval[] blocks = blockLists[level];
			LOG.debug("Got blocks for id {}: {}", bounds, blocks);
			return blocks;
		};

		this.meshes = new MeshManagerWithSingleMesh<>(
				getDataSource(),
				getBlockListFor,
				GetMeshFor.FromCache.fromLoader(loader),
				paintera.viewer3D().viewFrustumProperty(),
				paintera.viewer3D().eyeToWorldTransformProperty(),
				paintera.getMeshManagerExecutorService(),
				paintera.getMeshWorkerExecutorService(),
				new MeshViewUpdateQueue<>());
		this.meshes.viewerEnabledProperty().bind(paintera.viewer3D().meshesEnabledProperty());

		paintera.viewer3D().meshesGroup().getChildren().add(this.meshes.getMeshesGroup());
		this.meshes.getSettings().bindBidirectionalTo(meshSettings);
		this.meshes.getRendererSettings().meshesEnabledProperty().bindBidirectional(this.meshesEnabled);
		this.meshes.getRendererSettings().blockSizeProperty().bind(paintera.viewer3D().rendererBlockSizeProperty());
		this.meshes.getRendererSettings().showBlockBoundariesProperty().bind(paintera.viewer3D().showBlockBoundariesProperty());
		this.meshes.getRendererSettings().frameDelayMsecProperty().bind(paintera.viewer3D().frameDelayMsecProperty());
		this.meshes.getRendererSettings().numElementsPerFrameProperty().bind(paintera.viewer3D().numElementsPerFrameProperty());
		this.meshes.getRendererSettings().sceneUpdateDelayMsecProperty().bind(paintera.viewer3D().sceneUpdateDelayMsecProperty());
		this.meshes.colorProperty().bind(this.color);
		setMeshId();
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
		final Bounds bounds = new Bounds(min.doubleValue(), max.doubleValue());
		meshes.createMeshFor(bounds);
	}

	public boolean isMeshesEnabled() {
		return this.meshesEnabled.get();
	}

	public void setMeshesEnabeld(final boolean isMeshesEnabeld) {
		this.meshesEnabled.set(isMeshesEnabeld);
	}

	public BooleanProperty meshesEnabledProperty() {
		return this.meshesEnabled;
	}

}
