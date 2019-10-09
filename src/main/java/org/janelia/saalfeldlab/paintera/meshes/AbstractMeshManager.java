package org.janelia.saalfeldlab.paintera.meshes;

import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
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
import org.janelia.saalfeldlab.util.concurrent.PriorityExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * @author Philipp Hanslovsky
 * @author Igor Pisarev
 */
public abstract class AbstractMeshManager<N, T> implements MeshManager<N, T>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	protected static class BlockTreeParametersKey
	{
		public final int levelOfDetail, highestScaleLevel;

		public BlockTreeParametersKey(final int levelOfDetail, final int highestScaleLevel)
		{
			this.levelOfDetail = levelOfDetail;
			this.highestScaleLevel = highestScaleLevel;
		}

		@Override
		public int hashCode()
		{
			return 31 * levelOfDetail + highestScaleLevel;
		}

		@Override
		public boolean equals(final Object obj)
		{
			if (super.equals(obj))
				return true;
			if (obj instanceof BlockTreeParametersKey)
			{
				final BlockTreeParametersKey other = (BlockTreeParametersKey) obj;
				return levelOfDetail == other.levelOfDetail && highestScaleLevel == other.highestScaleLevel;
			}
			return false;
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

	protected final PriorityExecutorService<MeshWorkerPriority> workers;

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

	protected final InvalidationListener sceneUpdateInvalidationListener = obs -> update();

	protected final Map<BlockTreeParametersKey, BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>>> sceneBlockTrees = new HashMap<>();

	protected CellGrid[] rendererGrids;

	public AbstractMeshManager(
			final DataSource<?, ?> source,
			final InterruptibleFunction<T, Interval[]>[] blockListCache,
			final InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] meshCache,
			final Group root,
			final ObjectProperty<ViewFrustum> viewFrustumProperty,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
			final MeshSettings meshSettings,
			final ExecutorService managers,
			final PriorityExecutorService<MeshWorkerPriority> workers,
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

		this.viewFrustumProperty.addListener(obs -> update());
		this.areMeshesEnabledProperty.addListener((obs, oldv, newv) -> {if (newv) update(); else removeAllMeshes();});

		this.rendererBlockSizeProperty.addListener(obs -> {
			this.rendererGrids = RendererBlockSizes.getRendererGrids(this.source, this.rendererBlockSizeProperty.get());
			update();
		});

		levelOfDetailProperty().addListener(sceneUpdateInvalidationListener);
		highestScaleLevelProperty().addListener(sceneUpdateInvalidationListener);

		this.sceneUpdateHandler = new SceneUpdateHandler(() -> InvokeOnJavaFXApplicationThread.invoke(this::update));
		this.sceneUpdateDelayMsecProperty.addListener(obs -> this.sceneUpdateHandler.update(this.sceneUpdateDelayMsecProperty.get()));
		this.eyeToWorldTransformProperty.addListener(this.sceneUpdateHandler);

		final InvalidationListener meshViewUpdateQueueListener = obs -> this.meshViewUpdateQueue.update(this.numElementsPerFrameProperty.get(), this.frameDelayMsecProperty.get());
		this.numElementsPerFrameProperty.addListener(meshViewUpdateQueueListener);
		this.frameDelayMsecProperty.addListener(meshViewUpdateQueueListener);
	}

	protected abstract void update();

	@Override
	public void removeMesh(final N id)
	{
		Optional.ofNullable(this.neurons.remove(id)).ifPresent(mesh -> {
			mesh.meshSettingsProperty().unbind();
			mesh.meshSettingsProperty().set(null);
			mesh.interrupt();
			root.getChildren().remove(mesh.getRoot());
		});
	}

	@Override
	public void removeAllMeshes()
	{
		getAllMeshKeys().forEach(this::removeMesh);
	}

	@Override
	public Map<N, MeshGenerator<T>> unmodifiableMeshMap()
	{
		return Collections.unmodifiableMap(neurons);
	}

	protected Collection<N> getAllMeshKeys()
	{
		return new ArrayList<>(unmodifiableMeshMap().keySet());
	}

	@Override
	public IntegerProperty levelOfDetailProperty()
	{
		return this.meshSettings.levelOfDetailProperty();
	}

	@Override
	public IntegerProperty highestScaleLevelProperty()
	{
		return this.meshSettings.highestScaleLevelProperty();
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
}
