package org.janelia.saalfeldlab.paintera.control.selection;

import java.util.Arrays;

import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import org.janelia.saalfeldlab.fx.ObservableWithListenersList;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;

public class SelectedSegments extends ObservableWithListenersList
{

	private final SelectedIds selectedIds;

	private final FragmentSegmentAssignmentState assignment;

	private final TLongHashSet selectedSegments = new TLongHashSet();

	private final SelectionListener selectionListener = new SelectionListener();

	private final AssignmentListener assignmentListener = new AssignmentListener();

	public SelectedSegments(final SelectedIds selectedIds, final FragmentSegmentAssignmentState assignment)
	{
		super();
		this.selectedIds = selectedIds;
		this.assignment = assignment;
		this.selectedIds.addListener(selectionListener);
		this.assignment.addListener(assignmentListener);
	}

	public long[] getSelectedSegments()
	{
		synchronized (this.selectedSegments)
		{
			return this.selectedSegments.toArray();
		}
	}

	public boolean isSegmentSelected(final long id)
	{
		return this.selectedSegments.contains(id);
	}

	private void update()
	{
		synchronized (this.selectedSegments)
		{
			this.selectedSegments.clear();
			this.selectedSegments.addAll(Arrays.stream(this.selectedIds.getActiveIds()).map(assignment::getSegment)
					.toArray());
		}
		stateChanged();
	}

	private class SelectionListener implements InvalidationListener
	{

		@Override
		public void invalidated(final Observable obs)
		{
			update();
		}

	}

	private class AssignmentListener implements InvalidationListener
	{

		@Override
		public void invalidated(final Observable obs)
		{
			update();
		}

	}

}
