package bdv.bigcat.viewer.state;

import java.util.Arrays;

import gnu.trove.set.hash.TLongHashSet;

public class FragmentsInSelectedSegments< F extends FragmentSegmentAssignmentState< F > > extends AbstractState< FragmentsInSelectedSegments< F > >
{

	private final SelectedSegments< F > activeSegments;

	private final F assignment;

	private final TLongHashSet selectedFragments = new TLongHashSet();

	private final SelectionListener selectionListener = new SelectionListener();

	private final AssignmentListener assignmentListener = new AssignmentListener();

	public FragmentsInSelectedSegments( final SelectedSegments< F > activeSegments, final F assignment )
	{
		super();
		this.activeSegments = activeSegments;
		this.assignment = assignment;
		this.activeSegments.addListener( selectionListener );
		this.assignment.addListener( assignmentListener );
	}

	public long[] getFragments()
	{
		synchronized ( this.selectedFragments )
		{
			return this.selectedFragments.toArray();
		}
	}

	private void update()
	{
		synchronized ( this.selectedFragments )
		{
			final long[] activeSegments = this.activeSegments.getSelectedSegments();
			this.selectedFragments.clear();
			Arrays.stream( activeSegments ).mapToObj( assignment::getFragments ).forEach( this.selectedFragments::addAll );
		}
		stateChanged();
	}

	private class AssignmentListener implements StateListener< F >
	{

		@Override
		public void stateChanged()
		{
			update();
		}

	}

	private class SelectionListener implements StateListener< SelectedSegments< F > >
	{

		@Override
		public void stateChanged()
		{
			update();
		}

	}

}
