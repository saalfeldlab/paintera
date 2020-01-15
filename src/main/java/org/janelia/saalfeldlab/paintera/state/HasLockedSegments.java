package org.janelia.saalfeldlab.paintera.state;

import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsState;

@Deprecated
public interface HasLockedSegments {

	LockedSegmentsState lockedSegments();

}
