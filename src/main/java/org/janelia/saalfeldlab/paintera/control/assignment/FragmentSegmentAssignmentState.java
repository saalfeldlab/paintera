package org.janelia.saalfeldlab.paintera.control.assignment;

import javafx.beans.Observable;

public interface FragmentSegmentAssignmentState extends FragmentSegmentAssignment, Observable
{

	public void persist() throws UnableToPersist;

}
