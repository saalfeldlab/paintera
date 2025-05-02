package org.janelia.saalfeldlab.paintera.ui.source.mesh

import javafx.geometry.VPos
import javafx.scene.control.Label
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import org.fxmisc.flowless.VirtualizedScrollPane
import org.fxmisc.richtext.InlineCssTextArea
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.paintera.meshes.SegmentMeshInfo
import org.janelia.saalfeldlab.paintera.meshes.ui.MeshInfoPane

class SegmentMeshInfoNode(private val segmentMeshInfo: SegmentMeshInfo) : MeshInfoPane<Long>(segmentMeshInfo) {

    override fun createMeshInfoGrid(grid: GridPane): GridPane {

        val textIds = InlineCssTextArea(segmentMeshInfo.fragments().contentToString())
        val virtualPane = VirtualizedScrollPane(textIds)
        textIds.isWrapText = true

        val idsLabel = Label("ids: ")
        idsLabel.minWidth = 30.0
        idsLabel.maxWidth = 30.0
        val spacer = NamedNode.bufferNode()
        val idsHeader = HBox(idsLabel, spacer)

        return grid.apply {
            add(idsHeader, 0, 0)
	        GridPane.setValignment(idsHeader, VPos.CENTER)
            add(virtualPane, 1, 0)
	        GridPane.setHgrow(virtualPane, Priority.ALWAYS)
            super.createMeshInfoGrid(grid)
        }
    }
}