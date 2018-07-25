package org.janelia.saalfeldlab.paintera.control.paint;

import java.lang.invoke.MethodHandles;
import java.util.function.BiConsumer;

import bdv.viewer.Source;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SelectNextId
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final SourceInfo sourceInfo;

	public SelectNextId(final SourceInfo sourceInfo)
	{
		super();
		this.sourceInfo = sourceInfo;
	}

	public void getNextIds(final int count)
	{
		getNextId((selectedIds, idService) -> selectedIds.activate(idService.next(count)));
	}

	public void getNextId()
	{
		getNextId((selectedIds, idService) -> selectedIds.activate(idService.next()));
	}

	private void getNextId(final BiConsumer<SelectedIds, IdService> action)
	{

		final Source<?> currentSource = sourceInfo.currentSourceProperty().get();

		if (currentSource == null)
		{
			LOG.warn("No current source -- cannot create new id.");
			return;
		}

		final SourceState<?, ?> currentSourceState = sourceInfo.getState(currentSource);

		if (!(currentSourceState instanceof LabelSourceState<?, ?>))
		{
			LOG.warn("Not a label source -- cannot request id.");
			return;
		}
		final LabelSourceState<?, ?> state = (LabelSourceState<?, ?>) currentSourceState;

		// TODO should we create ids also for invisible sources?
		if (!state.isVisibleProperty().get())
		{
			LOG.warn("Source {} is not visible -- cannot create new id.", currentSource);
			return;
		}

		final IdService idService = state.idService();
		if (idService == null)
		{
			LOG.warn("Source {} does not provide id-service -- cannot create new id.", currentSource);
			return;
		}

		final SelectedIds selectedIds = state.selectedIds();
		action.accept(selectedIds, idService);
	}

}
