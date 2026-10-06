package org.janelia.saalfeldlab.paintera.ui

import net.imglib2.type.label.Label
import org.janelia.saalfeldlab.fx.ui.ObjectField.InvalidUserInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LabelIdTextTest {

	@Test
	fun `entries split on commas and newlines and are trimmed`() {
		assertEquals(listOf(1L, 2L, 3L), LabelIdText.parseIds(" 1, 2\n3 "))
	}

	@Test
	fun `redundant zeroes and decimal are normalized`() {
        /*trailing decimal 0*/
		assertEquals(listOf(12L), LabelIdText.parseIds("12.0"))
		assertEquals(listOf(12L), LabelIdText.parseIds("12.000"))
        /* trailing decimal only*/
		assertEquals(listOf(12L), LabelIdText.parseIds("12."))
        /* leading 0*/
		assertEquals(listOf(7L), LabelIdText.parseIds("007"))
		assertEquals(listOf(0L), LabelIdText.parseIds("0"))
	}

	@Test
	fun `anything but digits is invalid, not rewritten`() {
		for (entry in listOf("-2", "0.123", ".8", "-0.5", "12.5", "1e3", "a", "${Label.INVALID}"))
			assertEquals(emptyList(), LabelIdText.parseIds(entry), entry)
	}

	@Test
	fun `invalid entries are dropped and valid ones kept`() {
		assertEquals(listOf(1L, 3L), LabelIdText.parseIds("1, -2, 3"))
	}

	@Test
	fun `the converters keep the previous value when nothing is valid`() {
		assertFailsWith<InvalidUserInput> { LabelIdsConverter().fromString("-2") }
		assertFailsWith<InvalidUserInput> { LabelIdConverter().fromString("") }
	}

	@Test
	fun `an id that is not regular renders blank`() {
		assertEquals("", LabelIdConverter().toString(Label.INVALID))
		assertEquals("5", LabelIdConverter().toString(5))
	}
}
