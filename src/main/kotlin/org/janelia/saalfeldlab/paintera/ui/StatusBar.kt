package org.janelia.saalfeldlab.paintera.ui

import javafx.beans.binding.DoubleBinding
import javafx.beans.property.ObjectProperty
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.layout.Background
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.scene.text.Font
import net.imglib2.RealPoint
import org.janelia.saalfeldlab.fx.extensions.createValueBinding
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.control.navigation.CoordinateDisplayListener
import org.janelia.saalfeldlab.paintera.paintera

private const val NOT_APPLICABLE = "N/A"
private val MONOSPACE = Font.font("Monospaced")

internal class StatusBar(var backgroundBinding: ObjectProperty<Background>, var prefWidthBinding: DoubleBinding) : HBox() {

    private val statusLabel = Label().apply {
        tooltip = Tooltip().also { it.textProperty().bind(textProperty()) }
        prefWidth = 95.0
    }

    var statusText by statusLabel.textProperty().nullable()

    private val viewerCoordinateStatusLabel = Label().apply {
        prefWidth = 115.0
        font = MONOSPACE
    }
    private var viewerCoordinateStatus by viewerCoordinateStatusLabel.textProperty().nullable()

    private val worldCoordinateStatusLabel = Label().apply {
        prefWidth = 245.0
        font = MONOSPACE
    }

    private var worldCoordinateStatus by worldCoordinateStatusLabel.textProperty().nullable()

    private val statusValueLabel = Label()
    var statusValue by statusValueLabel.textProperty().nullable()

    private val sourceDisplayStatus = StackPane().apply {
        // show source name by default, or override it with source status text if any
        paintera.baseView.sourceInfo().currentState().addListener { _, _, newv ->
            with(newv) {
                displayStatus?.let { children.setAll(it) }
                statusLabel.textProperty().unbind()
                statusLabel.textProperty().bind(
                    statusTextProperty().createValueBinding(nameProperty()) {
                        it?.run { ifEmpty { null } } ?: nameProperty().get()
                    }
                )
            }
        }
    }

    private val modeStatus = Label().apply {
        paintera.baseView.activeModeProperty.addListener { _, _, new ->
            textProperty().unbind()
            textProperty().bind(new.statusProperty)
        }
    }

    fun updateStatusBarNode(vararg nodes: Node) {
        children.setAll(*nodes)
    }

    fun updateStatusBarLabel(text: String) {
        statusText = text
    }

    internal fun setViewerCoordinateStatus(point: RealPoint?) {
        val coords = point?.let {
            String.format("(% 4d, % 4d)", point.getDoublePosition(0).toInt(), point.getDoublePosition(1).toInt())
        } ?: NOT_APPLICABLE
        InvokeOnJavaFXApplicationThread {
            viewerCoordinateStatus = coords
        }
    }

    internal fun setWorldCoordinateStatus(point: RealPoint?) {
        val coords = point?.let {
            CoordinateDisplayListener.worldToString(point)
        } ?: NOT_APPLICABLE
        InvokeOnJavaFXApplicationThread {
            worldCoordinateStatus = coords
        }
    }


    init {
        spacing = 5.0

        children += sourceDisplayStatus
        children += statusLabel
        children += viewerCoordinateStatusLabel
        children += worldCoordinateStatusLabel
        children += statusValueLabel

        // for positioning the 'show status bar' checkbox on the right
        children += NamedNode.bufferNode().also { setHgrow(it, Priority.ALWAYS) }
        children += modeStatus
        children += NamedNode.bufferNode()

        backgroundProperty().bind(backgroundBinding)
        prefWidthProperty().bind(prefWidthBinding)
    }
}
