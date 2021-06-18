package org.janelia.saalfeldlab.paintera.data.mask.persist;

import javafx.beans.property.ReadOnlyDoubleProperty;

public interface ProgressReporter {

  ReadOnlyDoubleProperty getProgressProperty();

}
