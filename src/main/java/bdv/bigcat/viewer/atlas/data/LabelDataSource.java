package bdv.bigcat.viewer.atlas.data;

import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;

public interface LabelDataSource< D, T > extends DataSource< D, T >
{

	public FragmentSegmentAssignmentState< ? > getAssignment();

}
