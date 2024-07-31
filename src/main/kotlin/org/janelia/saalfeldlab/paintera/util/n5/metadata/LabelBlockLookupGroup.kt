package org.janelia.saalfeldlab.paintera.util.n5.metadata

import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.GzipCompression
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataGroup
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadata
import org.janelia.saalfeldlab.paintera.serialization.GsonExtensions.set
import org.janelia.saalfeldlab.util.n5.metadata.MetadataWriter

class LabelBlockLookupGroup(private val parentGroup: String, private val groupName: String, private val numScales: Int, private val labelBlockLookup: LabelBlockLookup) :
	N5MetadataGroup<N5SingleScaleMetadata>, MetadataWriter {

	val children = Array(numScales) { i ->
		N5SingleScaleMetadata(
			"$parentGroup/$groupName/s$i",
			AffineTransform3D(),
			doubleArrayOf(1.0),
			doubleArrayOf(1.0),
			doubleArrayOf(0.0),
			"label-block-lookup",
			labelBlockLookupAttributes
		)
	}
	override fun write(n5: N5Writer) {
		if (!n5.exists(path)) {
			n5.createGroup(path)
		}
		n5[path, "isMultiscale"] = true
		n5[path, "labelBlockLookup"] = labelBlockLookup
		n5[parentGroup, "labelBlockLookup"] = labelBlockLookup
		getChildrenMetadata().forEach {
			n5.createDataset(it.path, it.attributes)
		}
	}

	override fun getPath() = "$parentGroup/$groupName"

	override fun getPaths() = childrenMetadata.map { it.path }.toTypedArray()

	override fun getChildrenMetadata() = children

	companion object {
		private val labelBlockLookupAttributes = DatasetAttributes(longArrayOf(Long.MAX_VALUE), intArrayOf(3), DataType.INT8, GzipCompression())
	}
}
