package org.janelia.saalfeldlab.paintera.util.n5.metadata

import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.GzipCompression
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.universe.metadata.N5MetadataGroup
import org.janelia.saalfeldlab.n5.universe.metadata.N5SingleScaleMetadata
import org.janelia.saalfeldlab.util.n5.metadata.MetadataWriter

internal class LabelBlockLookupGroup(private val basePath: String, private val numScales: Int) :
	N5MetadataGroup<N5SingleScaleMetadata>, MetadataWriter {

	val children: Array<N5SingleScaleMetadata> = let {
		val staging = mutableListOf<N5SingleScaleMetadata>()
		for (i in 0..numScales) {
			staging += N5SingleScaleMetadata(
				"$basePath/s$i",
				AffineTransform3D(),
				doubleArrayOf(1.0),
				doubleArrayOf(1.0),
				doubleArrayOf(0.0),
				"label-block-lookup",
				labelBlockLookupAttributes
			)

		}
		staging.toTypedArray()
	}


	override fun write(n5: N5Writer) {
		if (!n5.exists(path)) {
			n5.createGroup(path)
		}
		n5.setAttribute(path, "isMultiscale", true)
		getChildrenMetadata().forEach {
			n5.createDataset(it.path, it.attributes)

		}
	}

	override fun getPath() = basePath

	override fun getPaths() = childrenMetadata.map { it.path }.toTypedArray()

	override fun getChildrenMetadata() = children

	companion object {
		private val labelBlockLookupAttributes = DatasetAttributes(longArrayOf(Long.MAX_VALUE), intArrayOf(3), DataType.INT8, GzipCompression())
	}
}
