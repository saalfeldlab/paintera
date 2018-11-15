package org.janelia.saalfeldlab.paintera.state;

import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsState;

public interface HasLockedSegments {

	LockedSegmentsState lockedSegments();

}
