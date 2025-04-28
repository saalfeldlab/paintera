package org.janelia.saalfeldlab.paintera.meshes

import com.sun.javafx.scene.control.MultipleAdditionAndRemovedChange
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments
import org.janelia.saalfeldlab.paintera.meshes.ui.MeshInfoList
import org.janelia.saalfeldlab.paintera.ui.source.mesh.SegmentMeshInfoNode

class SegmentMeshInfoList(
	selectedSegments: ObservableList<Long>,
	val manager: MeshManagerWithAssignmentForSegments
) : MeshInfoList<SegmentMeshInfo, Long>(FXCollections.observableArrayList(), manager) {

	init {
		val listChangeListener = ListChangeListener<Long> { change ->
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
				val meshInfoToRemove = meshInfoList.filter { toRemove.contains(it.key) }
				val meshInfoToAdd = toAdd.map { SegmentMeshInfo(it, manager) }.toSet()
				InvokeOnJavaFXApplicationThread {
					meshInfoList -= meshInfoToRemove
					meshInfoList += meshInfoToAdd
				}
			}
		}
		selectedSegments.addListener(listChangeListener)

		/*Trigger change listener for current list state */
		val currentListChange = MultipleAdditionAndRemovedChange<Long>(selectedSegments.toList(), emptyList<Long>(), selectedSegments)
		InvokeOnJavaFXApplicationThread {
			listChangeListener.onChanged(currentListChange)
		}
	}

	override fun meshNodeFactory(meshInfo: SegmentMeshInfo) = SegmentMeshInfoNode(meshInfo)
}