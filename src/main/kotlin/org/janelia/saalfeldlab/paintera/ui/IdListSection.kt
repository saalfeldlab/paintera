package org.janelia.saalfeldlab.paintera.ui

import javafx.beans.InvalidationListener
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.Pos
import javafx.scene.control.MenuButton
import javafx.scene.control.MenuItem
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.util.Subscription
import net.imglib2.type.label.Label
import org.controlsfx.control.ToggleSwitch
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.control.paint.LabelMaskConfig.SegmentFilterMode
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds

internal class IdListSection : VBox(3.0) {

	private val enabledSwitch = ToggleSwitch("Segment Filter")
	private val group = ToggleGroup()
	private val modeButtons = SegmentFilterMode.entries.associateWith { mode ->
		ToggleButton(mode.label).apply {
			toggleGroup = group
			userData = mode
		}
	}
	private val field = ObjectField(
		SimpleObjectProperty(longArrayOf()),
		LabelIdsConverter(allowBackground = true, allowEmpty = true),
		*ObjectField.SubmitOn.entries.toTypedArray(),
	).apply {
		textField.promptText = "comma separated ids"
		HBox.setHgrow(textField, Priority.ALWAYS)
	}

	private var enabledProperty: BooleanProperty? = null
	private var modeProperty: ObjectProperty<SegmentFilterMode>? = null
	private var modeSubscription: Subscription? = null
	private var ids: SelectedIds? = null
	private var currentSelection: () -> LongArray = { longArrayOf() }
	private var updating = false

	private val idsListener = InvalidationListener { InvokeOnJavaFXApplicationThread { updateField() } }

	init {
		val presets = MenuButton().apply {
			items.setAll(
				MenuItem("Current Selection").apply { setOnAction { ids?.activate(*currentSelection()) } },
				MenuItem("Background").apply { setOnAction { ids?.activate(Label.BACKGROUND) } },
				MenuItem("Clear").apply { setOnAction { ids?.deactivateAll() } }
			)
		}
		val modeRow = HBox(0.0).apply {
			children += modeButtons.values
			modeButtons.values.forEach {
				it.maxWidth = Double.MAX_VALUE
				HBox.setHgrow(it, Priority.ALWAYS)
			}
		}
		val idsRow = HBox(5.0, field.textField, presets).apply { alignment = Pos.CENTER_LEFT }
		val filterSettings = VBox(3.0, modeRow, idsRow).apply { disableProperty().bind(enabledSwitch.selectedProperty().not()) }
		children += listOf(enabledSwitch, filterSettings)

		group.selectedToggleProperty().subscribe { old, new ->
			if (new == null)
				old?.let { group.selectToggle(it) }
			else
				modeProperty?.set(new.userData as SegmentFilterMode)
		}
		group.selectToggle(modeButtons[SegmentFilterMode.Blacklist])

		field.valueProperty().subscribe { _, ids ->
			if (updating)
				return@subscribe
			val boundIds = this@IdListSection.ids ?: return@subscribe
			if (ids.isEmpty())
				boundIds.deactivateAll()
			else
				boundIds.activate(*ids)
		}
		isDisable = true
	}

	/** Binds [filterEnabled], [mode] and [segmentIds], or unbinds with nulls */
	fun bind(
		filterEnabled: BooleanProperty?,
		mode: ObjectProperty<SegmentFilterMode>?,
		segmentIds: SelectedIds?,
		currentSelection: () -> LongArray = { longArrayOf() }
	) {
		modeSubscription?.unsubscribe()
		enabledProperty?.let { enabledSwitch.selectedProperty().unbindBidirectional(it) }
		ids?.removeListener(idsListener)

		modeProperty = null
		ids = segmentIds
		this.currentSelection = currentSelection

		enabledProperty = filterEnabled?.also { enabledSwitch.selectedProperty().bindBidirectional(it) }
		mode?.value?.let { group.selectToggle(modeButtons[it]) }
		modeProperty = mode

		modeSubscription = mode?.subscribe { selected -> group.selectToggle(modeButtons[selected]) }
		segmentIds?.addListener(idsListener)
		updateField()
		isDisable = filterEnabled == null || mode == null || segmentIds == null
	}

	private fun updateField() {
		updating = true
		try {
			field.value = ids?.activeIdsCopyAsArray?.sortedArray() ?: longArrayOf()
		} finally {
			updating = false
		}
	}
}
