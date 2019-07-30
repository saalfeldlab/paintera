package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import org.janelia.saalfeldlab.fx.ObservableWithListenersList;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.util.Colors;
import org.janelia.saalfeldlab.util.concurrent.PriorityExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.beans.value.ObservableIntegerValue;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;

/**
 * @author Philipp Hanslovsky
 */
public class MeshManagerSimple<N, T> extends ObservableWithListenersList implements MeshManager<N, T>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final long updateIntervalWhileNavigatingMsec = 5000; // 5 sec

	private static final long updateDelayAfterNavigatingMsec = 500; // 0.5 sec

	private final DataSource<?, ?> source;

	private final InterruptibleFunction<T, Interval[]>[] blockListCache;

	private final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] meshCache;

	private final Map<N, MeshGenerator<T>> neurons = Collections.synchronizedMap(new HashMap<>());

	private final Group root;

	private final ObjectProperty<ViewFrustum> viewFrustumProperty;

	private final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty;

	private final IntegerProperty meshSimplificationIterations = new SimpleIntegerProperty();

	private final DoubleProperty smoothingLambda = new SimpleDoubleProperty();

	private final IntegerProperty smoothingIterations = new SimpleIntegerProperty();

	private final IntegerProperty preferredScaleLevel = new SimpleIntegerProperty();

	private final IntegerProperty highestScaleLevel = new SimpleIntegerProperty();

	private final ExecutorService managers;

	private final PriorityExecutorService workers;

	private final ObjectProperty<Color> color = new SimpleObjectProperty<>(Color.WHITE);

	private final DoubleProperty opacity = new SimpleDoubleProperty(1.0);

	private final Function<N, long[]> getIds;

	private final Function<N, T> idToMeshId;

	private final BooleanProperty areMeshesEnabled = new SimpleBooleanProperty(true);

	private final BooleanProperty showBlockBoundaries = new SimpleBooleanProperty(false);

	private final IntegerProperty rendererBlockSize = new SimpleIntegerProperty(64);

	public MeshManagerSimple(
			final DataSource<?, ?> source,
			final InterruptibleFunction<T, Interval[]>[] blockListCache,
			final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] meshCache,
			final Group root,
			final ObjectProperty<ViewFrustum> viewFrustumProperty,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
			final ObservableIntegerValue preferredScaleLevel,
			final ObservableIntegerValue highestScaleLevel,
			final ObservableIntegerValue meshSimplificationIterations,
			final ObservableDoubleValue smoothingLambda,
			final ObservableIntegerValue smoothingIterations,
			final ExecutorService managers,
			final PriorityExecutorService workers,
			final Function<N, long[]> getIds,
			final Function<N, T> idToMeshId)
	{
		super();
		this.source = source;
		this.blockListCache = blockListCache;
		this.meshCache = meshCache;
		this.root = root;
		this.viewFrustumProperty = viewFrustumProperty;
		this.eyeToWorldTransformProperty = eyeToWorldTransformProperty;
		this.getIds = getIds;
		this.idToMeshId = idToMeshId;

		this.preferredScaleLevel.set(Math.min(Math.max(preferredScaleLevel.get(), 0), source.getNumMipmapLevels() - 1));
		preferredScaleLevel.addListener((obs, oldv, newv) -> {
			this.preferredScaleLevel.set(Math.min(Math.max(newv.intValue(), 0), source.getNumMipmapLevels() - 1));
		});

		this.highestScaleLevel.set(Math.min(Math.max(highestScaleLevel.get(), 0), source.getNumMipmapLevels() - 1));
		highestScaleLevel.addListener((obs, oldv, newv) -> {
			this.highestScaleLevel.set(Math.min(Math.max(newv.intValue(), 0), source.getNumMipmapLevels() - 1));
		});

		this.meshSimplificationIterations.set(Math.max(meshSimplificationIterations.get(), 0));
		meshSimplificationIterations.addListener((obs, oldv, newv) -> {
			this.meshSimplificationIterations.set(Math.max(newv.intValue(), 0));
		});

		this.smoothingLambda.set(Math.min(Math.max(smoothingLambda.get(), 0), 1.0));
		smoothingLambda.addListener((obs, oldv, newv) -> {
			this.smoothingLambda.set(Math.min(Math.max(newv.doubleValue(), 0), 1.0));
		});

		this.smoothingIterations.set(Math.max(smoothingIterations.get(), 0));
		smoothingIterations.addListener((obs, oldv, newv) -> {
			this.smoothingIterations.set(Math.max(newv.intValue(), 0));
		});

		this.areMeshesEnabled.addListener((obs, oldv, newv) -> {if (newv) update(); else removeAllMeshes();});

		this.managers = managers;
		this.workers = workers;

		this.rendererBlockSize.addListener(obs -> {
			final Collection<N> keysCopy = getAllMeshKeys();
			removeAllMeshes();
			keysCopy.forEach(this::generateMesh);
		});

		this.viewFrustumProperty.addListener(obs -> update());

		this.eyeToWorldTransformProperty.addListener(new SceneUpdateHandler(
				updateIntervalWhileNavigatingMsec,
				updateDelayAfterNavigatingMsec,
				() -> InvokeOnJavaFXApplicationThread.invoke(this::update)
			));
	}

	private void update()
	{
		if (this.areMeshesEnabled.get())
			unmodifiableMeshMap().values().forEach(MeshGenerator::update);
	}

	@Override
	public void generateMesh(final N id)
	{
		final IntegerBinding color = Bindings.createIntegerBinding(
				() -> Colors.toARGBType(this.color.get()).get(),
				this.color
			);

		if (neurons.containsKey(id))
			return;

		LOG.debug("Adding mesh for segment {} (composed of ids={}).", id, getIds.apply(id));
		final MeshGenerator<T> nfx = new MeshGenerator<>(
				source,
				idToMeshId.apply(id),
				blockListCache,
				meshCache,
				color,
				viewFrustumProperty,
				eyeToWorldTransformProperty,
				preferredScaleLevel.get(),
				highestScaleLevel.get(),
				meshSimplificationIterations.get(),
				smoothingLambda.get(),
				smoothingIterations.get(),
				rendererBlockSize.get(),
				managers,
				workers,
				showBlockBoundaries
		);

		nfx.opacityProperty().bind(this.opacity);
		nfx.preferredScaleLevelProperty().bind(this.preferredScaleLevel);
		nfx.highestScaleLevelProperty().bind(this.highestScaleLevel);
		nfx.meshSimplificationIterationsProperty().bind(this.meshSimplificationIterations);
		nfx.smoothingIterationsProperty().bind(this.smoothingIterations);
		nfx.smoothingLambdaProperty().bind(this.smoothingLambda);

		neurons.put(id, nfx);
		root.getChildren().add(nfx.getRoot());

		nfx.update();

	}

	@Override
	public void removeMesh(final N id)
	{
		Optional.ofNullable(this.neurons.remove(id)).ifPresent(mesh -> {
			mesh.unbind();
			mesh.interrupt();
			root.getChildren().remove(mesh.getRoot());
		});
	}

	@Override
	public Map<N, MeshGenerator<T>> unmodifiableMeshMap()
	{
		return Collections.unmodifiableMap(neurons);
	}

	private Collection<N> getAllMeshKeys()
	{
		return new ArrayList<>(unmodifiableMeshMap().keySet());
	}

	@Override
	public IntegerProperty preferredScaleLevelProperty()
	{
		return this.preferredScaleLevel;
	}

	@Override
	public IntegerProperty highestScaleLevelProperty()
	{
		return this.highestScaleLevel;
	}

	@Override
	public IntegerProperty meshSimplificationIterationsProperty()
	{
		return this.meshSimplificationIterations;
	}

	@Override
	public DoubleProperty smoothingLambdaProperty()
	{
		return this.smoothingLambda;
	}

	@Override
	public IntegerProperty smoothingIterationsProperty()
	{
		return this.smoothingIterations;
	}

	@Override
	public void removeAllMeshes()
	{
		getAllMeshKeys().forEach(this::removeMesh);
	}

	@Override
	public InterruptibleFunction<T, Interval[]>[] blockListCache()
	{
		return blockListCache;
	}

	@Override
	public InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] meshCache()
	{
		return meshCache;
	}

	public ObjectProperty<Color> colorProperty()
	{
		return this.color;
	}

	@Override
	public DoubleProperty opacityProperty()
	{
		return this.opacity;
	}

	@Override
	public long[] containedFragments(final N id)
	{
		return getIds.apply(id);
	}

	@Override
	public void refreshMeshes()
	{
		update();
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
	public ManagedMeshSettings managedMeshSettings()
	{
		throw new UnsupportedOperationException("not implemented yet");
	}

}

