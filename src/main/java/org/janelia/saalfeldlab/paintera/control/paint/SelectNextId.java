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

	private final IdService idService;

	private final SelectedIds selectedIds;

	public SelectNextId(final IdService idService, final SelectedIds selectedIds)
	{
		super();
		this.idService = idService;
		this.selectedIds = selectedIds;
	}

	public void getNextId()
	{
		getNextId(SelectedIds::activate);
	}

	private void getNextId(final BiConsumer<SelectedIds, Long> action)
	{
		action.accept(selectedIds, idService.next());
	}

}
