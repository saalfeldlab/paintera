package org.janelia.saalfeldlab.paintera.control.undo;

import javafx.collections.ObservableList;

public interface HasHistory<T>
{

	ObservableList<T> events();

}
