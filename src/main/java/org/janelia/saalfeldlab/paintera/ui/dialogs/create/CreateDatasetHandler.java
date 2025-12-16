package org.janelia.saalfeldlab.paintera.ui.dialogs.create;

import bdv.viewer.Source;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.util.Pair;
import net.imglib2.Volatile;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.paintera.Constants;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState;
import org.janelia.saalfeldlab.paintera.state.label.n5.N5BackendLabel;
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CreateDatasetHandler {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static void createAndAddNewLabelDataset(
			final PainteraBaseView paintera,
			final Supplier<String> projectDirectory) {

		final var owner = Optional.ofNullable(paintera)
				.map(PainteraBaseView::viewer3D)
				.map(Viewer3DFX::getScene)
				.map(SubScene::getScene)
				.map(Scene::getWindow).orElse(null);
		createAndAddNewLabelDataset(paintera, projectDirectory, Exceptions.handler(Constants.NAME, "Unable to create new Dataset", null, owner));
	}

	private static void createAndAddNewLabelDataset(
			final PainteraBaseView paintera,
			final Supplier<String> projectDirectory,
			final Consumer<Exception> exceptionHandler) {

		createAndAddNewLabelDataset(
				paintera,
				projectDirectory,
				exceptionHandler,
				paintera.sourceInfo().currentSourceProperty().get(),
				paintera.sourceInfo().trackSources().toArray(Source[]::new));
	}

	public static void createAndAddNewLabelDataset(
			final PainteraBaseView pbv,
			final Supplier<String> projectDirectory,
			final Consumer<Exception> exceptionHandler,
			final Source<?> currentSource,
			final Source<?>... allSources) {

		try {
			createAndAddNewLabelDataset(pbv, projectDirectory, currentSource, allSources);
		} catch (final Exception e) {
			exceptionHandler.accept(e);
		}
	}

	private static <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & NativeType<T>> void createAndAddNewLabelDataset(
			final PainteraBaseView pbv,
			final Supplier<String> projectDirectory,
			final Source<?> currentSource,
			final Source<?>... allSources) {

		if (!pbv.isActionAllowed(MenuActionType.CreateLabelSource)) {
			LOG.debug("Creating Label Sources is disabled");
			return;
		}

		final CreateDataset cd = new CreateDataset(currentSource, Arrays.stream(allSources).map(pbv.sourceInfo()::getState).toArray(SourceState[]::new));
		final Optional<Pair<MetadataState, String>> metaAndName = cd.showDialog(projectDirectory.get());
		if (metaAndName.isPresent()) {
			final var metadataState = metaAndName.get().getKey();
			final var backend = N5BackendLabel.<D, T>createFrom( metadataState, pbv.getPropagationQueue());
			pbv.addState(new ConnectomicsLabelState<>(
					backend,
					pbv.viewer3D().getMeshesGroup(),
					pbv.viewer3D().getViewFrustumProperty(),
					pbv.viewer3D().getEyeToWorldTransformProperty(),
					pbv.getMeshWorkerExecutorService(),
					pbv.getQueue(),
					0,
					metaAndName.get().getValue(),
					null));
		}
	}
}
