package org.janelia.saalfeldlab.paintera.ui.dialogs.create;

import bdv.viewer.Source;
import com.pivovarit.function.ThrowingFunction;
import gnu.trove.set.hash.TLongHashSet;
import javafx.util.Pair;
import net.imglib2.Interval;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetType;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.Masks;
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5;
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5FSMeta;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerWithAssignmentForSegments;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState;
import org.janelia.saalfeldlab.paintera.state.label.n5.N5Backend;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.ui.opendialog.menu.OpenDialogMenuEntry;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class CreateDatasetHandler
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	// TODO how to get this to work?
	// TODO baseView.allowedActionsProperty().get().isAllowed(MenuActionType.CreateNewLabelSource)
	// TODO should this even be in menu?
	@Plugin(type = OpenDialogMenuEntry.class, menuPath = "_Create>_Label (N5)")
	public static class CreateDatasetMenuEntry implements OpenDialogMenuEntry {

		@Override
		public BiConsumer<PainteraBaseView, Supplier<String>> onAction() {
			return CreateDatasetHandler::createAndAddNewLabelDataset;
		}
	}

	public static void createAndAddNewLabelDataset(
			final PainteraBaseView paintera,
			final Supplier<String> projectDirectory) {
		createAndAddNewLabelDataset(paintera, projectDirectory, Exceptions.handler("Paintera", "Unable to create new Dataset"));
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
				paintera.sourceInfo().trackSources().stream().toArray(Source[]::new));
	}

	public static void createAndAddNewLabelDataset(
			final PainteraBaseView pbv,
			final Supplier<String> projecDirectory,
			final Consumer<Exception> exceptionHandler,
			final Source<?> currentSource,
			final Source<?>... allSources )
	{
		try {
			createAndAddNewLabelDataset(pbv, projecDirectory, currentSource, allSources);
		} catch (final Exception e)
		{
			exceptionHandler.accept(e);
		}
	}


	private static void createAndAddNewLabelDataset(
			final PainteraBaseView pbv,
			final Supplier<String> projecDirectory,
			final Source<?> currentSource,
			final Source<?>... allSources) throws IOException {
		final CreateDataset cd = new CreateDataset(currentSource, Arrays.stream(allSources).map(pbv.sourceInfo()::getState).toArray(SourceState[]::new));
		final Optional<Pair<N5FSMeta, String>> metaAndName = cd.showDialog();
		if (metaAndName.isPresent())
		{
			final N5FSMeta meta = metaAndName.get().getKey();
			final N5Backend backend = N5Backend.createFrom(
					meta.getWriter(),
					meta.getDataset(),
					pbv.getQueue(),
					0,
					metaAndName.get().getValue(),
					projecDirectory,
					pbv.getPropagationQueue(),
					N5Helpers.getResolution(meta.getWriter(), String.format("%s/data", meta.getDataset())),
					N5Helpers.getOffset(meta.getWriter(), String.format("%s/data", meta.getDataset())));
			pbv.addState(new ConnectomicsLabelState<>(
					backend,
					pbv.viewer3D().meshesGroup(),
					pbv.getMeshManagerExecutorService(),
					pbv.getMeshWorkerExecutorService()));
		}
	}
}
