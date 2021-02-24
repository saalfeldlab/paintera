package org.janelia.saalfeldlab.paintera.control.assignment;

import javafx.beans.Observable;

public interface FragmentSegmentAssignmentState extends FragmentSegmentAssignment, Observable {

  void persist() throws UnableToPersist;

  default boolean hasPersistableData() {

	return true;
  }
}
