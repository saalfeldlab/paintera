package org.janelia.saalfeldlab.paintera.ui.dialogs.create;

import bdv.viewer.Source;
import com.pivovarit.function.ThrowingFunction;
import javafx.util.Pair;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetType;
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
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class CreateDatasetHandler
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static void createAndAddNewLabelDataset(
			final PainteraBaseView pbv,
			final String projecDirectory,
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
			final String projecDirectory,
			final Source<?> currentSource,
			final Source<?>... allSources) throws IOException {
		final CreateDataset                    cd          = new CreateDataset(currentSource, allSources);
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
					pbv.getGlobalCache(),
					name,
					0
			);

			final Supplier<String> canvasDirUpdater = Masks.canvasTmpDirDirectorySupplier(projecDirectory);
			final CommitCanvasN5   commitCanvas     = new CommitCanvasN5(meta.writer(), group);
			final DataSource<LabelMultisetType, VolatileLabelMultisetType> maskedSource = Masks.mask(
					source,
					canvasDirUpdater.get(),
					canvasDirUpdater,
					commitCanvas,
					pbv.getMeshWorkerExecutorService());

			final IdService                      idService      = N5Helpers.idService(meta.writer(), group, 1);
			final SelectedIds                    selectedIds    = new SelectedIds();
			final FragmentSegmentAssignmentState assignment     = N5Helpers.assignments(meta.writer(), group);
			final SelectedSegments selectedSegments = new SelectedSegments(selectedIds, assignment);
			final LockedSegmentsOnlyLocal        lockedSegments = new LockedSegmentsOnlyLocal(locked -> {});

			final LabelBlockLookup lookup = getLookup(meta.reader(), meta.dataset());
			final InterruptibleFunction<Long, Interval[]>[] blockLoaders = IntStream
					.range(0, maskedSource.getNumMipmapLevels())
					.mapToObj(level -> InterruptibleFunction.fromFunction( ThrowingFunction.unchecked((ThrowingFunction<Long, Interval[], Exception>) id -> lookup.read(level, id))))
					.toArray(InterruptibleFunction[]::new );




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
					blockLoaders,
					pbv.getGlobalCache()::createNewCache,
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
