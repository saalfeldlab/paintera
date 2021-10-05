package org.janelia.saalfeldlab.paintera.data.n5

import com.google.gson.annotations.Expose
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import java.io.IOException

data class N5GenericMeta(
    @field:Expose override val reader: N5Reader,
    @field:Expose override val dataset: String
) : N5Meta {


    override val writer: N5Writer?
        @Throws(IOException::class)
        get() {
            if (reader is N5Writer) {
                return reader
            }
            throw IOException("N5Writer Not Supported!")
        }
}
