package org.janelia.saalfeldlab.paintera.control.assignment;

import java.util.Arrays;

import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import org.janelia.saalfeldlab.fx.ObservableWithListenersList;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;

public class FragmentsInSelectedSegments extends ObservableWithListenersList
{

	private final SelectedSegments activeSegments;

	private final FragmentSegmentAssignmentState assignment;

	private final TLongHashSet selectedFragments = new TLongHashSet();

	private final SelectionListener selectionListener = new SelectionListener();

	private final AssignmentListener assignmentListener = new AssignmentListener();

	public FragmentsInSelectedSegments(final SelectedSegments activeSegments, final FragmentSegmentAssignmentState
			assignment)
	{
		super();
		this.activeSegments = activeSegments;
		this.assignment = assignment;
		this.activeSegments.addListener(selectionListener);
		this.assignment.addListener(assignmentListener);
	}

	public long[] getFragments()
	{
		synchronized (this.selectedFragments)
		{
			return this.selectedFragments.toArray();
		}
	}

	private void update()
	{
		synchronized (this.selectedFragments)
		{
			final long[] activeSegments = this.activeSegments.getSelectedSegments();
			this.selectedFragments.clear();
			Arrays.stream(activeSegments).mapToObj(assignment::getFragments).forEach(this.selectedFragments::addAll);
		}
		stateChanged();
	}

	private class AssignmentListener implements InvalidationListener
	{

		@Override
		public void invalidated(final Observable obs)
		{
			update();
		}

	}

	private class SelectionListener implements InvalidationListener
	{

		@Override
		public void invalidated(final Observable obs)
		{
			update();
		}

	}

	public boolean contains(final long id)
	{
		return this.selectedFragments.contains(id);
	}

}
