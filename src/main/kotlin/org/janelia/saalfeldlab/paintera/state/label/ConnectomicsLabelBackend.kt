package org.janelia.saalfeldlab.paintera.state.label

import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsState
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.id.IdService

interface ConnectomicsLabelBackend<D, T> {

	val source: DataSource<D, T>

	val lockedSegments: LockedSegmentsState

	val fragmentSegmentAssignment: FragmentSegmentAssignmentState

	val labelBlockLookup: LabelBlockLookup

	val idService: IdService

	fun setResolution(x: Double, y: Double, z: Double)

	fun setOffset(x: Double, y: Double, z: Double)

	fun getResolution(): DoubleArray

	fun getOffset(): DoubleArray

}
