package org.janelia.saalfeldlab.paintera.meshes

import javafx.beans.property.SimpleObjectProperty
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManagerWithAssignmentForSegments
import org.janelia.saalfeldlab.paintera.meshes.ui.MeshInfoList
import org.janelia.saalfeldlab.paintera.ui.source.mesh.SegmentMeshInfoNode

class SegmentMeshInfoList(
	val manager: MeshManagerWithAssignmentForSegments,
) : MeshInfoList<SegmentMeshInfo, Long>() {

	private val segmentMeshInfoMap = mutableMapOf<Long, SegmentMeshInfo>()
	val selectedSegmentsProperty = SimpleObjectProperty<List<Long>>()

	init {

		val meshesEnabled = manager.managedSettings.meshesEnabledProperty

		selectedSegmentsProperty.`when`(meshesEnabled).subscribe { selectedSegments ->
			selectedSegments ?: return@subscribe

			updateItems(meshesEnabled.get())
		}
	}

	@Synchronized
	override fun updateItems(meshesEnabled: Boolean) {

		val selectedSegments by lazy(LazyThreadSafetyMode.NONE) { selectedSegmentsProperty.get() ?: emptyList() }

		if (!meshesEnabled || selectedSegments.isEmpty()) {
			InvokeOnJavaFXApplicationThread {
				segmentMeshInfoMap.clear()
				items.forEach { it?.dispose() }
				items.clear()
			}
			return
		}

		val toAdd = selectedSegments - segmentMeshInfoMap.keys
		val toRemove = segmentMeshInfoMap.keys - selectedSegments

		InvokeOnJavaFXApplicationThread {
			/* remove keys and dispose values we don't need anymore*/
			toRemove.forEach { segmentMeshInfoMap.remove(it)?.dispose() }
			/* add keys we want */
			segmentMeshInfoMap.putAll(toAdd.associateWith { SegmentMeshInfo(it, manager) })
			items.setAll(segmentMeshInfoMap.values)
		}


	}

	override fun meshNodeFactory(meshInfo: SegmentMeshInfo): SegmentMeshInfoNode {
		return SegmentMeshInfoNode(meshInfo).also {
			it.initContent()
		}
	}
}