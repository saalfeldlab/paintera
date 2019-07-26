package org.janelia.saalfeldlab.paintera.control.selection;

import org.janelia.saalfeldlab.fx.ObservableWithListenersList;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;

import gnu.trove.set.hash.TLongHashSet;

public class SelectedSegments extends ObservableWithListenersList
{
	private final SelectedIds selectedIds;

	private final FragmentSegmentAssignmentState assignment;

	private final TLongHashSet selectedSegments = new TLongHashSet();

	public SelectedSegments(final SelectedIds selectedIds, final FragmentSegmentAssignmentState assignment)
	{
		super();
		this.selectedIds = selectedIds;
		this.assignment = assignment;

		/* TODO the following updates the set twice which is unnecessary */
		this.selectedIds.addListener(a -> update());
		this.assignment.addListener(a -> update());
	}

	public long[] getSelectedSegments()
	{
		synchronized (selectedSegments)
		{
			return selectedSegments.toArray();
		}
	}

	public boolean isSegmentSelected(final long id)
	{
		return selectedSegments.contains(id);
	}

	private void update()
	{
		synchronized (selectedSegments)
		{
			selectedSegments.clear();
			synchronized(selectedIds.getSet())
			{
				selectedIds.getSet().forEach(id -> {
					selectedSegments.add(assignment.getSegment(id));
					return true;
				});
			}
		}
		stateChanged();
	}

	public SelectedIds getSelectedIds()
	{
		return selectedIds;
	}

	public FragmentSegmentAssignmentState getAssignment()
	{
		return assignment;
	}

	/**
	 * Package protected for {@link SelectedSegments} internal use.
	 *
	 * @return
	 */
	TLongHashSet getSet()
	{
		return selectedSegments;
	}
}
