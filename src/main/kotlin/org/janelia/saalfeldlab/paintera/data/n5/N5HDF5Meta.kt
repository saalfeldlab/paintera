package org.janelia.saalfeldlab.paintera.data.n5

import com.google.gson.annotations.Expose
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer
import java.io.IOException

class N5HDF5Meta(
    @field:Expose val file: String,
    @field:Expose override val dataset: String,
    @field:Expose private val defaultCellDimensions: IntArray?,
    @field:Expose val isOverrideCellDimensions: Boolean
) : N5Meta {

    val defaultCellDimensionsCopy: IntArray?
        get() = defaultCellDimensions?.clone()

    @Throws(ReflectionException::class)
    constructor(reader: N5HDF5Reader, dataset: String) : this(
        reader.filename.absolutePath,
        dataset,
        reader.defaultBlockSizeCopy,
        reader.doesOverrideBlockSize()
    )

    //FIXME can/should we generify N5Meta rn?
    // TODO this needs to be a reader eventually
    override val reader: N5HDF5Reader
        @Throws(IOException::class)
        get() = writer

    override val writer: N5HDF5Writer
        @Throws(IOException::class)
        get() = defaultCellDimensions?.let { N5HDF5Writer(file, *it) } ?: N5HDF5Writer(file)

    override fun toString() = String.format("{N5HDF5: container=%s dataset=%s}", file, dataset)
}
