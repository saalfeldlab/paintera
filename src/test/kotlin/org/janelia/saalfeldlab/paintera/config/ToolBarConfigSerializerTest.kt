package org.janelia.saalfeldlab.paintera.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ToolBarConfigSerializerTest {

	private val gson = GsonBuilder().registerTypeAdapter(ToolBarConfig::class.java, ToolBarConfigSerializer()).create()

	private fun roundTrip(config: ToolBarConfig) = gson.fromJson(gson.toJsonTree(config), ToolBarConfig::class.java)

	@Test
	fun `default config is not written`() {
		assertEquals(JsonNull.INSTANCE, gson.toJsonTree(ToolBarConfig()))
	}

	@Test
	fun `mode and visibility round trip`() {
		val restored = roundTrip(ToolBarConfig().apply {
			mode = ToolBarConfig.Mode.RIGHT
			isVisible = false
		})
		assertEquals(ToolBarConfig.Mode.RIGHT, restored.mode)
		assertFalse(restored.isVisible)
	}

	@Test
	fun `mode alone round trips`() {
		assertEquals(ToolBarConfig.Mode.RIGHT, roundTrip(ToolBarConfig().apply { mode = ToolBarConfig.Mode.RIGHT }).mode)
	}
}
