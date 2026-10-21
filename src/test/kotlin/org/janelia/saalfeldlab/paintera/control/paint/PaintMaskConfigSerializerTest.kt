package org.janelia.saalfeldlab.paintera.control.paint

import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import org.janelia.saalfeldlab.paintera.control.paint.PaintMaskConfig.SegmentMaskMode
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaintMaskConfigSerializerTest {

	private val gson = GsonBuilder().registerTypeAdapter(PaintMaskConfig::class.java, PaintMaskConfigSerializer()).create()

	private fun roundTrip(config: PaintMaskConfig) = gson.fromJson(gson.toJsonTree(config), PaintMaskConfig::class.java)

	@Test
	fun `default config is not written`() {
		assertEquals(JsonNull.INSTANCE, gson.toJsonTree(PaintMaskConfig()))
		assertNull(roundTrip(PaintMaskConfig()))
	}

	@Test
	fun `only non default values are written`() {
		val json = gson.toJsonTree(PaintMaskConfig().apply { paintOverLockedLabelsProperty.set(true) }).asJsonObject
		assertEquals(setOf("paintOverLockedLabels"), json.keySet())
	}

	@Test
	fun `all values round trip`() {
		val restored = roundTrip(PaintMaskConfig().apply {
			paintOverLockedLabelsProperty.set(true)
			segmentMaskModeProperty.set(SegmentMaskMode.Blacklist)
			ids.activate(7L, 3L, 0L)
		})
		assertTrue(restored.paintOverLockedLabelsProperty.get())
		assertEquals(SegmentMaskMode.Blacklist, restored.segmentMaskModeProperty.get())
		assertContentEquals(longArrayOf(0L, 3L, 7L), restored.ids.activeIdsCopyAsArray.sortedArray())
	}

	@Test
	fun `ids round trip without a mode`() {
		val restored = roundTrip(PaintMaskConfig().apply { ids.activate(5L) })
		assertFalse(restored.paintOverLockedLabelsProperty.get())
		assertEquals(null, restored.segmentMaskModeProperty.get())
		assertContentEquals(longArrayOf(5L), restored.ids.activeIdsCopyAsArray)
	}

	@Test
	fun `unknown mode is ignored`() {
		val json = gson.toJsonTree(PaintMaskConfig().apply { segmentMaskModeProperty.set(SegmentMaskMode.Whitelist) }).asJsonObject
		json.addProperty("segmentMaskMode", "Nonsense")
		assertEquals(null, gson.fromJson(json, PaintMaskConfig::class.java).segmentMaskModeProperty.get())
	}
}
