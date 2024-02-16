package org.janelia.saalfeldlab.paintera.meshes

import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments
import org.janelia.saalfeldlab.paintera.meshes.ui.MeshInfoList
import org.janelia.saalfeldlab.paintera.ui.source.mesh.SegmentMeshInfoNode

class SegmentMeshInfoList(
	selectedSegments: ObservableList<Long>,
	val manager: MeshManagerWithAssignmentForSegments
) : MeshInfoList<SegmentMeshInfo, Long>(FXCollections.observableArrayList(), manager) {

	init {
		selectedSegments.addListener(ListChangeListener {change ->
			if (manager.managedSettings.isMeshListEnabledProperty.get()) {
				val toRemove = mutableSetOf<Long>()
				val toAdd = mutableSetOf<Long>()
				while (change.next()) {
					val removed = change.removed
					val added = change.addedSubList

					/* remove the outdated things */
					toRemove.removeAll(added)
					toAdd.removeAll(removed)

					/* add the new things */
					toAdd.addAll(added)
					toRemove.addAll(removed)
				}
				meshInfoList.removeIf { toRemove.contains(it.key) }
				meshInfoList += toAdd.map { SegmentMeshInfo(it, manager) }.toSet()
			}
		})
	}

	override fun meshNodeFactory(meshInfo: SegmentMeshInfo) = SegmentMeshInfoNode(meshInfo)
}