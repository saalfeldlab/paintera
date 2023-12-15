package org.janelia.saalfeldlab.paintera.meshes

import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class SegmentMeshInfo(segmentId: Long, override val manager: MeshManagerWithAssignmentForSegments) : MeshInfo<Long>(segmentId, manager) {

	fun fragments(): LongArray {
		return manager.getFragmentsFor(key).toArray()
	}
}

