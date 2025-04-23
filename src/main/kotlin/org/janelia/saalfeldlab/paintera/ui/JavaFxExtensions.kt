package org.janelia.saalfeldlab.paintera.ui

import javafx.scene.Node
import javafx.scene.control.TextFormatter
import javafx.scene.layout.HBox.setHgrow
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox.setVgrow
import javafx.util.StringConverter


class PositiveLongTextFormatter(initialValue: Long? = null, customMapping: ((String?) -> String?)? = null) : TextFormatter<Long?>(
	object : StringConverter<Long?>() {
		override fun toString(`object`: Long?) = `object`?.takeIf { it >= 0 }?.let { "$it" }
		override fun fromString(string: String?) = string?.toLongOrNull()
	},
	initialValue,
	{
		it.apply {
			customMapping?.invoke(controlNewText)?.let { mapped ->
				setRange(0, controlText.length)
				text = mapped.takeIf { it == "" || it.runCatching { toLong() }.isSuccess } ?: ""
			} ?: let {
				if (controlNewText.runCatching { toLong() }.isFailure)
					text = ""
			}
		}
		it
	}
)

class PositiveDoubleTextFormatter(initialValue: Double? = null, format: String? = "%.3f") : TextFormatter<Double>(
	object : StringConverter<Double>() {
		override fun toString(`object`: Double?) = `object`?.takeIf { it >= 0 }?.let { format?.format(it) ?: "$it" }
		override fun fromString(string: String?) = string?.toDoubleOrNull()
	},
	initialValue,
	{ it.apply { if (controlNewText?.toDoubleOrNull() == null) text = "" } }
)

fun <T : Node> T.hGrow(priority: Priority = Priority.ALWAYS, apply: (T.() -> Unit)? = { } ): T {
	setHgrow(this, priority)
	apply?.invoke(this)
	return this
}

fun <T : Node> T.vGrow(priority: Priority = Priority.ALWAYS, apply: (T.() -> Unit)? = { }): T {
	setVgrow(this, priority)
	apply?.invoke(this)
	return this
}

fun <T : Node> T.hvGrow(apply: (T.() -> Unit)? = { }): T {
	hGrow()
	vGrow()
	apply?.invoke(this)
	return this
}