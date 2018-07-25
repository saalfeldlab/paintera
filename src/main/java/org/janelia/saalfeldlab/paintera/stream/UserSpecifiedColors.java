package org.janelia.saalfeldlab.paintera.stream;

import javafx.collections.ObservableMap;
import javafx.scene.paint.Color;

public interface UserSpecifiedColors
{

	public ObservableMap<Long, Color> userSpecifiedColors();

	public void setColor(long id, Color color);

	public void removeColor(long id);

}
