package org.janelia.saalfeldlab.paintera.meshes;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.property.ObjectProperty;
import javafx.scene.Group;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.imglib2.util.Triple;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.util.Colors;
import org.janelia.saalfeldlab.util.concurrent.PriorityExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * @author Philipp Hanslovsky
 * @author Igor Pisarev
 */
public class MeshManagerSimple<N, T> extends AbstractMeshManager<N, T>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final Function<N, long[]> getIds;

	private final Function<N, T> idToMeshId;

	public MeshManagerSimple(
			final DataSource<?, ?> source,
			final InterruptibleFunction<T, Interval[]>[] blockListCache,
			final InterruptibleFunction<ShapeKey<T>, Triple<float[], float[], int[]>>[] meshCache,
			final Group root,
			final ObjectProperty<ViewFrustum> viewFrustumProperty,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
			final MeshSettings meshSettings,
			final ExecutorService managers,
			final PriorityExecutorService<MeshWorkerPriority> workers,
			final Function<N, long[]> getIds,
			final Function<N, T> idToMeshId)
	{
		super(
				source,
				blockListCache,
				meshCache,
				root,
				viewFrustumProperty,
				eyeToWorldTransformProperty,
				meshSettings,
				managers,
				workers,
				new MeshViewUpdateQueue<>()
			);

		this.getIds = getIds;
		this.idToMeshId = idToMeshId;
	}

	@Override
	public void update()
	{
		if (rendererGrids == null)
			return;

		final BlockTreeParametersKey blockTreeParametersKey = new BlockTreeParametersKey(
				levelOfDetailProperty().get(),
				highestScaleLevelProperty().get()
			);

		this.sceneBlockTrees.clear();
		this.sceneBlockTrees.put(blockTreeParametersKey, SceneBlockTree.createSceneBlockTree(
				source,
				viewFrustumProperty.get(),
				eyeToWorldTransformProperty.get(),
				levelOfDetailProperty().get(),
				highestScaleLevelProperty().get(),
				rendererGrids
			));

		if (this.areMeshesEnabledProperty.get())
			unmodifiableMeshMap().keySet().forEach(this::generateMesh);
	}

	@Override
	public void generateMesh(final N id)
	{
		if (!neurons.containsKey(id))
		{
			LOG.debug("Adding mesh for segment {} (composed of ids={}).", id, getIds.apply(id));

			final IntegerBinding color = Bindings.createIntegerBinding(
					() -> Colors.toARGBType(this.color.get()).get(),
					this.color
				);

			final MeshGenerator<T> meshGenerator = new MeshGenerator<>(
					source,
					idToMeshId.apply(id),
					blockListCache,
					meshCache,
					meshViewUpdateQueue,
					color,
					viewFrustumProperty,
					eyeToWorldTransformProperty,
					unshiftedWorldTransforms,
					managers,
					workers,
					showBlockBoundariesProperty
				);

			meshGenerator.meshSettingsProperty().set(meshSettings);
			neurons.put(id, meshGenerator);
			root.getChildren().add(meshGenerator.getRoot());
		}

		final BlockTreeParametersKey blockTreeParametersKey = new BlockTreeParametersKey(
				levelOfDetailProperty().get(),
				highestScaleLevelProperty().get()
			);

		neurons.get(id).update(sceneBlockTrees.get(blockTreeParametersKey), rendererGrids);
	}

	@Override
	public void refreshMeshes()
	{
		update();
	}

	@Override
	public long[] containedFragments(final N id)
	{
		return getIds.apply(id);
	}

	@Override
	public ManagedMeshSettings managedMeshSettings()
	{
		throw new UnsupportedOperationException("not implemented yet");
	}
}
