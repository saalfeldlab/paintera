package org.janelia.saalfeldlab.paintera.ui

import javafx.beans.InvalidationListener
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
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.control.paint.PaintMaskConfig.SegmentMaskMode
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds

internal class IdListSection : VBox(3.0) {

	private val group = ToggleGroup()
	private val modeButtons = (listOf<SegmentMaskMode?>(null) + SegmentMaskMode.entries).associateWith { mode ->
		ToggleButton(mode?.label ?: "None").apply {
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

	private var modeProperty: ObjectProperty<SegmentMaskMode?>? = null
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
		children += HBox(0.0).apply {
			children += modeButtons.values
			modeButtons.values.forEach {
				it.maxWidth = Double.MAX_VALUE
				HBox.setHgrow(it, Priority.ALWAYS)
			}
		}
		children += HBox(5.0, field.textField, presets).apply { alignment = Pos.CENTER_LEFT }

		/* one button is always selected; clicking the selected one does not clear it */
		group.selectedToggleProperty().subscribe { old, new ->
			if (new == null)
				old?.let { group.selectToggle(it) }
			else
				modeProperty?.set(new.userData as SegmentMaskMode?)
		}
		group.selectToggle(modeButtons[null])

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

	/** Binds [mode] and [ids], or unbinds with nulls */
	fun bind(mode: ObjectProperty<SegmentMaskMode?>?, ids: SelectedIds?, currentSelection: () -> LongArray = { longArrayOf() }) {
		modeSubscription?.unsubscribe()
		ids?.removeListener(idsListener)

		modeProperty = mode
		this@IdListSection.ids = ids
		this.currentSelection = currentSelection

		modeSubscription = mode?.subscribe { selected -> group.selectToggle(modeButtons[selected]) }
		ids?.addListener(idsListener)
		updateField()
		isDisable = mode == null || ids == null
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
