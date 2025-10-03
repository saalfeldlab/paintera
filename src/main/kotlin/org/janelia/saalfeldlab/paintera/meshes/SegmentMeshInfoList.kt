package org.janelia.saalfeldlab.paintera.meshes

import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments
import org.janelia.saalfeldlab.paintera.meshes.ui.MeshInfoList
import org.janelia.saalfeldlab.paintera.ui.source.mesh.SegmentMeshInfoNode

class SegmentMeshInfoList(
	selectedSegments: List<Long>,
	val manager: MeshManagerWithAssignmentForSegments,
) : MeshInfoList<SegmentMeshInfo, Long>(FXCollections.observableArrayList(), manager) {

	val segmentMeshInfoMap = mutableMapOf<Long, SegmentMeshInfo>()
	val selectedSegmentsProperty = SimpleObjectProperty<List<Long>>(selectedSegments)

	init {

		selectedSegmentsProperty.subscribe { newSelection ->

			val toRemove = mutableSetOf<SegmentMeshInfo?>()
			val curSelection = newSelection.toMutableSet()
			/* remove old keys from cur, and existing keys from new*/
			segmentMeshInfoMap.entries.removeIf { (id, meshInfo) ->
				val remove = id !in curSelection
				if (remove) toRemove += meshInfo
				else curSelection -= id

				remove
			}
			curSelection.forEach {
				segmentMeshInfoMap[it] = SegmentMeshInfo(it, manager)
				segmentMeshInfoMap[it]!!
			}

			InvokeOnJavaFXApplicationThread {
				items = FXCollections.observableArrayList(segmentMeshInfoMap.values)
			}
		}
	}

	override fun meshNodeFactory(meshInfo: SegmentMeshInfo) = SegmentMeshInfoNode(meshInfo)
}