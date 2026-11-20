package org.janelia.saalfeldlab.paintera.ui.source

import bdv.viewer.Source
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.css.PseudoClass
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.input.MouseEvent
import javafx.scene.control.ButtonType
import javafx.scene.control.TitledPane
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.VBox
import javafx.stage.Window
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.state.SourceInfo
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.source.state.StatePane
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.stream.Collectors
import kotlin.jvm.optionals.getOrNull


class SourceTabs(private val info: SourceInfo) : VBox() {

	private val statePaneCache = mutableMapOf<Source<*>, StatePane>()

	private val statePanes = FXCollections.observableArrayList<StatePane>().also { obs ->
		obs.addListener(ListChangeListener {
			InvokeOnJavaFXApplicationThread {
				this@SourceTabs.children.setAll(obs.map { it.pane }) }
		})
	}
	private val activeSourceToggleGroup = ToggleGroup().apply {
		selectedToggleProperty().map { it?.userData as? Source<*> }.subscribe { source ->
			info.currentSourceProperty().set(source)
		}
		info.currentSourceProperty().subscribe { source ->
			toggles.firstOrNull { it?.userData == source }?.let { selectToggle(it) } ?: selectToggle(null)
		}
	}

	init {
		LOG.debug("Constructing {}", SourceTabs::class.java.name)
		this.info.trackSources().addListener(ListChangeListener {
			val copy = ArrayList(this.info.trackSources())
			val show = copy.stream().map { source -> statePaneCache.computeIfAbsent(source) { this.makeStatePane(it) } }.collect(Collectors.toList())
			this.statePanes.setAll(show)
		})

		this.info.removedSourcesTracker().addListener(ListChangeListener { change -> change.list.toTypedArray().forEach { statePaneCache.remove(it) } })

	}

	private fun makeStatePane(source: Source<*>): StatePane {
		val statePane = StatePane(
			info.getState(source),
			activeSourceToggleGroup
		) { removeDialog(info, it, this.scene?.window) }

		statePane.pane.addDragAndDropListener(statePane, info, this.children)

		return statePane
	}

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private fun removeDialog(info: SourceInfo, source: Source<*>, window: Window?) {
			val name = info.getState(source)?.nameProperty()?.get() ?: source.name
			val index = info.indexOf(source)
			PainteraAlerts.confirmation("_Remove", "_Cancel").apply {
				contentText = "Remove source #$index `$name?'"
				headerText = null
			}.showAndWait().filter { it -> ButtonType.OK == it }.getOrNull()?.let {
				runCatching { info.removeSource(source) }.exceptionOrNull()?.let { e ->
					(e as? Exception)?.let {
						Exceptions.exceptionAlert(
							Constants.NAME,
							"Unable to remove source #$index `$name': ${e.message}",
							it,
							owner = window
						)
					}
				}
			}
		}

		private val DROP_ABOVE = PseudoClass.getPseudoClass("drop-above")
		private val DROP_BELOW = PseudoClass.getPseudoClass("drop-below")
		private val DRAGGING = PseudoClass.getPseudoClass("dragging")

		/* drag for reorder is valid on any part of the source tab header that either the title style or the DRAG_TARGET_STYLE */
		private fun isDragStartTarget(target: Any?, statePane: StatePane): Boolean {

			val pickedNode = target as? Node ?: return false
			return StatePane.DRAG_TARGET_STYLE in pickedNode.styleClass || "title" in pickedNode.styleClass
		}

		private fun TitledPane.addDragAndDropListener(statePane: StatePane, info: SourceInfo, children: List<Node>) {

			fun clearDropIndicator() {
				pseudoClassStateChanged(DROP_ABOVE, false)
				pseudoClassStateChanged(DROP_BELOW, false)
			}

			/* set DRAGGING on the title box if a drag started. */
			setOnDragDetected { event ->
				if (!isDragStartTarget(event.target, statePane))
					return@setOnDragDetected
				statePane.titleBox.pseudoClassStateChanged(DRAGGING, true)
				startFullDrag()
				event.consume()
			}
			/* MOUSE_RELEASE ends the drag state*/
			addEventHandler(MouseEvent.MOUSE_RELEASED) {
				statePane.titleBox.pseudoClassStateChanged(DRAGGING, false)
			}

			/* indicate which of the children of the title pane are being dragged over for styling. */
			setOnMouseDragOver { event ->
				val origin = event.gestureSource
				if (origin === this || origin !is TitledPane)
					return@setOnMouseDragOver
				val movingDown = children.indexOf(origin) < children.indexOf(this)
				pseudoClassStateChanged(DROP_BELOW, movingDown)
				pseudoClassStateChanged(DROP_ABOVE, !movingDown)
			}

			setOnMouseDragExited { clearDropIndicator() }

			setOnMouseDragReleased { event ->
				clearDropIndicator()
				val origin = event.gestureSource
				(origin as? TitledPane)?.graphic?.let { title ->
					title.pseudoClassStateChanged(DRAGGING, false)
					title.lookup(".source-drag-handle")?.cursor = Cursor.OPEN_HAND
				}
				if (origin !== this && origin is TitledPane) {
					val sourceIndex = children.indexOf(origin)
					val targetIndex = children.indexOf(this)
					info.moveSourceTo(sourceIndex, targetIndex)
				}
			}
		}
	}

}
