package org.janelia.saalfeldlab.paintera.ui.source.mesh

import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import org.fxmisc.flowless.VirtualizedScrollPane
import org.fxmisc.richtext.InlineCssTextArea
import org.janelia.saalfeldlab.paintera.meshes.SegmentMeshInfo
import org.janelia.saalfeldlab.paintera.meshes.ui.MeshInfoPane

class SegmentMeshInfoNode(private val segmentMeshInfo: SegmentMeshInfo) : MeshInfoPane<Long>(segmentMeshInfo) {

	override fun createMeshInfoGrid(): Node {

		val textIds = InlineCssTextArea(segmentMeshInfo.fragments().contentToString())
		textIds.totalHeightEstimateProperty().subscribe { height ->
			if (height != null && height < 100.0)
				textIds.maxHeight = height
			else textIds.maxHeight = USE_COMPUTED_SIZE
		}
		val virtualPane = VirtualizedScrollPane(textIds)
		textIds.isWrapText = true

		val idsLabel = Label("Fragment IDs: ").apply {
			val boldFont = Font.font(font.family, FontWeight.BOLD, font.size)
			font = boldFont
		}


		val node = super.createMeshInfoGrid()
		val topRow = VBox(idsLabel, virtualPane)
		HBox.setHgrow(virtualPane, Priority.ALWAYS)
		return VBox(
			topRow,
			node
		)
	}
}