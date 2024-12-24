package org.janelia.saalfeldlab.paintera.meshes;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.Material;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Shape3D;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import org.fxyz3d.shapes.polygon.PolygonMeshView;
import org.janelia.saalfeldlab.paintera.meshes.managed.GetBlockListFor;
import org.janelia.saalfeldlab.paintera.meshes.managed.GetMeshFor;
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;

/**
 * @author Philipp Hanslovsky
 * @author Igor Pisarev
 */
public class MeshGenerator<T> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final class State {

		// TODO what to use for numScaleLevels? Should MeshSettings not know about the number of scale levels?
		private final MeshSettings settings = new MeshSettings(Integer.MAX_VALUE);
		private final IndividualMeshProgressState progress = new IndividualMeshProgressState();
		private final ObjectProperty<Color> color = new SimpleObjectProperty<>(Color.WHITE);
		private final BooleanProperty showBlockBoundaries = new SimpleBooleanProperty(false);
		private final ObjectBinding<Color> premultipliedColor = Bindings.createObjectBinding(
				() -> getColor().deriveColor(0.0, 1.0, 1.0, settings.getOpacity()),
				color,
				settings.getOpacityProperty());

		public MeshSettings getSettings() {

			return settings;
		}

		public IndividualMeshProgressState getProgress() {

			return progress;
		}

		public ObjectProperty<Color> colorProperty() {

			return color;
		}

		public Color getColor() {

			return color.get();
		}

		public void setColor(final Color color) {

			this.color.set(color);
		}

		public BooleanProperty showBlockBoundariesProperty() {

			return showBlockBoundaries;
		}

		public boolean isShowBlockBoundaries() {

			return showBlockBoundaries.get();
		}

		public void setShowBlockBoundaries(final boolean isShowBlockBoundaries) {

			showBlockBoundaries.set(isShowBlockBoundaries);
		}
	}

	private static class SceneUpdateParameters {

		final BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>> sceneBlockTree;
		final CellGrid[] rendererGrids;
		final int meshSimplificationIterations;
		final double smoothingLambda;
		final int smoothingIterations;
		final double minLabelRatio;
		final boolean overlap;

		SceneUpdateParameters(
				final BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>> sceneBlockTree,
				final CellGrid[] rendererGrids,
				final int meshSimplificationIterations,
				final double smoothingLambda,
				final int smoothingIterations,
				final double minLabelRatio,
				final boolean overlap) {

			this.sceneBlockTree = sceneBlockTree;
			this.rendererGrids = rendererGrids;
			this.meshSimplificationIterations = meshSimplificationIterations;
			this.smoothingLambda = smoothingLambda;
			this.smoothingIterations = smoothingIterations;
			this.minLabelRatio = minLabelRatio;
			this.overlap = overlap;
		}
	}

	private final T id;

	private final ObservableMap<ShapeKey<T>, Pair<MeshView, Node>> meshesAndBlocks = FXCollections.observableHashMap();

	private final Group root;

	private final Group meshesGroup;

	private final Group blocksGroup;

	private final MeshGeneratorJobManager<T> manager;

	private final State state;

	private final AtomicBoolean isInterrupted = new AtomicBoolean();

	private final InvalidationListener updateInvalidationListener;

	private SceneUpdateParameters sceneUpdateParameters;

	private final ChangeListener<Boolean> showBlockBoundariesListener;

	public MeshGenerator(
			final int numScaleLevels,
			final T segmentId,
			final GetBlockListFor<T> getBlockLists,
			final GetMeshFor<T> getMeshes,
			final MeshViewUpdateQueue<T> meshViewUpdateQueue,
			final IntFunction<AffineTransform3D> unshiftedWorldTransforms,
			final ExecutorService managers,
			final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> workers) {

		this(
				numScaleLevels,
				segmentId,
				getBlockLists,
				getMeshes,
				meshViewUpdateQueue,
				unshiftedWorldTransforms,
				managers,
				workers,
				new State());
	}

	public MeshGenerator(
			final int numScaleLevels,
			final T segmentId,
			final GetBlockListFor<T> getBlockLists,
			final GetMeshFor<T> getMeshes,
			final MeshViewUpdateQueue<T> meshViewUpdateQueue,
			final IntFunction<AffineTransform3D> unshiftedWorldTransforms,
			final ExecutorService managers,
			final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> workers,
			final State state) {

		super();
		this.state = state;
		this.id = segmentId;

		this.updateInvalidationListener = obs -> {
			synchronized (this) {
				sceneUpdateParameters = new SceneUpdateParameters(
						sceneUpdateParameters != null ? sceneUpdateParameters.sceneBlockTree : null,
						sceneUpdateParameters != null ? sceneUpdateParameters.rendererGrids : null,
						this.state.settings.getSimplificationIterations(),
						this.state.settings.getSmoothingLambda(),
						this.state.settings.getSmoothingIterations(),
						this.state.settings.getMinLabelRatio(),
						this.state.settings.getOverlap()
				);
				updateMeshes();
			}
		};

		this.state.settings.getSimplificationIterationsProperty().addListener(updateInvalidationListener);
		this.state.settings.getSmoothingLambdaProperty().addListener(updateInvalidationListener);
		this.state.settings.getSmoothingIterationsProperty().addListener(updateInvalidationListener);
		this.state.settings.getMinLabelRatioProperty().addListener(updateInvalidationListener);
		this.state.settings.getOverlapProperty().addListener(updateInvalidationListener);

		// initialize
		updateInvalidationListener.invalidated(null);

		this.meshesGroup = new Group();
		this.blocksGroup = new Group();
		this.root = new Group(meshesGroup);

		this.showBlockBoundariesListener = (obs, oldv, newv) -> {
			if (newv)
				this.root.getChildren().add(this.blocksGroup);
			else
				this.root.getChildren().remove(this.blocksGroup);
		};

		this.root.visibleProperty().bind(this.state.getSettings().isVisibleProperty());

		this.state.showBlockBoundaries.addListener(showBlockBoundariesListener);
		showBlockBoundariesListener.changed(null, null, this.state.isShowBlockBoundaries());

		this.manager = new MeshGeneratorJobManager<>(
				numScaleLevels,
				id,
				meshesAndBlocks,
				new ValuePair<>(meshesGroup, blocksGroup),
				meshViewUpdateQueue,
				getBlockLists,
				getMeshes,
				unshiftedWorldTransforms,
				managers,
				workers,
				state.progress);

		this.meshesAndBlocks.addListener((MapChangeListener<ShapeKey<T>, Pair<MeshView, Node>>) change ->
		{
			if (change.wasRemoved()) {
				if (change.getValueRemoved().getA() != null) {
					final MeshView meshRemoved = change.getValueRemoved().getA();
					((PhongMaterial) meshRemoved.getMaterial()).diffuseColorProperty().unbind();
					meshRemoved.drawModeProperty().unbind();
					meshRemoved.cullFaceProperty().unbind();
					meshRemoved.scaleXProperty().unbind();
					meshRemoved.scaleYProperty().unbind();
					meshRemoved.scaleZProperty().unbind();
				}

				if (change.getValueRemoved().getB() != null) {
					final Node blockOutlineRemoved = change.getValueRemoved().getB();
					final Material material;
					if (blockOutlineRemoved instanceof PolygonMeshView)
						material = ((PolygonMeshView) blockOutlineRemoved).getMaterial();
					else if (blockOutlineRemoved instanceof Shape3D)
						material = ((Shape3D) blockOutlineRemoved).getMaterial();
					else
						material = null;
					if (material instanceof PhongMaterial)
						((PhongMaterial) material).diffuseColorProperty().unbind();
					blockOutlineRemoved.scaleXProperty().unbind();
					blockOutlineRemoved.scaleYProperty().unbind();
					blockOutlineRemoved.scaleZProperty().unbind();
				}
			}

			if (change.wasAdded()) {
				if (change.getValueAdded().getA() != null) {
					final MeshView meshAdded = change.getValueAdded().getA();
					((PhongMaterial) meshAdded.getMaterial()).diffuseColorProperty().bind(this.state.premultipliedColor);
					meshAdded.drawModeProperty().bind(this.state.settings.getDrawModeProperty());
					meshAdded.cullFaceProperty().bind(this.state.settings.getCullFaceProperty());
				}

				if (change.getValueAdded().getB() != null) {
					final Node blockOutlineAdded = change.getValueAdded().getB();
					final Material material;
					if (blockOutlineAdded instanceof PolygonMeshView)
						material = ((PolygonMeshView) blockOutlineAdded).getMaterial();
					else if (blockOutlineAdded instanceof Shape3D)
						material = ((Shape3D) blockOutlineAdded).getMaterial();
					else
						material = null;
					if (material instanceof PhongMaterial)
						((PhongMaterial) material).diffuseColorProperty().bind(this.state.premultipliedColor);
					blockOutlineAdded.setDisable(true);
				}
			}
		});

	}

	public State getState() {

		return this.state;
	}

	public boolean isInterrupted() {

		return isInterrupted.get();
	}

	public synchronized void update(final BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>> sceneBlockTree, final CellGrid[] rendererGrids) {

		sceneUpdateParameters = new SceneUpdateParameters(
				sceneBlockTree,
				rendererGrids,
				sceneUpdateParameters.meshSimplificationIterations,
				sceneUpdateParameters.smoothingLambda,
				sceneUpdateParameters.smoothingIterations,
				sceneUpdateParameters.minLabelRatio,
				sceneUpdateParameters.overlap);
		updateMeshes();
	}

	public synchronized void interrupt() {

		if (isInterrupted.get()) {
			LOG.debug("MeshGenerator for {} has already been interrupted", id);
			return;
		}

		LOG.debug("Interrupting rendering tasks for {}", id);
		isInterrupted.set(true);

		manager.interrupt();

		this.state.settings.getSimplificationIterationsProperty().removeListener(updateInvalidationListener);
		this.state.settings.getSmoothingLambdaProperty().removeListener(updateInvalidationListener);
		this.state.settings.getSmoothingIterationsProperty().removeListener(updateInvalidationListener);
		this.state.settings.getMinLabelRatioProperty().removeListener(updateInvalidationListener);
		this.state.showBlockBoundariesProperty().removeListener(showBlockBoundariesListener);
	}

	private synchronized void updateMeshes() {

		if (isInterrupted.get()) {
			LOG.debug("MeshGenerator for {} has been interrupted, ignoring update request", id);
			return;
		}

		if (sceneUpdateParameters.sceneBlockTree == null || sceneUpdateParameters.rendererGrids == null) {
			LOG.debug("Block tree for {} is not initialized yet", id);
			return;
		}

		manager.submit(
				sceneUpdateParameters.sceneBlockTree,
				sceneUpdateParameters.rendererGrids,
				sceneUpdateParameters.meshSimplificationIterations,
				sceneUpdateParameters.smoothingLambda,
				sceneUpdateParameters.smoothingIterations,
				sceneUpdateParameters.minLabelRatio,
				sceneUpdateParameters.overlap);
	}

	public T getId() {

		return id;
	}

	public Node getRoot() {

		return this.root;
	}
}
