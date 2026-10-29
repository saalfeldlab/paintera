package org.janelia.saalfeldlab.paintera.control.paint

import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import org.janelia.saalfeldlab.paintera.control.paint.LabelMaskConfig.SegmentFilterMode
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabelMaskConfigSerializerTest {

	private val gson = GsonBuilder().registerTypeAdapter(LabelMaskConfig::class.java, LabelMaskConfigSerializer()).create()

	private fun roundTrip(config: LabelMaskConfig) = gson.fromJson(gson.toJsonTree(config), LabelMaskConfig::class.java)

	@Test
	fun `default config is not written`() {
		assertEquals(JsonNull.INSTANCE, gson.toJsonTree(LabelMaskConfig()))
		assertNull(roundTrip(LabelMaskConfig()))
	}

	@Test
	fun `only non default values are written`() {
		val json = gson.toJsonTree(LabelMaskConfig().apply { paintOverLockedLabelsProperty.set(true) }).asJsonObject
		assertEquals(setOf("paintOverLockedLabels"), json.keySet())
	}

	@Test
	fun `enabled filter is written as a nested segment filter`() {
		val json = gson.toJsonTree(LabelMaskConfig().apply {
			segmentFilterEnabledProperty.set(true)
			segmentFilterModeProperty.set(SegmentFilterMode.Blacklist)
		}).asJsonObject
		assertEquals(setOf("segmentFilter"), json.keySet())
		val filter = json.getAsJsonObject("segmentFilter")
		assertEquals(setOf("segmentMaskMode", "ids"), filter.keySet())
		assertEquals("Blacklist", filter.get("segmentMaskMode").asString)
	}

	@Test
	fun `all values round trip`() {
		val restored = roundTrip(LabelMaskConfig().apply {
			paintOverLockedLabelsProperty.set(true)
			segmentFilterEnabledProperty.set(true)
			segmentFilterModeProperty.set(SegmentFilterMode.Whitelist)
			segmentIds.activate(7L, 3L, 0L)
		})
		assertTrue(restored.paintOverLockedLabelsProperty.get())
		assertTrue(restored.segmentFilterEnabledProperty.get())
		assertEquals(SegmentFilterMode.Whitelist, restored.segmentFilterModeProperty.get())
		assertContentEquals(longArrayOf(0L, 3L, 7L), restored.segmentIds.activeIdsCopyAsArray.sortedArray())
	}

	@Test
	fun `filter settings are not written while the filter is off`() {
		val config = LabelMaskConfig().apply {
			segmentFilterModeProperty.set(SegmentFilterMode.Whitelist)
			segmentIds.activate(5L)
		}
		assertEquals(JsonNull.INSTANCE, gson.toJsonTree(config))

		config.paintOverLockedLabelsProperty.set(true)
		assertEquals(setOf("paintOverLockedLabels"), gson.toJsonTree(config).asJsonObject.keySet())

		val restored = roundTrip(config)
		assertFalse(restored.segmentFilterEnabledProperty.get())
		assertEquals(SegmentFilterMode.Blacklist, restored.segmentFilterModeProperty.get())
		assertTrue(restored.segmentIds.isEmpty)
	}

	@Test
	fun `unknown mode is ignored`() {
		val json = gson.toJsonTree(LabelMaskConfig().apply {
			segmentFilterEnabledProperty.set(true)
			segmentFilterModeProperty.set(SegmentFilterMode.Whitelist)
		}).asJsonObject
		json.getAsJsonObject("segmentFilter").addProperty("segmentMaskMode", "Nonsense")
		val restored = gson.fromJson(json, LabelMaskConfig::class.java)
		assertTrue(restored.segmentFilterEnabledProperty.get())
		assertEquals(SegmentFilterMode.Blacklist, restored.segmentFilterModeProperty.get())
	}
}
