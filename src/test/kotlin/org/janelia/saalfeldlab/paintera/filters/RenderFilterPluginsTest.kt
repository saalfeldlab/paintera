package org.janelia.saalfeldlab.paintera.filters

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RenderFilterPluginsTest {

	@Test
	fun `the built in filters are plugins, in order`() {
		assertEquals(listOf("depthBlur", "clahe"), RenderFilterPlugins.all.map { it.typeKey }.take(2))
	}

	@Test
	fun `a plugin makes the config of its type key`() {
		RenderFilterPlugins.all.forEach { plugin -> assertEquals(plugin.typeKey, plugin.newConfig().typeKey) }
		assertNull(RenderFilterPlugins.forTypeKey("noSuchFilter"))
	}
}
