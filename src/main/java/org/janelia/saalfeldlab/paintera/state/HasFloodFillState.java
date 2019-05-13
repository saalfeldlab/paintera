package org.janelia.saalfeldlab.paintera.state;

import javafx.beans.property.ObjectProperty;

public interface HasFloodFillState {

	ObjectProperty<Long> floodFillState();
}
