package org.janelia.saalfeldlab.paintera.state.metadata

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.janelia.saalfeldlab.util.n5.metadata.N5PainteraLabelMultiScaleGroup

object MetadataExtensions {

	private interface JsonKeyValueProperty {
		val key: String
		val value: JsonElement?
	}

	private enum class PainteraLabelConstants(override val key: String, override val value: JsonElement? = null) : JsonKeyValueProperty {
		TypeLabel("type", "label");

		constructor(key: String, value: String) : this(key, )
	}

	private enum class PainteraLabelGroup(val group: String) {
		Data("data"),
		UniqueLabels("unique-labels")
	}

	@JvmStatic
	fun N5PainteraLabelMultiScaleGroup.createGroup(writer: N5Writer, group: String) {
		val groupProperties = JsonObject().let {
			it["type"] = "label"
		}
	}
}