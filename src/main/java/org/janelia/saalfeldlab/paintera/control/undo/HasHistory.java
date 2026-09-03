package org.janelia.saalfeldlab.paintera.control.undo;

import org.janelia.saalfeldlab.fx.undo.EventHistory;

/**
 * Can provide an EventHistory that support undo/redo/delete operations.
 */
public interface HasHistory<T> {

	EventHistory<T> getHistory();

}
