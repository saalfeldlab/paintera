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
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.control.actions.OpenSourceModel;
import org.janelia.saalfeldlab.paintera.control.actions.SourceType;
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority;
import org.janelia.saalfeldlab.paintera.state.SourceState;
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
import java.util.concurrent.ExecutorService;

import static org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread.invoke;

public class N5OpenSourceHelper {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static void addSource(
			final SourceType type,
			final OpenSourceModel model,
			final PainteraBaseView viewer) throws Exception {

		LOG.debug("Type={}", type);
		switch (type) {
		case RAW:
			LOG.trace("Adding raw data");
			addRaw(model, viewer);
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
			final OpenSourceModel model,
			PainteraBaseView viewer) {

		final SourceState<T, V> raw = getRaw(model, viewer.getQueue(), viewer.getQueue().getNumPriorities() - 1);
		LOG.debug("Got raw: {}", raw);
		invoke(() -> viewer.addState(raw)).join();
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
		invoke(() -> viewer.addState(rep)).join();
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
		if (state.getChannels() != null && model.getActiveChannels() != null)
			state.getChannels().setActiveChannels(model.getActiveChannels());
		return state;
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
