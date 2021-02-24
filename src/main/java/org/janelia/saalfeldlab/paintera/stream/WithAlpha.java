package org.janelia.saalfeldlab.paintera.stream;

import javafx.beans.property.IntegerProperty;

public interface WithAlpha {

  public IntegerProperty alphaProperty();

  public IntegerProperty activeSegmentAlphaProperty();

  public IntegerProperty activeFragmentAlphaProperty();

}
