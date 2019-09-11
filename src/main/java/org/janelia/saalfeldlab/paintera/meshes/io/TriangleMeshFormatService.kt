package org.janelia.saalfeldlab.paintera.meshes.io

import org.scijava.Context
import org.scijava.InstantiableException
import org.scijava.plugin.Plugin
import org.scijava.plugin.PluginService
import org.scijava.service.AbstractService
import org.scijava.service.SciJavaService
import org.scijava.service.Service
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

@Plugin(type = Service::class)
class TriangleMeshFormatService : AbstractService(), SciJavaService {


	private lateinit var _meshFormats: List<TriangleMeshFormat>

	private lateinit var _extensionToFormatMapping: Map<String, List<TriangleMeshFormat>>

	val meshFormats: List<TriangleMeshFormat>
		@Synchronized get() {
			if (!this::_meshFormats.isInitialized)
				initFormatsAndExtensions()
			return _meshFormats
		}

	val extensionToFormatMapping: Map<String, List<TriangleMeshFormat>>
		@Synchronized get() {
			if (!this::_extensionToFormatMapping.isInitialized)
				initFormatsAndExtensions()
			return _extensionToFormatMapping
		}

	private fun initFormatsAndExtensions() {
		val p = makeMeshFormats(context)
		_meshFormats = p.first
		_extensionToFormatMapping = p.second
	}

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private fun makeMeshFormats(context: Context): Pair<List<TriangleMeshFormat>, Map<String, List<TriangleMeshFormat>>> {
			val pluginService = context.getService(PluginService::class.java)
			val pluginInfos = pluginService.getPluginsOfType(TriangleMeshFormat::class.java)
			val meshFormats = mutableListOf<TriangleMeshFormat>()
			val extensionToFormatMapping = mutableMapOf<String, MutableList<TriangleMeshFormat>>()
			for (pluginInfo in pluginInfos) {
				try {
					val instance = pluginInfo.createInstance();
					meshFormats.add(instance);
					for (ext in instance.knownExtensions())
						extensionToFormatMapping.computeIfAbsent(ext) { mutableListOf() } += instance
				} catch (e: InstantiableException) {
					LOG.error("Unable to instantiate plugin {}", pluginInfo, e);
				}
			}
			return Pair(meshFormats, extensionToFormatMapping)
		}
	}
}
