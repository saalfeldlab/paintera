package org.janelia.saalfeldlab.paintera.state.label

import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsState
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.id.IdService
import org.janelia.saalfeldlab.paintera.state.SourceStateBackend

interface ConnectomicsLabelBackend<D, T> : SourceStateBackend<D, T> {

	val fragmentSegmentAssignment: FragmentSegmentAssignmentState

	val providesLookup: Boolean
		get() = false

	fun createLabelBlockLookup(source: DataSource<D, T>): LabelBlockLookup

	fun createIdService(source: DataSource<D, T>): IdService

}
