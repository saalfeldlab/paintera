package org.janelia.saalfeldlab.paintera.ui

import javafx.beans.binding.Bindings
import javafx.beans.binding.DoubleBinding
import javafx.beans.property.ObjectProperty
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.layout.Background
import javafx.scene.layout.HBox
import javafx.scene.layout.StackPane
import javafx.scene.text.Font
import net.imglib2.RealPoint
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.control.navigation.CoordinateDisplayListener
import org.janelia.saalfeldlab.paintera.paintera

private const val NOT_APPLICABLE = "N/A"

internal class StatusBar(var backgroundBinding: ObjectProperty<Background>, var prefWidthBinding: DoubleBinding) : HBox() {

    private val currentSourceStatusLabel = Label().apply {
        tooltip = Tooltip().also { it.textProperty().bind(textProperty()) }
        prefWidth = 95.0
    }

    private val viewerCoordinateStatusLabel = Label().apply {
        prefWidth = 115.0
        font = Font.font("Monospaced")
    }
    private var viewerCoordinateStatus by viewerCoordinateStatusLabel.textProperty().nullable()

    private val worldCoordinateStatusLabel = Label().apply {
        prefWidth = 245.0
        font = Font.font("Monospaced")
    }
    private var worldCoordinateStatus by worldCoordinateStatusLabel.textProperty().nullable()

    private val statusValueLabel = Label()
    var statusValue by statusValueLabel.textProperty().nullable()

    val sourceDisplayStatus = StackPane().apply {
        // show source name by default, or override it with source status text if any
        paintera.baseView.sourceInfo().currentState().addListener { _, _, newv ->
            newv.displayStatus?.let { children.setAll(it) }
            currentSourceStatusLabel.textProperty().unbind()
            currentSourceStatusLabel.textProperty().bind(
                Bindings.createStringBinding(
                    {
                        newv.statusTextProperty().get()?.let {
                            it.ifEmpty { null }
                        } ?: newv.nameProperty().get()
                    },
                    newv.nameProperty(),
                    newv.statusTextProperty()
                )
            )
        }
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
        children += currentSourceStatusLabel
        children += viewerCoordinateStatusLabel
        children += worldCoordinateStatusLabel
        children += statusValueLabel

        // for positioning the 'show status bar' checkbox on the right
        children += NamedNode.bufferNode()
        children += NamedNode.bufferNode()

        backgroundProperty().bind(backgroundBinding)
        prefWidthProperty().bind(prefWidthBinding)
    }
}
