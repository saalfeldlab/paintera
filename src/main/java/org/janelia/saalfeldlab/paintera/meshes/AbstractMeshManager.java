package org.janelia.saalfeldlab.paintera.meshes;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import net.imglib2.Interval;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.config.Viewer3DConfig;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;

/**
 * @author Philipp Hanslovsky
 * @author Igor Pisarev
 */
public abstract class AbstractMeshManager<N, T> implements MeshManager<N, T>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	protected static class BlockTreeParametersKey
	{
		public final int levelOfDetail;
		public final int coarsestScaleLevel;
		public final int finestScaleLevel;

		public BlockTreeParametersKey(
				final int levelOfDetail,
				final int coarsestScaleLevel,
				final int finestScaleLevel)
		{
			this.levelOfDetail = levelOfDetail;
			this.coarsestScaleLevel = coarsestScaleLevel;
			this.finestScaleLevel = finestScaleLevel;
		}

		@Override
		public int hashCode()
		{
			int result = levelOfDetail;
			result = 31 * result + coarsestScaleLevel;
			result = 31 * result + finestScaleLevel;
			return result;
		}

		@Override
		public boolean equals(final Object obj)
		{
			if (super.equals(obj))
				return true;
			if (obj instanceof BlockTreeParametersKey)
			{
				final BlockTreeParametersKey other = (BlockTreeParametersKey) obj;
				return
						levelOfDetail == other.levelOfDetail &&
						coarsestScaleLevel == other.coarsestScaleLevel &&
						finestScaleLevel == other.finestScaleLevel;
			}
			return false;
		}
	}

	protected static class SceneUpdateParameters
	{
		final ViewFrustum viewFrustum;
		final AffineTransform3D eyeToWorldTransform;
		final CellGrid[] rendererGrids;

		SceneUpdateParameters(
				final ViewFrustum viewFrustum,
				final AffineTransform3D eyeToWorldTransform,
				final CellGrid[] rendererGrids)
		{
			this.viewFrustum = viewFrustum;
			this.eyeToWorldTransform = eyeToWorldTransform;
			this.rendererGrids = rendererGrids;
		}
	}

	protected final DataSource<?, ?> source;

	protected final InterruptibleFunction<T, Interval[]>[] blockListCache;

	protected final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] meshCache;

	protected final Map<N, MeshGenerator<T>> neurons = Collections.synchronizedMap(new HashMap<>());

	protected final Group root = new Group();

	protected final ObjectProperty<ViewFrustum> viewFrustumProperty;

	protected final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty;

	protected final AffineTransform3D[] unshiftedWorldTransforms;

	protected final ExecutorService managers;

	protected final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> workers;

	protected final MeshSettings meshSettings;

	protected final MeshViewUpdateQueue<T> meshViewUpdateQueue;

	protected final ObjectProperty<Color> color = new SimpleObjectProperty<>(Color.WHITE);

	protected final BooleanProperty areMeshesEnabledProperty = new SimpleBooleanProperty(true);

	protected final BooleanProperty showBlockBoundariesProperty = new SimpleBooleanProperty(false);

	protected final IntegerProperty rendererBlockSizeProperty = new SimpleIntegerProperty(Viewer3DConfig.RENDERER_BLOCK_SIZE_DEFAULT_VALUE);

	protected final IntegerProperty numElementsPerFrameProperty = new SimpleIntegerProperty(Viewer3DConfig.NUM_ELEMENTS_PER_FRAME_DEFAULT_VALUE);

	protected final LongProperty frameDelayMsecProperty = new SimpleLongProperty(Viewer3DConfig.FRAME_DELAY_MSEC_DEFAULT_VALUE);

	protected final LongProperty sceneUpdateDelayMsecProperty = new SimpleLongProperty(Viewer3DConfig.SCENE_UPDATE_DELAY_MSEC_DEFAULT_VALUE);

	protected final SceneUpdateHandler sceneUpdateHandler;

	protected final InvalidationListener sceneUpdateInvalidationListener;

	protected CellGrid[] rendererGrids;

	private final ExecutorService sceneUpdateService = Executors.newSingleThreadExecutor(new NamedThreadFactory("meshmanager-sceneupdate-%d", true));

	private final ObjectProperty<SceneUpdateParameters> sceneUpdateParametersProperty = new SimpleObjectProperty<>();

	private Future<?> currentSceneUpdateTask, scheduledSceneUpdateTask;

	public AbstractMeshManager(
			final DataSource<?, ?> source,
			final InterruptibleFunction<T, Interval[]>[] blockListCache,
			final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] meshCache,
			final Group root,
			final ObjectProperty<ViewFrustum> viewFrustumProperty,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
			final MeshSettings meshSettings,
			final ExecutorService managers,
			final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> workers,
			final MeshViewUpdateQueue<T> meshViewUpdateQueue)
	{
		this.source = source;
		this.blockListCache = blockListCache;
		this.meshCache = meshCache;
		this.viewFrustumProperty = viewFrustumProperty;
		this.eyeToWorldTransformProperty = eyeToWorldTransformProperty;
		this.meshSettings = meshSettings;
		this.managers = managers;
		this.workers = workers;
		this.meshViewUpdateQueue = meshViewUpdateQueue;

		this.unshiftedWorldTransforms = DataSource.getUnshiftedWorldTransforms(source, 0);
		root.getChildren().add(this.root);

		this.sceneUpdateInvalidationListener = obs -> {
			synchronized (this)
			{
				if (currentSceneUpdateTask != null)
				{
					currentSceneUpdateTask.cancel(true);
					currentSceneUpdateTask = null;
				}
				if (scheduledSceneUpdateTask != null)
				{
					scheduledSceneUpdateTask.cancel(true);
					scheduledSceneUpdateTask = null;
				}
				sceneUpdateParametersProperty.set(null);
				update();
			}
		};

		this.viewFrustumProperty.addListener(sceneUpdateInvalidationListener);
		this.areMeshesEnabledProperty.addListener((obs, oldv, newv) -> {if (newv) update(); else removeAllMeshes();});

		this.rendererBlockSizeProperty.addListener(obs -> {
			synchronized (this)
			{
				this.rendererGrids = RendererBlockSizes.getRendererGrids(this.source, this.rendererBlockSizeProperty.get());
				update();
			}
		});

		levelOfDetailProperty().addListener(sceneUpdateInvalidationListener);
		coarsestScaleLevelProperty().addListener(sceneUpdateInvalidationListener);
		finestScaleLevelProperty().addListener(sceneUpdateInvalidationListener);

		this.sceneUpdateHandler = new SceneUpdateHandler(() -> InvokeOnJavaFXApplicationThread.invoke(this::update));
		this.sceneUpdateDelayMsecProperty.addListener(obs -> this.sceneUpdateHandler.update(this.sceneUpdateDelayMsecProperty.get()));
		this.eyeToWorldTransformProperty.addListener(this.sceneUpdateHandler);

		final InvalidationListener meshViewUpdateQueueListener = obs -> this.meshViewUpdateQueue.update(this.numElementsPerFrameProperty.get(), this.frameDelayMsecProperty.get());
		this.numElementsPerFrameProperty.addListener(meshViewUpdateQueueListener);
		this.frameDelayMsecProperty.addListener(meshViewUpdateQueueListener);
	}

	@Override
	public synchronized void update()
	{
		assert Platform.isFxApplicationThread();
		if (this.rendererGrids == null || !areMeshesEnabledProperty.get())
			return;

		final SceneUpdateParameters sceneUpdateParameters = new SceneUpdateParameters(
				viewFrustumProperty.get(),
				eyeToWorldTransformProperty.get(),
				rendererGrids
			);

		final boolean needToSubmit = sceneUpdateParametersProperty.get() == null;
		sceneUpdateParametersProperty.set(sceneUpdateParameters);
		if (needToSubmit && !managers.isShutdown())
		{
			assert scheduledSceneUpdateTask == null;
			scheduledSceneUpdateTask = sceneUpdateService.submit(withErrorPrinting(this::updateScene));
		}
	}

	private void updateScene()
	{
		assert !Platform.isFxApplicationThread();
		try
		{
			final SceneUpdateParameters sceneUpdateParameters;
			final Map<BlockTreeParametersKey, List<MeshGenerator<T>>> blockTreeParametersKeysToMeshGenerators = new HashMap<>();
			final BooleanSupplier wasInterrupted = () -> Thread.currentThread().isInterrupted();
			synchronized (this)
			{
				assert currentSceneUpdateTask == null;
				if (wasInterrupted.getAsBoolean())
					return;

				if (sceneUpdateParametersProperty.get() == null)
					return;
				sceneUpdateParameters = sceneUpdateParametersProperty.get();
				sceneUpdateParametersProperty.set(null);

				if (scheduledSceneUpdateTask == null)
					return;
				currentSceneUpdateTask = scheduledSceneUpdateTask;
				scheduledSceneUpdateTask = null;

				for (final MeshGenerator<T> meshGenerator : neurons.values())
				{
					final BlockTreeParametersKey blockTreeParametersKey = new BlockTreeParametersKey(
							meshGenerator.meshSettingsProperty().get().levelOfDetailProperty().get(),
							meshGenerator.meshSettingsProperty().get().coarsestScaleLevelProperty().get(),
							meshGenerator.meshSettingsProperty().get().finestScaleLevelProperty().get()
					);
					if (!blockTreeParametersKeysToMeshGenerators.containsKey(blockTreeParametersKey))
						blockTreeParametersKeysToMeshGenerators.put(blockTreeParametersKey, new ArrayList<>());
					blockTreeParametersKeysToMeshGenerators.get(blockTreeParametersKey).add(meshGenerator);
				}
			}

			final Map<BlockTreeParametersKey, BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>>> sceneBlockTrees = new HashMap<>();
			for (final BlockTreeParametersKey blockTreeParametersKey : blockTreeParametersKeysToMeshGenerators.keySet())
			{
				if (wasInterrupted.getAsBoolean())
					return;

				sceneBlockTrees.put(blockTreeParametersKey, SceneBlockTree.createSceneBlockTree(
						source,
						sceneUpdateParameters.viewFrustum,
						sceneUpdateParameters.eyeToWorldTransform,
						blockTreeParametersKey.levelOfDetail,
						blockTreeParametersKey.coarsestScaleLevel,
						blockTreeParametersKey.finestScaleLevel,
						sceneUpdateParameters.rendererGrids,
						wasInterrupted
				));
			}

			synchronized (this)
			{
				if (wasInterrupted.getAsBoolean())
					return;

				for (final Map.Entry<BlockTreeParametersKey, List<MeshGenerator<T>>> entry : blockTreeParametersKeysToMeshGenerators.entrySet())
				{
					final BlockTreeParametersKey blockTreeParametersKey = entry.getKey();
					final BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>> sceneBlockTreeForKey = sceneBlockTrees.get(blockTreeParametersKey);
					for (final MeshGenerator<T> meshGenerator : entry.getValue())
						meshGenerator.update(sceneBlockTreeForKey, sceneUpdateParameters.rendererGrids);
				}
			}
		}
		finally
		{
			synchronized (this)
			{
				currentSceneUpdateTask = null;
			}
		}
	}

	@Override
	public synchronized void removeMesh(final N id)
	{
		Optional.ofNullable(this.neurons.remove(id)).ifPresent(mesh -> {
			mesh.meshSettingsProperty().unbind();
			mesh.meshSettingsProperty().set(null);
			mesh.interrupt();
			root.getChildren().remove(mesh.getRoot());
		});
	}

	@Override
	public synchronized void removeAllMeshes()
	{
		getAllMeshKeys().forEach(this::removeMesh);
	}

	@Override
	public synchronized Map<N, MeshGenerator<T>> unmodifiableMeshMap()
	{
		return Collections.unmodifiableMap(neurons);
	}

	protected synchronized Collection<N> getAllMeshKeys()
	{
		return new ArrayList<>(unmodifiableMeshMap().keySet());
	}

	@Override
	public IntegerProperty levelOfDetailProperty()
	{
		return this.meshSettings.levelOfDetailProperty();
	}

	@Override
	public IntegerProperty coarsestScaleLevelProperty()
	{
		return this.meshSettings.coarsestScaleLevelProperty();
	}

	@Override
	public IntegerProperty finestScaleLevelProperty()
	{
		return this.meshSettings.finestScaleLevelProperty();
	}

	@Override
	public IntegerProperty meshSimplificationIterationsProperty()
	{
		return this.meshSettings.simplificationIterationsProperty();
	}

	@Override
	public DoubleProperty smoothingLambdaProperty()
	{
		return this.meshSettings.smoothingLambdaProperty();
	}

	@Override
	public IntegerProperty smoothingIterationsProperty()
	{
		return this.meshSettings.smoothingIterationsProperty();
	}

	@Override
	public DoubleProperty minLabelRatioProperty()
	{
		return this.meshSettings.minLabelRatioProperty();
	}

	@Override
	public DoubleProperty opacityProperty()
	{
		return this.meshSettings.opacityProperty();
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

	private static Runnable withErrorPrinting(final Runnable runnable)
	{
		return () -> {
			try {
				runnable.run();
			} catch (final RejectedExecutionException e) {
				// this happens when the application is being shut down and is normal, don't do anything
			} catch (final Throwable e) {
				e.printStackTrace();
			}
		};
	}
}
