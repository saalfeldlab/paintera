package org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5;

import net.imglib2.Volatile;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.OpenSourceState;
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.meta.MetaPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Objects;

import static org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread.invoke;

public class N5OpenSourceHelper {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	static void addSource(
			final MetaPanel.TYPE type,
			final OpenSourceState openSourceState,
			final int[] channelSelection,
			final PainteraBaseView viewer) throws Exception {

		LOG.debug("Type={}", type);
		switch (type) {
		case RAW:
			LOG.trace("Adding raw data");
			addRaw(channelSelection, openSourceState, viewer);
			break;
		case LABEL:
			LOG.trace("Adding label data");
			addLabel(openSourceState, viewer);
			break;
		default:
			break;
		}
	}

	private static <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>> void
	addRaw(
			final int[] channelSelection,
			final OpenSourceState openSourceState,
			PainteraBaseView viewer) {

		final DatasetAttributes attributes = Objects.requireNonNull(openSourceState.getDatasetAttributes());
		if (attributes.getNumDimensions() == 4) {
			LOG.debug("4-dimensional data, assuming channel index at {}", 3);
			final var channels = OpenSourceState.getChannels(openSourceState, channelSelection, viewer.getQueue(), viewer.getQueue().getNumPriorities() - 1);
			LOG.debug("Got {} channel sources", channels.size());
			invoke(() -> channels.forEach(viewer::addState));
			LOG.debug("Added {} channel sources", channels.size());
		} else {
			final SourceState<T, V> raw = OpenSourceState.getRaw(openSourceState, viewer.getQueue(), viewer.getQueue().getNumPriorities() - 1);
			LOG.debug("Got raw: {}", raw);
			invoke(() -> viewer.addState(raw));
		}
	}

	private static <D extends NativeType<D> & IntegerType<D>, T extends Volatile<D> & NativeType<T>> void addLabel(
			final OpenSourceState openSourceState,
			final PainteraBaseView viewer) throws Exception {

		final SourceState<D, T> rep = OpenSourceState.getLabels(
				openSourceState,
				viewer.getQueue(),
				viewer.getQueue().getNumPriorities() - 1,
				viewer.viewer3D().getMeshesGroup(),
				viewer.viewer3D().getViewFrustumProperty(),
				viewer.viewer3D().getEyeToWorldTransformProperty(),
				viewer.getMeshManagerExecutorService(),
				viewer.getMeshWorkerExecutorService(),
				viewer.getPropagationQueue()
		);
		invoke(() -> viewer.addState(rep));
	}
}
