package org.janelia.saalfeldlab.paintera.control.paint;

import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.function.BiConsumer;

public class SelectNextId {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final IdService idService;

  private final SelectedIds selectedIds;

  public SelectNextId(final IdService idService, final SelectedIds selectedIds) {

	super();
	this.idService = idService;
	this.selectedIds = selectedIds;
  }

  public long getNextId() {

	return getNextId(SelectedIds::activate);
  }

  public long getNextId(final BiConsumer<SelectedIds, Long> action) {

	long next = idService.next();
	action.accept(selectedIds, next);
	return next;
  }

}
