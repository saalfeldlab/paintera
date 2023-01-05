package org.janelia.saalfeldlab.paintera.data.n5

import com.google.gson.annotations.Expose
import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.paintera.Paintera.Companion.n5Factory
import java.io.IOException

data class N5FSMeta(
    @field:Expose private val n5: String,
    @field:Expose override val dataset: String,
) : N5Meta {

    @Transient private var _writer : N5Writer? = null

    @get:Throws(IOException::class)
    override val writer: N5Writer? by lazy { _writer ?: n5Factory.openFSWriter(n5) }

    @get:Throws(IOException::class)
    override val reader: N5Reader by lazy { writer!! }

    @Throws(ReflectionException::class)
    constructor(reader: N5FSReader, dataset: String) : this(fromReader(reader), dataset) {
        _writer = reader as? N5Writer
    }

    fun basePath() = n5

    companion object {
        @Throws(ReflectionException::class)
        private fun fromReader(reader: N5FSReader): String {

            try {
                return ReflectionHelpers.searchForField(reader.javaClass, "basePath").get(reader) as String
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
