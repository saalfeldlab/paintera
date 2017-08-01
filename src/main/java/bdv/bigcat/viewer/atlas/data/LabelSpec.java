package bdv.bigcat.viewer.atlas.data;

import java.util.function.Consumer;

import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import gnu.trove.map.hash.TLongLongHashMap;

public interface LabelSpec< T, VT > extends DatasetSpec< T, VT >
{

	public FragmentSegmentAssignmentState< ? > getAssignment();

	public default Consumer< TLongLongHashMap > assignmentWriteBack()
	{
		return m -> {};
	}

}
