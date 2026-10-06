package org.janelia.saalfeldlab.paintera.ui

import javafx.util.StringConverter
import net.imglib2.type.label.Label
import org.janelia.saalfeldlab.fx.ui.ObjectField.InvalidUserInput

/**
 * Label ids typed into a text field. Entries are split on commas and newlines and trimmed; an entry is
 * only digits, optionally with a trailing `.` or `.0*`, and leading zeros are dropped. Invalid entries are ignored.
 */
object LabelIdText {

	private val ID_ENTRY = Regex("""0*(\d+)(?:\.0*)?""")

	fun parseIds(text: String?): List<Long> = text.orEmpty()
		.split(',', '\n')
		.map { it.trim() }
		.filter { it.isNotEmpty() }
		.mapNotNull { entry -> ID_ENTRY.matchEntire(entry)?.groupValues?.get(1)?.toULongOrNull()?.toLong() }
		.filter { Label.regular(it) }

	fun parseId(text: String?): Long? = parseIds(text).firstOrNull()

	fun format(ids: LongArray): String = ids.joinToString(",")

	fun format(id: Long?): String = id?.takeIf { Label.regular(it) }?.toString() ?: ""
}

/** For an `ObjectField`: throws [InvalidUserInput] when no entry is valid, so the field keeps its value. */
open class LabelIdsConverter : StringConverter<LongArray>() {

	override fun toString(ids: LongArray?): String = ids?.let { LabelIdText.format(it) } ?: ""

	override fun fromString(string: String?): LongArray = LabelIdText.parseIds(string)
		.ifEmpty { throw InvalidUserInput("No valid id in: $string") }
		.toLongArray()
}

/** For an `ObjectField` holding one id; an id that is not [Label.regular] renders blank. */
open class LabelIdConverter : StringConverter<Long>() {

	override fun toString(id: Long?): String = LabelIdText.format(id)

	override fun fromString(string: String?): Long = LabelIdText.parseId(string) ?: throw InvalidUserInput("Not a valid id: $string")
}
