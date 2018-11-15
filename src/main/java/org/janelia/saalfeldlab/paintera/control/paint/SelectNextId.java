package org.janelia.saalfeldlab.paintera.control.paint;

import java.lang.invoke.MethodHandles;
import java.util.function.BiConsumer;

import bdv.viewer.Source;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.state.HasIdService;
import org.janelia.saalfeldlab.paintera.state.HasSelectedIds;
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
			LOG.info("No current source -- cannot create new id.");
			return;
		}

		final SourceState<?, ?> currentSourceState = sourceInfo.getState(currentSource);

		if (!(currentSourceState instanceof HasIdService))
		{
			LOG.info("State does not have an IdService -- cannot request id.");
			return;
		}

		// TODO should we create ids also for invisible sources?
		if (!currentSourceState.isVisibleProperty().get())
		{
			LOG.info("Source {} is not visible -- cannot create new id.", currentSource);
			return;
		}

		final IdService idService = ((HasIdService)currentSourceState).idService();
		if (idService == null || idService instanceof IdService.IdServiceNotProvided)
		{
			LOG.info("Source {} does not provide id-service -- cannot create new id.", currentSource);
			return;
		}

		if (!(currentSourceState instanceof HasSelectedIds))
		{
			LOG.info("State does not have SelectedIds -- cannot request id");
			return;
		}

		final SelectedIds selectedIds = ((HasSelectedIds)currentSourceState).selectedIds();
		action.accept(selectedIds, idService);
	}

}
