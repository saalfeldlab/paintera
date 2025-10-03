package org.janelia.saalfeldlab.paintera.ui.source.state

import bdv.viewer.Source
import javafx.css.PseudoClass
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.paint.Color
import org.janelia.saalfeldlab.fx.TextFields
import org.janelia.saalfeldlab.fx.extensions.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.state.SourceState
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
		val titleBox = HBox(
			nameField,
			NamedNode.bufferNode(),
			activeSource,
			visibilityButton,
			closeButton
		).apply {
			alignment = Pos.CENTER
			padding = Insets(0.0, RIGHT_PADDING, 0.0, LEFT_PADDING)
		}
		with(TitledPaneExtensions) {
			pane.graphicsOnly(titleBox)
		}
		// TODO how to get underlined in TextField?
//        nameField.underlineProperty().bind(_isCurrentSource)

	}

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private const val LEFT_PADDING = 0.0

		private const val RIGHT_PADDING = 0.0

		private val EDITABLE_BACKGROUND = Background(BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets(-1.4, 0.0, 1.0, 2.0)))

		private val UNEDITABLE_BACKGROUND = Background(BackgroundFill(Color.WHITE.deriveColor(0.0, 1.0, 1.0, 0.5), CornerRadii.EMPTY, Insets.EMPTY))

	}

}
