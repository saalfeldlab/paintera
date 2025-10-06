package org.janelia.saalfeldlab.paintera.ui

import javafx.scene.control.TextFormatter
import kotlin.test.Test
import kotlin.test.assertEquals

private var <K> TextFormatter<K>.string: String?
	get() = valueConverter.toString(value)
	set(string) {
		value = valueConverter.run {
			val double = fromString(string)
			val formattedString = toString(double)
			val formattedDouble = fromString(formattedString)
			formattedDouble
		}
	}

class PositiveDoubleTextFormatterTests {

	val formatter = PositiveDoubleTextFormatter()

	@Test
	fun `typical value tests`() {

		// positive value
		formatter.string = "1.2"
		assertEquals("1.2", formatter.string)
		assertEquals(1.2, formatter.value)

		//negative is invalid
		formatter.string = "-1.0"
		assertEquals(null, formatter.string)

		//another positive valid value
		formatter.string = "123.456"
		assertEquals("123.456", formatter.string)
		assertEquals(123.456, formatter.value)

		//invalid is invalid
		formatter.string = "invalid"
		assertEquals(null, formatter.string)
	}

	@Test
	fun `all sig figs are decimal`() {
		formatter.string = "0.123456"
		assertEquals("0.123", formatter.string)
		assertEquals(0.123, formatter.value)
	}

	@Test
	fun `sig figs before decimal`() {
		formatter.string = "123.123456"
		assertEquals("123.123", formatter.string)
		assertEquals(123.123, formatter.value)
	}


	@Test
	fun `truncate insignificant zeros`() {
		for (validIntegerPart in listOf(0, 123)) {
			formatter.string = "$validIntegerPart.000"
			assertEquals("$validIntegerPart", formatter.string)
			assertEquals(validIntegerPart.toDouble(), formatter.value)

			formatter.string = "$validIntegerPart.0001"
			assertEquals("$validIntegerPart", formatter.string)
			assertEquals(validIntegerPart.toDouble(), formatter.value)

			formatter.string = "$validIntegerPart.0001"
			assertEquals("$validIntegerPart", formatter.string)
			assertEquals(validIntegerPart.toDouble(), formatter.value)

			formatter.string = "$validIntegerPart.123000"
			assertEquals("$validIntegerPart.123", formatter.string)
			assertEquals(validIntegerPart.toDouble() + .123, formatter.value)

			formatter.string = "$validIntegerPart.123500"
			assertEquals("$validIntegerPart.124", formatter.string)
			assertEquals(validIntegerPart.toDouble() + .124, formatter.value)
		}
	}

	@Test
	fun `rules for zero`() {
		val zeros = listOf("0.0", "0", ".0", "00.00", "000.000", "0.00000005")
		for (zero in zeros) {
			formatter.string = zero
			assertEquals("0", formatter.string)
			assertEquals(0.0, formatter.value)
		}
	}

	@Test
	fun `requires rounding`() {

		/* requires rounding */
		formatter.string = "123.1239123"
		assertEquals("123.124", formatter.string)
		assertEquals(123.124, formatter.value)

		//Trailing zeros should be truncated
		formatter.string = "123.123000"
		assertEquals("123.123", formatter.string)
		assertEquals(123.123, formatter.value)

		//rounding after zeros
		formatter.string = "0.000999"
		assertEquals("0.001", formatter.string)
		assertEquals(0.001, formatter.value)

		// I don't like this behaviour (I'd prefer 123.001) ,
		// but it's consistent with how BigDecimal.scale(3, HALF_UP) behaves, so we will keep it.
		formatter.string = "123.000499"
		assertEquals("123", formatter.string)
		assertEquals(123.0, formatter.value)

		formatter.string = 99.999999.toString()
		assertEquals(formatter.string, "100")
		assertEquals(formatter.value, 100.0)
	}

	@Test
	fun `leading decimal point`() {

		formatter.string = ".5"
		assertEquals("0.5", formatter.string)
		assertEquals(0.5, formatter.value)
	}
}