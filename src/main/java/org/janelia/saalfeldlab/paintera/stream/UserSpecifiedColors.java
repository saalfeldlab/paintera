package org.janelia.saalfeldlab.paintera.stream;

import javafx.collections.ObservableMap;
import javafx.scene.paint.Color;

public interface UserSpecifiedColors {

  ObservableMap<Long, Color> userSpecifiedColors();

  default void setColor(long id, Color color) {

	setColor(id, color, false);
  }

  void setColor(long id, Color color, boolean overrideAlpha);

  void removeColor(long id);

}
