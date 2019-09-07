package org.janelia.saalfeldlab.paintera.ui.source.state

import bdv.viewer.Source
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.binding.Bindings
import javafx.beans.binding.DoubleExpression
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.RadioButton
import javafx.scene.control.TitledPane
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.CornerRadii
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import org.janelia.saalfeldlab.fx.TextFields
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.paintera.state.SourceInfo
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.concurrent.Callable
import java.util.function.Consumer

class StatePane2(
        private val state: SourceState<*, *>,
        private val sourceInfo: SourceInfo,
		activeSourceRadioButtonGroup: ToggleGroup,
        remove: Consumer<Source<*>>,
        width: DoubleExpression) {

    private val _name = state.nameProperty()

    private val _isCurrentSource = sourceInfo.isCurrentSource(state.dataSource)

    private val _isVisible = state.isVisibleProperty

	var name: String
		get() = _name.get()
		set(name) = _name.set(name)

	val isCurrentSource: Boolean
		get() = _isCurrentSource.get()

	var isVisible: Boolean
		get() = _isVisible.get()
		set(isVisible) = _isVisible.set(isVisible)

	private val _pane = TitledPane(null, state.preferencePaneNode())
			.also { it.prefWidthProperty().bind(width) }
			.also { it.maxWidthProperty().bind(width) }
			.also { it.isExpanded = false }
			.also { it.alignment = Pos.CENTER_RIGHT }
			.also { LOG.debug("_pane width is {} ({})", it.width, width) }

	// TODO can we infer this somehow from _pane?
	private val arrowWidth = 50.0

	private val graphicWidth = width.subtract(arrowWidth)

	val pane: Node
		get() = _pane

    init {
		val closeButton = Button(null, FontAwesomeIconView(FontAwesomeIcon.CLOSE).also { it.scaleX = 2.0; it.scaleY = 2.0; it.scaleZ = 2.0 })
				.also { it.onAction = EventHandler { remove.accept(state.dataSource) } }
				.also { it.tooltip = Tooltip("Remove source") }
		val activeSource = RadioButton()
				.also { it.tooltip = Tooltip("Select as active source") }
				.also { it.selectedProperty().addListener { _, _, new -> if (new) sourceInfo.currentSourceProperty().set(state.dataSource)} }
				.also { _isCurrentSource.addListener { _, _, newv -> if (newv) it.isSelected = true } }
				.also { it.isSelected = isCurrentSource }
				.also { it.toggleGroup = activeSourceRadioButtonGroup }
		val visibilityIconViewVisible = FontAwesomeIconView(FontAwesomeIcon.EYE)
				.also { it.stroke = Color.BLACK }
				.also { it.scaleX = 2.0; it.scaleY = 2.0; it.scaleZ = 2.0 }
		val visibilityIconViewInvisible = FontAwesomeIconView(FontAwesomeIcon.EYE_SLASH)
				.also { it.stroke = Color.GRAY }
				.also { it.fill = Color.GRAY }
				.also { it.scaleX = 2.0; it.scaleY = 2.0; it.scaleZ = 2.0 }
		val visibilityButton = Button(null)
				.also { it.onAction = EventHandler { isVisible = !isVisible } }
				.also { it.graphicProperty().bind(Bindings.createObjectBinding(Callable { if (isVisible) visibilityIconViewVisible else visibilityIconViewInvisible }, _isVisible)) }
				.also { it.maxWidth = 20.0 }
				.also { it.tooltip = Tooltip("Toggle visibility") }
		val nameField = TextFields.editableOnDoubleClick()
				.also { it.textProperty().bindBidirectional(_name) }
				.also { it.tooltip = Tooltip().also { t -> t.textProperty().bind(Bindings.createStringBinding(Callable {"Source ${_name.value}: Double click to change name, enter to confirm, escape to discard."}, _name)) } }
				.also { HBox.setHgrow(it, Priority.ALWAYS) }
				.also {
					val bgProp = Bindings.createObjectBinding(Callable {if (it.isEditable) EDITABLE_BACKGROUND else UNEDITABLE_BACKGROUND}, it.editableProperty())
					it.backgroundProperty().bind(bgProp) }
		val titleBox = HBox(
				nameField,
				Region().also { HBox.setHgrow(it, Priority.ALWAYS) },
				activeSource,
				visibilityButton,
				closeButton)
				.also { it.alignment = Pos.CENTER }
				.also { it.padding = Insets(0.0, RIGHT_PADDING, 0.0, LEFT_PADDING) }
		with (TitledPaneExtensions) {
			_pane.graphicsOnly(titleBox)
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
