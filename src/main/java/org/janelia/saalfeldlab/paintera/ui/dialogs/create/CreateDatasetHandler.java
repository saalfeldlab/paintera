package org.janelia.saalfeldlab.paintera.ui.dialogs.create;

import bdv.viewer.Source;
import javafx.util.Pair;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetType;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.paintera.Paintera;
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
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerWithAssignmentForSegments;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
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
		createAndAddNewLabelDataset(paintera, projectDirectory, Exceptions.handler(Paintera.NAME, "Unable to create new Dataset"));
	}

	public static void createAndAddNewLabelDataset(
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


	public static void createAndAddNewLabelDataset(
			final PainteraBaseView pbv,
			final Supplier<String> projecDirectory,
			final Source<?> currentSource,
			final Source<?>... allSources) throws IOException {
		final CreateDataset                    cd          = new CreateDataset(currentSource, Arrays.stream(allSources).map(pbv.sourceInfo()::getState).toArray(SourceState[]::new));
		final Optional<Pair<N5FSMeta, String>> metaAndName = cd.showDialog();
		if (metaAndName.isPresent())
		{
			final N5FSMeta    meta      = metaAndName.get().getKey();
			final String      name      = metaAndName.get().getValue();
			final N5FSReader  reader    = meta.reader();
			final String      group     = meta.dataset();
			final String      dataGroup = String.format("%s/data", group);
			final AffineTransform3D transform = N5Helpers.getTransform(reader, dataGroup);
			final DataSource<LabelMultisetType, VolatileLabelMultisetType> source = new N5DataSource<>(
					meta,
					transform,
					name,
					pbv.getQueue(),
					0);

			final Supplier<String> canvasDirUpdater = Masks.canvasTmpDirDirectorySupplier(projecDirectory);
			final CommitCanvasN5   commitCanvas     = new CommitCanvasN5(meta.writer(), group);
			final DataSource<LabelMultisetType, VolatileLabelMultisetType> maskedSource = Masks.mask(
					source,
					pbv.getQueue(),
					canvasDirUpdater.get(),
					canvasDirUpdater,
					commitCanvas,
					pbv.getPropagationQueue());

			final IdService                      idService      = N5Helpers.idService(meta.writer(), group, 1);
			final SelectedIds                    selectedIds    = new SelectedIds();
			final FragmentSegmentAssignmentState assignment     = N5Helpers.assignments(meta.writer(), group);
			final SelectedSegments selectedSegments = new SelectedSegments(selectedIds, assignment);
			final LockedSegmentsOnlyLocal        lockedSegments = new LockedSegmentsOnlyLocal(locked -> {});
			final LabelBlockLookup lookup = getLookup(meta.reader(), meta.dataset());

			final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new ModalGoldenAngleSaturatedHighlightingARGBStream(
					selectedSegments,
					lockedSegments);
			final HighlightingStreamConverter<VolatileLabelMultisetType> converter = HighlightingStreamConverter.forType(
					stream,
					new VolatileLabelMultisetType());

			final MeshManagerWithAssignmentForSegments meshManager = MeshManagerWithAssignmentForSegments.fromBlockLookup(
					maskedSource,
					selectedSegments,
					stream,
					pbv.viewer3D().meshesGroup(),
					pbv.viewer3D().viewFrustumProperty(),
					pbv.viewer3D().eyeToWorldTransformProperty(),
					lookup,
					pbv.getMeshManagerExecutorService(),
					pbv.getMeshWorkerExecutorService());

			final LabelSourceState<LabelMultisetType, VolatileLabelMultisetType> state = new LabelSourceState<>(
					maskedSource,
					converter,
					new ARGBCompositeAlphaYCbCr(),
					name,
					assignment,
					lockedSegments,
					idService,
					selectedIds,
					meshManager,
					lookup);

			LOG.warn("Adding label state {}", state);
			pbv.addLabelSource(state);
		}
	}

	private static LabelBlockLookup getLookup(final N5Reader reader, final String dataset) throws IOException {
		try {
			return N5Helpers.getLabelBlockLookup(reader, dataset);
		} catch (final N5Helpers.NotAPainteraDataset e) {
			LOG.error("Error while creating label-block-lookup", e);
			return new LabelBlockLookupNoBlocks();
		}
	}
}
