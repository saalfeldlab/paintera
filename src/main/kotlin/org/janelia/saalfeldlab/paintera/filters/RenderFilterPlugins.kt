package org.janelia.saalfeldlab.paintera.filters

import io.github.oshai.kotlinlogging.KotlinLogging
import org.scijava.Context
import org.scijava.InstantiableException
import org.scijava.plugin.PluginService

/** The [RenderFilterPlugin]s on the classpath, highest priority first */
object RenderFilterPlugins {

	private val LOG = KotlinLogging.logger { }

	val all: List<RenderFilterPlugin> by lazy {
		val context = Context(PluginService::class.java)
		try {
			val found = mutableListOf<RenderFilterPlugin>()
			for (pluginInfo in context.getService(PluginService::class.java).getPluginsOfType(RenderFilterPlugin::class.java)) {
				try {
					val plugin = pluginInfo.createInstance()
					if (found.any { it.typeKey == plugin.typeKey })
						LOG.warn { "Ignoring render filter plugin ${pluginInfo.className}; its type key ${plugin.typeKey} is already taken" }
					else {
						LOG.info { "Found render filter plugin ${plugin.typeKey} (${pluginInfo.className})" }
						found += plugin
					}
				} catch (e: InstantiableException) {
					LOG.error(e) { "Unable to instantiate render filter plugin $pluginInfo" }
				}
			}
			found
		} finally {
			context.dispose()
		}
	}

	fun forTypeKey(typeKey: String) = all.firstOrNull { it.typeKey == typeKey }

	fun forConfig(config: RenderFilterConfig) = forTypeKey(config.typeKey) ?: throw IllegalStateException("No render filter plugin for ${config.typeKey}")

	fun newConfigs() = all.map { it.newConfig() }
}
