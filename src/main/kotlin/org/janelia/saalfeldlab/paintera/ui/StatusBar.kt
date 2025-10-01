package org.janelia.saalfeldlab.paintera.ui

import bdv.viewer.Interpolation
import javafx.beans.property.ObjectProperty
import javafx.beans.value.ObservableBooleanValue
import javafx.beans.value.ObservableDoubleValue
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.layout.Background
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.text.Font
import net.imglib2.RealPoint
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.control.OrthoViewCoordinateDisplayListener
import org.janelia.saalfeldlab.paintera.control.OrthogonalViewsValueDisplayListener
import org.janelia.saalfeldlab.paintera.control.navigation.CoordinateDisplayListener
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState

private const val NOT_APPLICABLE = "N/A"
private val MONOSPACE = Font.font("Monospaced")

internal class StatusBar(backgroundBinding: ObjectProperty<Background>) : HBox() {

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

	private val sourceCoordinateStatusLabel = Label().apply {
		prefWidth = 245.0
		font = MONOSPACE
	}

	private var sourceCoordinateStatus by sourceCoordinateStatusLabel.textProperty().nullable()

	private val statusValueLabel = Label()
	var statusValue by statusValueLabel.textProperty().nullable()

	private val sourceDisplayStatus = StackPane().apply {
		/* bind sourceState lambda */
		val bindSourceState: (SourceState<*, *>?) -> Unit = { sourceState ->

			statusLabel.textProperty().unbind()
			sourceState?.apply {
				displayStatus?.let { children.setAll(it) }
				statusLabel.textProperty().bind(
					statusTextProperty().createNullableValueBinding(nameProperty()) {
						it?.run { ifEmpty { null } } ?: nameProperty().get()
					}
				)
			}
		}
		// show source name by default, or override it with source status text if any
		paintera.baseView.sourceInfo().currentState().addListener { _, _, newv ->
			bindSourceState(newv)
		}
		/* set manually the first time */
		paintera.baseView.sourceInfo().currentState().get()?.let {
			bindSourceState(it)
		}
	}


	private val modeStatus = Label().apply {
		paintera.baseView.activeModeProperty.addListener { _, _, new ->
			textProperty().unbind()
			if (new != null)
				textProperty().bind(new.statusProperty)
		}
	}

	init {
		spacing = 5.0

		children += sourceDisplayStatus
		children += statusLabel
		children += viewerCoordinateStatusLabel
		children += worldCoordinateStatusLabel
		children += sourceCoordinateStatusLabel
		children += statusValueLabel

		// for positioning the 'show status bar' checkbox on the right
		children += NamedNode.bufferNode().also { setHgrow(it, Priority.ALWAYS) }
		children += modeStatus
		children += NamedNode.bufferNode()

		parentProperty().subscribe { parent ->
			prefWidthProperty().unbind()
			(parent as? Region)?.let { parent ->
				prefWidthProperty().bind(parent.widthProperty())
			}
		}
		backgroundProperty().bind(backgroundBinding)
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
			CoordinateDisplayListener.realPointToString(point)
		} ?: NOT_APPLICABLE
		InvokeOnJavaFXApplicationThread {
			worldCoordinateStatus = coords
		}
	}

	internal fun setSourceCoordinateStatus(point: RealPoint?) {
		val coords = point?.let {
			CoordinateDisplayListener.realPointToString(point)
		} ?: NOT_APPLICABLE
		InvokeOnJavaFXApplicationThread {
			sourceCoordinateStatus = coords
		}
	}

	companion object {
		fun createPainteraStatusBar(
			backgroundProperty: ObjectProperty<Background>,
			visibilityBinding: ObservableBooleanValue? = null
		): StatusBar {
			return StatusBar(backgroundProperty).apply {
				visibilityBinding?.let {
					visibleProperty().bind(it)
					managedProperty().bind(visibleProperty())
				}

				this.let {
					val sourceInfo = paintera.baseView.sourceInfo()
					val currentSource = sourceInfo.currentSourceProperty()
					val vdl2 = OrthogonalViewsValueDisplayListener(
						{ status -> it.statusValue = status },
						currentSource
					) {
						sourceInfo.getState(it)?.interpolationProperty()?.get() ?: Interpolation.NEARESTNEIGHBOR
					}
					vdl2.bindActiveViewer(paintera.baseView.currentFocusHolder)

					val cdl2 = OrthoViewCoordinateDisplayListener(
						{ point -> it.setViewerCoordinateStatus(point) },
						{ point -> it.setWorldCoordinateStatus(point) },
						{ point -> it.setSourceCoordinateStatus(point) },
						currentSource
					)
					cdl2.bindActiveViewer(paintera.baseView.currentFocusHolder)
				}
			}
		}
	}
}
