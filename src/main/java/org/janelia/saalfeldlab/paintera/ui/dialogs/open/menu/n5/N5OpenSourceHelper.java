package org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5;

import bdv.cache.SharedQueue;
import javafx.beans.property.ObjectProperty;
import javafx.scene.Group;
import net.imglib2.Volatile;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.view.composite.RealComposite;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.control.actions.OpenSourceModel;
import org.janelia.saalfeldlab.paintera.control.actions.SourceType;
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet;
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.channel.ConnectomicsChannelState;
import org.janelia.saalfeldlab.paintera.state.channel.n5.N5BackendChannel;
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState;
import org.janelia.saalfeldlab.paintera.state.label.n5.N5BackendLabel;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState;
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState;
import org.janelia.saalfeldlab.paintera.state.raw.n5.N5BackendRaw;
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum;
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import static org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread.invoke;

public class N5OpenSourceHelper {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static void addSource(
			final SourceType type,
			final OpenSourceModel model,
			final int[] channelSelection,
			final PainteraBaseView viewer) throws Exception {

		LOG.debug("Type={}", type);
		switch (type) {
		case RAW:
			LOG.trace("Adding raw data");
			addRaw(channelSelection, model, viewer);
			break;
		case LABEL:
			LOG.trace("Adding label data");
			addLabel(model, viewer);
			break;
		default:
			break;
		}
	}

	private static <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>> void
	addRaw(
			final int[] channelSelection,
			final OpenSourceModel model,
			PainteraBaseView viewer) {

		final DatasetAttributes attributes = Objects.requireNonNull(model.getMetadataState()).getDatasetAttributes();
		if (attributes.getNumDimensions() == 4) {
			LOG.debug("4-dimensional data, assuming channel index at {}", 3);
			final var channels = getChannels(model, channelSelection, viewer.getQueue(), viewer.getQueue().getNumPriorities() - 1);
			LOG.debug("Got {} channel sources", channels.size());
			invoke(() -> channels.forEach(viewer::addState));
			LOG.debug("Added {} channel sources", channels.size());
		} else {
			final SourceState<T, V> raw = getRaw(model, viewer.getQueue(), viewer.getQueue().getNumPriorities() - 1);
			LOG.debug("Got raw: {}", raw);
			invoke(() -> viewer.addState(raw));
		}
	}

	private static <D extends NativeType<D> & IntegerType<D>, T extends Volatile<D> & NativeType<T>> void addLabel(
			final OpenSourceModel model,
			final PainteraBaseView viewer) {

		final SourceState<D, T> rep = getLabels(
				model,
				viewer.getQueue(),
				viewer.getQueue().getNumPriorities() - 1,
				viewer.viewer3D().getMeshesGroup(),
				viewer.viewer3D().getViewFrustumProperty(),
				viewer.viewer3D().getEyeToWorldTransformProperty(),
				viewer.getMeshWorkerExecutorService(),
				viewer.getPropagationQueue()
		);
		invoke(() -> viewer.addState(rep));
	}

	public static <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>>
	SourceState<T, V> getRaw(
			final OpenSourceModel model,
			final SharedQueue sharedQueue,
			final int priority) {

		final MetadataState metadataState = model.getMetadataState().copy();
		final var backend = new N5BackendRaw<T, V>(metadataState);
		final var state = new ConnectomicsRawState<>(backend, sharedQueue, priority, model.getSourceName());
		state.converter().setMin(metadataState.getMinIntensity());
		state.converter().setMax(metadataState.getMaxIntensity());
		return state;
	}

	public static <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>>
	List<SourceState<RealComposite<T>, VolatileWithSet<RealComposite<V>>>> getChannels(
			final OpenSourceModel model,
			final int[] channelSelection,
			final SharedQueue sharedQueue,
			final int priority) {

		final MetadataState metadataState = model.getMetadataState().copy();
		/* we are explicitly not opening a label source */
		metadataState.setLabel(false);

		int channelIdx = 3;
		for (int i = 0; i < metadataState.getAxes().length; i++) {
			if (metadataState.getAxes()[i].getType().equals(Axis.CHANNEL)) {
				channelIdx = i;
				break;
			}
		}
		final var backend = new N5BackendChannel<T, V>(metadataState, channelSelection, channelIdx);
		final var state = new ConnectomicsChannelState<>(backend, sharedQueue, priority, model.getSourceName());
		state.converter().setMins(_ -> metadataState.getMinIntensity());
		state.converter().setMaxs(_ -> metadataState.getMaxIntensity());
		return List.of(state);
	}


	public static <T extends IntegerType<T> & NativeType<T>, V extends Volatile<T> & NativeType<V>>
	SourceState<T, V> getLabels(
			final OpenSourceModel model,
			final SharedQueue sharedQueue,
			final int priority,
			final Group meshesGroup,
			final ObjectProperty<ViewFrustum> viewFrustumProperty,
			final ObjectProperty<AffineTransform3D> eyeToWorldTransformProperty,
			final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> workers,
			final ExecutorService propagationQueue) {

		final MetadataState metadataState = model.getMetadataState().copy();
		if (metadataState.getDatasetAttributes().getNumDimensions() > 3) {
			metadataState.setN5ContainerState(metadataState.getN5ContainerState().readOnlyCopy());
			/* we are explicitly opening a label source */
			metadataState.setLabel(true);
		}

		final N5BackendLabel<T, V> backend = N5BackendLabel.createFrom(metadataState, propagationQueue);
		return new ConnectomicsLabelState<>(
				backend,
				meshesGroup,
				viewFrustumProperty,
				eyeToWorldTransformProperty,
				workers,
				sharedQueue,
				priority,
				model.getSourceName(),
				null
		);
	}
}
