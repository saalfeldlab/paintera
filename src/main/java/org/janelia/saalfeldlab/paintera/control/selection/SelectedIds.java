package org.janelia.saalfeldlab.paintera.control.selection;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;

import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.type.label.Label;
import org.janelia.saalfeldlab.fx.ObservableWithListenersList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SelectedIds extends ObservableWithListenersList
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final TLongHashSet selectedIds;

	private long lastSelection = Label.INVALID;

	public SelectedIds()
	{
		this(new TLongHashSet());
	}

	public SelectedIds(final TLongHashSet selectedIds)
	{
		super();
		this.selectedIds = selectedIds;
		updateLastSelection();
	}

	public boolean isActive(final long id)
	{
		return selectedIds.contains(id);
	}

	public void activate(final long... ids)
	{
		deactivateAll(false);
		activateAlso(ids);
		LOG.debug("Activated " + Arrays.toString(ids) + " " + selectedIds);
	}

	public void activateAlso(final long... ids)
	{
		for (final long id : ids)
			selectedIds.add(id);
		if (ids.length > 0)
			this.lastSelection = ids[0];
		stateChanged();
	}

	public void deactivateAll()
	{
		deactivateAll(true);
	}

	private void deactivateAll(final boolean notify)
	{
		selectedIds.clear();
		lastSelection = Label.INVALID;
		if (notify)
			stateChanged();
	}

	public void deactivate(final long... ids)
	{
		for (final long id : ids)
		{
			selectedIds.remove(id);
			if (id == lastSelection)
				lastSelection = Label.INVALID;
		}
		LOG.debug("Deactivated {}, {}", Arrays.toString(ids), selectedIds);
		stateChanged();
	}

	public boolean isOnlyActiveId(final long id)
	{
		return selectedIds.size() == 1 && isActive(id);
	}

	public long[] getActiveIds()
	{
		return this.selectedIds.toArray();
	}

	public long getLastSelection()
	{
		return this.lastSelection;
	}

	public boolean isLastSelection(final long id)
	{
		return this.lastSelection == id;
	}

	@Override
	public String toString()
	{
		return selectedIds.toString();
	}

	private void updateLastSelection()
	{
		if (selectedIds.size() > 0)
		{
			lastSelection = selectedIds.iterator().next();
		}
	}

}
