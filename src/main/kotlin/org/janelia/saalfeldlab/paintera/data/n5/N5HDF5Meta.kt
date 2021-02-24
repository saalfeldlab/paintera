package org.janelia.saalfeldlab.paintera.data.n5

import ch.systemsx.cisd.hdf5.IHDF5Reader
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
        ihdfReaderFromReader(reader).file.absolutePath,
        dataset,
        defaultBlockSizeFromReader(reader),
        overrideBlockSizeFromReader(reader)
    )

    // TODO this needs to be a reader eventually
    override val reader: N5HDF5Reader
        @Throws(IOException::class)
        get() = writer

    override val writer: N5HDF5Writer
        @Throws(IOException::class)
        get() = defaultCellDimensions?.let { N5HDF5Writer(file, *it) } ?: N5HDF5Writer(file)

    override fun toString() = String.format("{N5HDF5: container=%s dataset=%s}", file, dataset)

    companion object {
        @Throws(ReflectionException::class)
        private fun ihdfReaderFromReader(reader: N5HDF5Reader): IHDF5Reader {

            try {
                return ReflectionHelpers.searchForField(reader.javaClass, "reader").get(reader) as IHDF5Reader
            } catch (e: IllegalArgumentException) {
                throw ReflectionException(e)
            } catch (e: IllegalAccessException) {
                throw ReflectionException(e)
            } catch (e: NoSuchFieldException) {
                throw ReflectionException(e)
            } catch (e: SecurityException) {
                throw ReflectionException(e)
            }

        }

        @Throws(ReflectionException::class)
        private fun defaultBlockSizeFromReader(reader: N5HDF5Reader): IntArray {

            try {
                return ReflectionHelpers.searchForField(reader.javaClass, "defaultBlockSize").get(reader) as IntArray
            } catch (e: IllegalArgumentException) {
                throw ReflectionException(e)
            } catch (e: IllegalAccessException) {
                throw ReflectionException(e)
            } catch (e: NoSuchFieldException) {
                throw ReflectionException(e)
            } catch (e: SecurityException) {
                throw ReflectionException(e)
            }

        }

        @Throws(ReflectionException::class)
        private fun overrideBlockSizeFromReader(reader: N5HDF5Reader): Boolean {

            try {
                return ReflectionHelpers.searchForField(reader.javaClass, "overrideBlockSize").get(reader) as Boolean
            } catch (e: IllegalArgumentException) {
                throw ReflectionException(e)
            } catch (e: IllegalAccessException) {
                throw ReflectionException(e)
            } catch (e: NoSuchFieldException) {
                throw ReflectionException(e)
            } catch (e: SecurityException) {
                throw ReflectionException(e)
            }

        }
    }

}
