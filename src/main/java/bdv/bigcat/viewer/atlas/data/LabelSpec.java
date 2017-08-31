package bdv.bigcat.viewer.atlas.data;

import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;

public interface LabelSpec< T, VT > extends DatasetSpec< T, VT >
{

	public FragmentSegmentAssignmentState< ? > getAssignment();

}
