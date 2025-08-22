package org.janelia.saalfeldlab.paintera.meshes;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
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
import javafx.util.Subscription;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import org.fxyz3d.shapes.polygon.PolygonMeshView;
import org.janelia.saalfeldlab.paintera.meshes.managed.GetBlockListFor;
import org.janelia.saalfeldlab.paintera.meshes.managed.GetMeshFor;
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.UUID;
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

		public BooleanProperty showBlocksProperty() {

			return showBlockBoundaries;
		}

	}

	public static class SceneUpdateParameters {

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

	private Subscription subscriptions = initUnsubscribeSubscription();

	private SceneUpdateParameters sceneUpdateParameters;

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
		this.meshesGroup = new Group();
		this.blocksGroup = new Group();
		this.root = new Group(meshesGroup);
		this.root.visibleProperty().bind(this.state.getSettings().isVisibleProperty());

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


		subscriptions = subscriptions.and(initMeshAndBlockSubscription())
				.and(initShowBlockBoundarySubscription())
				.and(initUpdateMeshesSubscription());
	}

	@NotNull
	private Subscription initUpdateMeshesSubscription() {

		return Bindings.createObjectBinding(UUID::randomUUID,
				this.state.settings.getSimplificationIterationsProperty(),
				this.state.settings.getSmoothingLambdaProperty(),
				this.state.settings.getSmoothingIterationsProperty(),
				this.state.settings.getMinLabelRatioProperty(),
				this.state.settings.getOverlapProperty()
		).subscribe(unused -> {
			synchronized (this) {
				updateMeshes(sceneUpdateParameters);
			}
		});
	}

	@NotNull
	private Subscription initUnsubscribeSubscription() {

		return () -> subscriptions = initUnsubscribeSubscription();
	}

	@NotNull
	private Subscription initMeshAndBlockSubscription() {

		final MeshAndBlockOutlineListener<T> meshAndBlockListener = new MeshAndBlockOutlineListener<>(this.state);
		this.meshesAndBlocks.addListener(meshAndBlockListener);
		return () -> this.meshesAndBlocks.removeListener(meshAndBlockListener);
	}

	@NotNull
	private Subscription initShowBlockBoundarySubscription() {

		return this.state.showBlocksProperty().subscribe(showBlockBoundary -> {
			if (showBlockBoundary)
				this.root.getChildren().add(this.blocksGroup);
			else
				this.root.getChildren().remove(this.blocksGroup);
		});
	}

	public State getState() {

		return this.state;
	}

	public boolean isInterrupted() {

		return isInterrupted.get();
	}

	private void updateSceneUpdateParameters(final BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>> sceneBlockTree, final CellGrid[] rendererGrids) {
		sceneUpdateParameters = new SceneUpdateParameters(
				sceneBlockTree,
				rendererGrids,
				this.state.settings.getSimplificationIterations(),
				this.state.settings.getSmoothingLambda(),
				this.state.settings.getSmoothingIterations(),
				this.state.settings.getMinLabelRatio(),
				this.state.settings.getOverlap());
	}



	public synchronized void updateMeshes(final SceneUpdateParameters sceneUpdateParameters) {

		updateMeshes(
				sceneUpdateParameters == null ? null : sceneUpdateParameters.sceneBlockTree,
				sceneUpdateParameters == null ? null : sceneUpdateParameters.rendererGrids
		);
	}


	public synchronized void updateMeshes(final BlockTree<BlockTreeFlatKey, BlockTreeNode<BlockTreeFlatKey>> sceneBlockTree, final CellGrid[] rendererGrids) {

		updateSceneUpdateParameters(sceneBlockTree, rendererGrids);
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

		subscriptions.unsubscribe();
		subscriptions = Subscription.EMPTY;
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

	private record MeshAndBlockOutlineListener<T>(State state) implements MapChangeListener<ShapeKey<T>, Pair<MeshView, Node>> {

		@Override
		public void onChanged(Change<? extends ShapeKey<T>, ? extends Pair<MeshView, Node>> change) {

			if (change.wasRemoved()) {
				final MeshView removedMesh = change.getValueRemoved().getA();
				if (removedMesh != null)
					unbindRemovedMesh(removedMesh);

				final Node removedBlockOutline = change.getValueRemoved().getB();
				if (removedBlockOutline != null)
					unbindRemovedBlockOutlineNode(removedBlockOutline);
			}

			if (change.wasAdded()) {
				final MeshView addedMesh = change.getValueAdded().getA();
				if (addedMesh != null)
					bindAddedMesh(addedMesh);

				final Node addedBlockOutline = change.getValueAdded().getB();
				if (addedBlockOutline != null) {
					bindAddedBlockOutlineNode(addedBlockOutline);
				}
			}
		}

		private void bindAddedBlockOutlineNode(Node addedBlockOutline) {

			final Material material;
			if (addedBlockOutline instanceof PolygonMeshView)
				material = ((PolygonMeshView)addedBlockOutline).getMaterial();
			else if (addedBlockOutline instanceof Shape3D)
				material = ((Shape3D)addedBlockOutline).getMaterial();
			else
				material = null;
			if (material instanceof PhongMaterial)
				((PhongMaterial)material).diffuseColorProperty().bind(state.premultipliedColor);
			addedBlockOutline.setDisable(true);
		}

		private void bindAddedMesh(MeshView addedMesh) {

			((PhongMaterial)addedMesh.getMaterial()).diffuseColorProperty().bind(state.premultipliedColor);
			addedMesh.drawModeProperty().bind(state.settings.getDrawModeProperty());
			addedMesh.cullFaceProperty().bind(state.settings.getCullFaceProperty());
		}

		private void unbindRemovedBlockOutlineNode(Node blockOutlineRemoved) {

			final Material material;
			if (blockOutlineRemoved instanceof PolygonMeshView)
				material = ((PolygonMeshView)blockOutlineRemoved).getMaterial();
			else if (blockOutlineRemoved instanceof Shape3D)
				material = ((Shape3D)blockOutlineRemoved).getMaterial();
			else
				material = null;
			if (material instanceof PhongMaterial)
				((PhongMaterial)material).diffuseColorProperty().unbind();
			blockOutlineRemoved.scaleXProperty().unbind();
			blockOutlineRemoved.scaleYProperty().unbind();
			blockOutlineRemoved.scaleZProperty().unbind();
		}

		private void unbindRemovedMesh(final MeshView removedMesh) {

			((PhongMaterial)removedMesh.getMaterial()).diffuseColorProperty().unbind();
			removedMesh.drawModeProperty().unbind();
			removedMesh.cullFaceProperty().unbind();
			removedMesh.scaleXProperty().unbind();
			removedMesh.scaleYProperty().unbind();
			removedMesh.scaleZProperty().unbind();
		}
	}
}
