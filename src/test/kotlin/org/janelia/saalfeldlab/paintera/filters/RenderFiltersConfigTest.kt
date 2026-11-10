package org.janelia.saalfeldlab.paintera.filters

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import javafx.scene.control.Label
import org.janelia.saalfeldlab.paintera.filters.clahe.Clahe
import org.janelia.saalfeldlab.paintera.filters.clahe.ClaheConfig
import org.janelia.saalfeldlab.paintera.filters.clahe.ClaheConfigAdapter
import org.janelia.saalfeldlab.paintera.filters.clahe.ClaheModel
import org.janelia.saalfeldlab.paintera.filters.depthblur.DepthBlur
import org.janelia.saalfeldlab.paintera.filters.depthblur.DepthBlurConfig
import org.janelia.saalfeldlab.paintera.filters.depthblur.DepthBlurConfigAdapter
import org.janelia.saalfeldlab.paintera.filters.depthblur.DepthBlurConfigModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RenderFiltersConfigTest {

	private val gson = GsonBuilder()
		.registerTypeAdapter(DepthBlurConfig::class.java, DepthBlurConfigAdapter())
		.registerTypeAdapter(ClaheConfig::class.java, ClaheConfigAdapter())
		.registerTypeAdapter(RenderFiltersConfig::class.java, RenderFiltersConfigAdapter())
		.create()

	private fun modelOf(config: RenderFiltersConfig) = RenderFiltersModel(config, config.filterConfigs.map { filterConfig ->
		when (filterConfig) {
			is DepthBlurConfig -> RenderFiltersModel.Entry(DepthBlurConfigModel(filterConfig)) { Label() }
			else -> RenderFiltersModel.Entry(ClaheModel(filterConfig as ClaheConfig)) { Label() }
		}
	})

	@Test
	fun `the filters follow the entries' order and enabled state`() {
		val config = RenderFiltersConfig()
		val filters = modelOf(config)
		assertTrue(filters.filtersProperty.value.isEmpty())
		config.filterConfigs.forEach { it.enabledProperty.set(true) }
		assertEquals(listOf(DepthBlur::class, Clahe::class), filters.filtersProperty.value.map { it::class })
		config.filterConfigs.add(0, config.filterConfigs.removeAt(1))
		assertEquals(listOf(Clahe::class, DepthBlur::class), filters.filtersProperty.value.map { it::class })
		config.filterConfigs[0].enabledProperty.set(false)
		assertEquals(listOf(DepthBlur::class), filters.filtersProperty.value.map { it::class })
	}

	@Test
	fun `order and settings survive a save and load`() {
		val saved = RenderFiltersConfig()
		saved.filterConfigs.add(0, saved.filterConfigs.removeAt(1))
		(saved.filterConfigs[0] as ClaheConfig).radiusProperty.set(7.0)
		saved.filterConfigs[0].enabledProperty.set(true)
		(saved.filterConfigs[1] as DepthBlurConfig).enabledProperty.set(true)
		val json = gson.toJsonTree(saved)

		val loaded = RenderFiltersConfig()
		val filters = modelOf(loaded)
		loaded.set(gson.fromJson(json, RenderFiltersConfig::class.java))
		assertEquals(listOf("clahe", "depthBlur"), loaded.filterConfigs.map { it.typeKey })
		assertEquals(7.0, (loaded.filterConfigs[0] as ClaheConfig).radiusProperty.get(), 1e-12)
		assertEquals(listOf(Clahe::class, DepthBlur::class), filters.filtersProperty.value.map { it::class })

		/* an entry the project does not know keeps its place after the saved ones */
		val partial = gson.fromJson(JsonArray().also { it.add(json.asJsonArray[0]) }, RenderFiltersConfig::class.java)
		assertEquals(listOf("clahe", "depthBlur"), partial.filterConfigs.map { it.typeKey })
	}
}
