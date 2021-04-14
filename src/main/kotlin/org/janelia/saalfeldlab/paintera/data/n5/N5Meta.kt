package org.janelia.saalfeldlab.paintera.data.n5

import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.invoke.MethodHandles

interface N5Meta {

    // TODO: Remove the deprecated functions when https://youtrack.jetbrains.com/issue/KT-40609 is closed. Otherwise,
    //  The above property getter will now indicate in intelliJ that getWriter() doesn't throw IOException when used in Java.
    //  This requires you to NOT catch IOException, which is less than ideal.

    @get:Throws(IOException::class)
    val reader: N5Reader

    @Throws(IOException::class)
    @Deprecated("Use property syntax instead", replaceWith = ReplaceWith("reader"))
    fun reader() = reader

    @get:Throws(IOException::class)
    val writer: N5Writer

    @Throws(IOException::class)
    @Deprecated("Use property syntax instead", replaceWith = ReplaceWith("writer"))
    fun writer() = writer

    val dataset: String

    @get:Throws(IOException::class)
    val datasetAttributes: DatasetAttributes
        get() = reader.getDatasetAttributes(dataset)

    @Throws(IOException::class)
    @Deprecated("Use property syntax instead", replaceWith = ReplaceWith("datasetAttributes"))
    fun datasetAttributes() = datasetAttributes

    companion object {

        val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

        @Throws(ReflectionException::class)
        @JvmStatic
        fun fromReader(reader: N5Reader, dataset: String): N5Meta? {
            if (reader is N5FSReader) {
                return N5FSMeta(reader, dataset)
            }

            if (reader is N5HDF5Reader) {
                return N5HDF5Meta(reader, dataset)
            }

            LOG.debug("Cannot create specific meta for reader of type {}. Using generic", reader.javaClass.name)

            /* FIXME is this sufficient? I think this needs to be migrated to proper metadata detection, similar to what
            *   Is done in n5-ij; I think this will be provided when we switch to deepList and the metadata detection from there. */
            return N5GenericMeta(reader, dataset)
        }
    }

}
