package bdv.bigcat.viewer.state;

import java.util.Arrays;

import gnu.trove.set.hash.TLongHashSet;

public class SelectedSegments< F extends FragmentSegmentAssignmentState< F > > extends AbstractState< SelectedSegments< F > >
{

	private final SelectedIds selectedIds;

	private final F assignment;

	private final TLongHashSet selectedSegments = new TLongHashSet();

	private final SelectionListener selectionListener = new SelectionListener();

	private final AssignmentListener assignmentListener = new AssignmentListener();

	public SelectedSegments( final SelectedIds selectedIds, final F assignment )
	{
		super();
		this.selectedIds = selectedIds;
		this.assignment = assignment;
		this.selectedIds.addListener( selectionListener );
		this.assignment.addListener( assignmentListener );
	}

	public long[] getSelectedSegments()
	{
		synchronized ( this.selectedSegments )
		{
			return this.selectedSegments.toArray();
		}
	}

	private void update()
	{
		synchronized ( this.selectedSegments )
		{
			this.selectedSegments.clear();
			this.selectedSegments.addAll( Arrays.stream( this.selectedIds.getActiveIds() ).map( assignment::getSegment ).toArray() );
		}
		stateChanged();
	}

	private class SelectionListener implements StateListener< SelectedIds >
	{

		@Override
		public void stateChanged()
		{
			update();
		}

	}

	private class AssignmentListener implements StateListener< F >
	{

		@Override
		public void stateChanged()
		{
			update();
		}

	}

}
