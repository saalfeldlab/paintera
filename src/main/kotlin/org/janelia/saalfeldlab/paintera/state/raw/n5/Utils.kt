package org.janelia.saalfeldlab.paintera.state.raw.n5

import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.googlecloud.N5GoogleCloudStorageReader
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Reader
import org.janelia.saalfeldlab.util.n5.N5Helpers
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

object Utils {

    @JvmStatic
    fun getUrlRepresentation(reader: N5Reader): String {
        return reader.urlRepresentation();
    }

}

fun N5Reader.urlRepresentation() = when (this) {
    //FIXME this needs to be updated to handle other readers
    is N5FSReader -> "$basePath"
    is N5HDF5Reader -> "h5://${filename.absolutePath}"
    is N5AmazonS3Reader -> getS3Url()
    is N5GoogleCloudStorageReader -> getGoogleCloudUrl()
    else -> "??://${toString()}"
}

fun N5AmazonS3Reader.getS3Url(): String {
    val bucketName = N5AmazonS3Reader::class.memberProperties
        .first { it.name == "bucketName" }
        .apply { isAccessible = true }
        .get(this) as String


    val fullPath = N5AmazonS3Reader::class.memberFunctions
        .first { it.name == "getFullPath" }
        .apply { isAccessible = true }
        .call(this, "") as String

    return "s3://$bucketName/$fullPath"
}

fun N5GoogleCloudStorageReader.getGoogleCloudUrl(): String {
    val bucketName = N5GoogleCloudStorageReader::class.memberProperties
        .first { it.name == "bucketName" }
        .apply { isAccessible = true }
        .get(this) as String


    val fullPath = N5GoogleCloudStorageReader::class.memberFunctions
        .first { it.name == "getFullPath" }
        .apply { isAccessible = true }
        .call(this, "") as String

    return "gs://$bucketName/$fullPath"
}

fun getReaderOrWriterIfN5Container(container: String): N5Reader? {
    var reader: N5Reader? = null
    return try {
        reader = N5Helpers.n5Reader(container)
        N5Helpers.n5Writer(container)
    } catch (e: Exception) {
        reader
    }
}
