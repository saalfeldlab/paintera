package org.janelia.saalfeldlab.paintera.ui.source

import javafx.beans.property.Property
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.control.MenuButton
import javafx.scene.control.MenuItem
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts

/** An editable list of the active channels of [numChannels], with a Select menu; edits write to [activeChannels] */
class ActiveChannelsNode(private val numChannels: Int, private val activeChannels: Property<List<Int>>) : VBox(2.0) {

	val field = TextField().apply {
		tooltip = Tooltip("the composited channels, e.g. `0, 2, 4-6`; enter to apply")
		HBox.setHgrow(this, Priority.ALWAYS)
	}

	val select = MenuButton(
		"Select", null,
		MenuItem("All").apply { setOnAction { set((0 until numChannels).toList()) } },
		MenuItem("None").apply { setOnAction { set(emptyList()) } },
		MenuItem("Every Nth").apply { setOnAction { everyNth()?.let { set(it) } } }
	).apply { minWidth = Region.USE_PREF_SIZE }

	val controls = HBox(5.0, field, select).apply { alignment = Pos.CENTER_LEFT }

	init {
		children.setAll(Label("Active Channels"), controls)
		fun applyField() {
			val channels = parseChannels(field.text)
			if (channels == null)
				field.text = formatChannels(activeChannels.value)
			else
				set(channels)
		}
		field.setOnAction { applyField() }
		field.focusedProperty().subscribe { _, focused ->
			if (!focused)
				applyField()
		}
		activeChannels.subscribe { channels -> field.text = formatChannels(channels) }
	}

	private fun set(channels: List<Int>) {
		activeChannels.value = channels.filter { it in 0 until numChannels }.distinct()
	}

	/** `{start, start + step, ...}` below `stop`; null when cancelled */
	private fun everyNth(): List<Int>? {
		val start = NumberField.intField(0, { it in 0 until numChannels }, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST)
		val stop = NumberField.intField(numChannels, { it in 0..numChannels }, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST)
		val step = NumberField.intField(1, { it > 0 }, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST)
		val grid = GridPane().apply {
			hgap = 5.0
			vgap = 5.0
			add(Label("Start"), 0, 0)
			add(Label("Stop"), 0, 1)
			add(Label("Step"), 0, 2)
			add(start.textField, 1, 0)
			add(stop.textField, 1, 1)
			add(step.textField, 1, 2)
		}
		val dialog = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION, true).apply {
			headerText = "Select every Nth channel: {start, start + step, ...}; start is inclusive, stop is exclusive"
			dialogPane.content = grid
		}
		(dialog.dialogPane.lookupButton(ButtonType.OK) as Button).setOnAction {
			start.submit()
			stop.submit()
			step.submit()
		}
		if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
			return null
		return (start.valueProperty().get() until stop.valueProperty().get() step step.valueProperty().get()).toList()
	}

	companion object {

		/** `0, 2, 4-6` -> [0, 2, 4, 5, 6]; empty text is no channels; null when a token is not an index or range */
		fun parseChannels(text: String): List<Int>? {
			val channels = mutableListOf<Int>()
			for (token in text.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }) {
				val range = token.split('-')
				when (range.size) {
					1 -> channels += range[0].toIntOrNull() ?: return null
					2 -> {
						val from = range[0].toIntOrNull() ?: return null
						val to = range[1].toIntOrNull() ?: return null
						if (from > to)
							return null
						channels += from..to
					}
					else -> return null
				}
			}
			return channels
		}

		fun formatChannels(channels: List<Int>) = channels.joinToString(", ")
	}
}
