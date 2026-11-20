package org.janelia.saalfeldlab.paintera.ui.source.state

import bdv.viewer.Source
import javafx.css.PseudoClass
import javafx.scene.Cursor
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.control.ContentDisplay
import javafx.scene.layout.*
import javafx.scene.paint.Color
import org.janelia.saalfeldlab.fx.TextFields
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid
import org.kordamp.ikonli.javafx.FontIcon
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.function.Consumer

class StatePane(
	private val state: SourceState<*, *>,
	activeSourceRadioButtonGroup: ToggleGroup,
	remove: Consumer<Source<*>>,
) {

	private val nameProperty = state.nameProperty()

	private val statePaneVisibleProperty = state.isVisibleProperty
	var statePaneIsVisible: Boolean by statePaneVisibleProperty.nonnull()

	val pane = TitledPane(null, state.preferencePaneNode()).apply {
		HBox.setHgrow(this, Priority.ALWAYS)
		VBox.setVgrow(this, Priority.ALWAYS)
		isExpanded = false
		alignment = Pos.CENTER_RIGHT
		LOG.debug("_pane width is {} ({})", this.width, width)
	}

	val titleBox = HBox()

	init {
		val closeButton = Button(null).apply {
			addStyleClass("source-control", *Style.CLOSE_ICON.classes)
			onAction = EventHandler { remove.accept(state.dataSource) }
			tooltip = Tooltip("Remove source")
		}
		val activeSource = RadioButton().apply {
			tooltip = Tooltip("Select as active source")
			toggleGroup = activeSourceRadioButtonGroup
			userData = state.dataSource
		}

		val visibilityButton = Button(null).apply {
			addStyleClass("source-control", *Style.VISIBILITY_ICON.classes)
			statePaneVisibleProperty.subscribe { visible ->
				pseudoClassStateChanged(PseudoClass.getPseudoClass("visible"), visible)
			}
			onAction = EventHandler { statePaneIsVisible = !statePaneIsVisible }
			maxWidth = 20.0
			tooltip = Tooltip("Toggle visibility")
		}
		val nameField = TextFields.editableOnDoubleClick().apply {
			textProperty().bindBidirectional(nameProperty)
			tooltip = Tooltip().also {
				it.textProperty()
					.bind(nameProperty.createNullableValueBinding { name -> "Source $name: Double click to change name, enter to confirm, escape to discard." })
			}
			backgroundProperty().bind(editableProperty().createNonNullValueBinding { if (it) EDITABLE_BACKGROUND else UNEDITABLE_BACKGROUND })
			HBox.setHgrow(this, Priority.ALWAYS)
		}
		val dragHandle = Label(null, FontIcon(FontAwesomeSolid.GRIP_LINES)).apply {
			styleClass += "source-drag-handle"
			cursor = Cursor.OPEN_HAND
			tooltip = Tooltip("Drag to reorder")
			setOnMousePressed { cursor = Cursor.CLOSED_HAND }
			setOnMouseReleased { cursor = Cursor.OPEN_HAND }
		}
		titleBox.apply {
			val bufferNode = NamedNode.bufferNode()
			styleClass += "source-title"
			children.setAll(nameField, bufferNode, activeSource, visibilityButton, closeButton, dragHandle)
			alignment = Pos.CENTER
			padding = Insets(0.0, RIGHT_PADDING, 0.0, LEFT_PADDING)
			/* all these nodes can receive he drag event for reordering the source tabs */
			listOf(this, bufferNode, dragHandle, dragHandle.graphic).forEach { it.styleClass += DRAG_TARGET_STYLE }
		}
		pane.graphic = titleBox
		pane.contentDisplay = ContentDisplay.GRAPHIC_ONLY

	}

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		internal const val DRAG_TARGET_STYLE = "valid-drag-target"

		private const val LEFT_PADDING = 0.0

		private const val RIGHT_PADDING = 0.0

		private val EDITABLE_BACKGROUND = Background(BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets(-1.4, 0.0, 1.0, 2.0)))

		private val UNEDITABLE_BACKGROUND = Background(BackgroundFill(Color.WHITE.deriveColor(0.0, 1.0, 1.0, 0.5), CornerRadii.EMPTY, Insets.EMPTY))

	}

}
