package org.janelia.saalfeldlab.paintera.data.n5

import com.google.gson.annotations.Expose
import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5FSWriter
import java.io.IOException

data class N5FSMeta(
    @field:Expose private val n5: String,
    @field:Expose override val dataset: String
) : N5Meta {

    override val reader: N5FSReader
        @Throws(IOException::class)
        get() = N5FSReader(n5)

    override val writer: N5FSWriter
        @Throws(IOException::class)
        get() = N5FSWriter(n5)

    @Throws(ReflectionException::class)
    constructor(reader: N5FSReader, dataset: String) : this(fromReader(reader), dataset)

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
