package bdv.bigcat.viewer.atlas.data;

import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.viewer3d.marchingCubes.ForegroundCheck;

public interface LabelDataSource< D, T > extends DataSource< D, T >
{

	public FragmentSegmentAssignmentState< ? > getAssignment();

	public ForegroundCheck< D > foregroundCheck( D d );

}
