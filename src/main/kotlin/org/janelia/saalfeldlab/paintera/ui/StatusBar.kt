package org.janelia.saalfeldlab.paintera.ui

import bdv.viewer.Interpolation
import javafx.beans.value.ObservableBooleanValue
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Dialog
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.Background
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import javafx.util.Subscription
import net.imglib2.RealPoint
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.StyleGroup
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.control.OrthoViewCoordinateDisplayListener
import org.janelia.saalfeldlab.paintera.control.OrthogonalViewsValueDisplayListener
import org.janelia.saalfeldlab.paintera.control.navigation.CoordinateDisplayListener
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState

private const val NOT_APPLICABLE = "N/A"

private enum class StatusBarStyle(val style: String, vararg classes: String) : StyleGroup by StyleGroup.of(style, *classes) {
	STATUS_BAR("status-bar"),
	SOURCE_STATUS("source-status"),
	MODE_STATUS("mode-status"),
	SOURCE_VALUE("source-value"),
	SOURCE_DISPLAY_NODES("source-display-nodes"),
	COORDINATES("coordinates"),
	VIEWER_COORDS("viewer", COORDINATES),
	WORLD_COORDS("world", COORDINATES),
	SOURCE_COORDS("source", COORDINATES);

	constructor(style: String, vararg styles: StyleGroup) : this(style, *styles.flatMap { it.classes.toList() }.toTypedArray())
	constructor(style: String) : this(style, *emptyArray<StyleGroup>())

}

internal class StatusBar() : HBox() {

	private val statusLabel = TextField().apply {
		addStyleClass(StatusBarStyle.SOURCE_STATUS)
		tooltip = Tooltip().also { it.textProperty().bind(textProperty()) }
	}

	private val viewerCoordinateStatusLabel = TextField().apply {
		addStyleClass(StatusBarStyle.VIEWER_COORDS)
	}
	private var viewerCoordinateStatus by viewerCoordinateStatusLabel.textProperty().nullable()

	private val worldCoordinateStatusLabel = TextField().apply {
		addStyleClass(StatusBarStyle.WORLD_COORDS)
	}

	private var worldCoordinateStatus by worldCoordinateStatusLabel.textProperty().nullable()

	private val sourceCoordinateStatusLabel = TextField().apply {
		addStyleClass(StatusBarStyle.SOURCE_COORDS)
	}

	private var sourceCoordinateStatus by sourceCoordinateStatusLabel.textProperty().nullable()

	private val statusValueLabel = TextField().apply {
		addStyleClass(StatusBarStyle.SOURCE_VALUE)
	}

	private val sourceDisplayStatus = HBox().also { displayStatusPane ->
		addStyleClass(StatusBarStyle.SOURCE_DISPLAY_NODES)
		val conflatedTextUpdater = InvokeOnJavaFXApplicationThread.conflatedPulseLoop()

		val subscribeToSource: SourceState<*, *>.() -> Subscription? = {
			displayStatus?.let { displayStatusPane.children.setAll(it) }
			// show the source name by default, or override it with source status text if any
			statusTextProperty().createNullableValueBinding(nameProperty()) {
				it?.run { ifEmpty { null } } ?: nameProperty().get()
			}.subscribe { it ->
				conflatedTextUpdater.submit { statusLabel.text = it }
			}.and { displayStatusPane.children.clear() }
		}

		var prevSubscription: Subscription? = null
		paintera.baseView.sourceInfo().currentState().subscribe { newSource ->
			prevSubscription?.unsubscribe()
			prevSubscription = newSource?.subscribeToSource()
		}
	}


	private val modeStatus = TextField().apply {
		addStyleClass(StatusBarStyle.MODE_STATUS)
		paintera.baseView.activeModeProperty.addListener { _, _, new ->
			textProperty().unbind()
			if (new != null)
				textProperty().bind(new.statusProperty)
		}
	}

	init {
		addStyleClass(StatusBarStyle.STATUS_BAR)
		hGrow {
			maxWidth = Double.MAX_VALUE
		}
		spacing = 5.0

		children += sourceDisplayStatus
		children += statusLabel
		children += viewerCoordinateStatusLabel
		children += worldCoordinateStatusLabel
		children += sourceCoordinateStatusLabel
		children += statusValueLabel.hGrow()
		children += modeStatus.apply { alignment = Pos.CENTER_RIGHT }

//TODO Caleb: Remove this?
//		parentProperty().subscribe { parent ->
//			prefWidthProperty().unbind()
//			(parent as? Region)?.let { parent ->
//				prefWidthProperty().bind(parent.widthProperty())
//			}
//		}
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
		fun createPainteraStatusBar(visibilityBinding: ObservableBooleanValue? = null) = StatusBar().apply {
			visibilityBinding?.let {
				visibleProperty().bind(it)
				managedProperty().bind(visibleProperty())
			}

			val sourceInfo = paintera.baseView.sourceInfo()
			val currentSource = sourceInfo.currentSourceProperty()
			val vdl2 = OrthogonalViewsValueDisplayListener(
				{ status -> statusValueLabel.text = status },
				currentSource
			) {
				sourceInfo.getState(it)?.interpolationProperty()?.get() ?: Interpolation.NEARESTNEIGHBOR
			}
			vdl2.bindActiveViewer(paintera.baseView.mostRecentFocusHolder)

			val cdl2 = OrthoViewCoordinateDisplayListener(
				{ point -> setViewerCoordinateStatus(point) },
				{ point -> setWorldCoordinateStatus(point) },
				{ point -> setSourceCoordinateStatus(point) },
				currentSource
			)
			cdl2.bindActiveViewer(paintera.baseView.mostRecentFocusHolder)
		}
	}
}


fun main() {
	InvokeOnJavaFXApplicationThread {
		Dialog<Unit>().apply {
			isResizable = true
			this.dialogPane.content = HBox().apply {
				prefWidth = 600.0
				maxWidth = Double.MAX_VALUE
				prefHeight = 100.0
				background = Background.fill(Color.SEAGREEN)
				padding = Insets(10.0, 0.0, 10.0, 0.0)
				children += StatusBar()
			}
			showAndWait()
		}
	}
}