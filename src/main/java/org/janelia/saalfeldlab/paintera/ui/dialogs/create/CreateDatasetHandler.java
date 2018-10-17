package org.janelia.saalfeldlab.paintera.ui.dialogs.create;

import bdv.viewer.Source;
import javafx.util.Pair;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetType;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
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
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.janelia.saalfeldlab.util.n5.N5Helpers;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class CreateDatasetHandler
{

	public static void createAndAddNewLabelDataset(
			final PainteraBaseView pbv,
			final String projecDirectory,
			Consumer<Exception> exceptionHandler,
			final Source<?> currentSource,
			final Source<?>... allSources )
	{
		try {
			createAndAddNewLabelDataset(pbv, projecDirectory, currentSource, allSources);
		} catch (Exception e)
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
			AffineTransform3D transform = N5Helpers.getTransform(reader, dataGroup);
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
					pbv.getMeshWorkerExecutorService()
			                                                                                        );

			final IdService                      idService      = N5Helpers.idService(meta.writer(), group);
			final SelectedIds                    selectedIds    = new SelectedIds();
			final FragmentSegmentAssignmentState assignment     = N5Helpers.assignments(meta.writer(), group);
			final LockedSegmentsOnlyLocal        lockedSegments = new LockedSegmentsOnlyLocal(locked -> {});

			final LabelBlockLookup lookup = N5Helpers.getLabelBlockLookup(meta.reader(), meta.dataset());
			InterruptibleFunction<Long, Interval[]>[] blockLoaders = IntStream
					.range(0, maskedSource.getNumMipmapLevels())
					.mapToObj(level -> InterruptibleFunction.fromFunction( MakeUnchecked.function((MakeUnchecked.CheckedFunction<Long, Interval[]>) id -> lookup.read(level, id))))
					.toArray(InterruptibleFunction[]::new );


			ModalGoldenAngleSaturatedHighlightingARGBStream stream = new ModalGoldenAngleSaturatedHighlightingARGBStream(
					selectedIds,
					assignment,
					lockedSegments);
			final HighlightingStreamConverter<VolatileLabelMultisetType> converter = HighlightingStreamConverter.forType(
					stream,
					new VolatileLabelMultisetType());

			final MeshManagerWithAssignmentForSegments meshManager = MeshManagerWithAssignmentForSegments.fromBlockLookup(
					maskedSource,
					selectedIds,
					assignment,
					stream,
					pbv.viewer3D().meshesGroup(),
					blockLoaders,
					pbv.getGlobalCache()::createNewCache,
					pbv.getMeshManagerExecutorService(),
					pbv.getMeshWorkerExecutorService());

			LabelSourceState<LabelMultisetType, VolatileLabelMultisetType> state = new LabelSourceState<>(
					maskedSource,
					converter,
					new ARGBCompositeAlphaYCbCr(),
					name,
					assignment,
					lockedSegments,
					idService,
					selectedIds,
					meshManager);

			pbv.addLabelSource(state);
		}
	}
}
