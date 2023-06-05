package org.janelia.saalfeldlab.paintera.state.raw.n5

import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.googlecloud.N5GoogleCloudStorageReader
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Reader
import org.janelia.saalfeldlab.util.n5.N5Helpers
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

object N5Utils {

	@JvmStatic
	fun N5Reader.urlRepresentation(): String = when (this) {
		//FIXME this needs to be updated to handle other readers
		is N5FSReader -> basePath
		is N5HDF5Reader -> "h5://${filename.absolutePath}"
		is N5AmazonS3Reader -> getS3Url()
		is N5GoogleCloudStorageReader -> getGoogleCloudUrl()
		else -> "??://${toString()}"
	}

	@JvmStatic
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

	@JvmStatic
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

	@JvmStatic
	fun getReaderOrWriterIfN5ContainerExists(container: String): N5Reader? {
		var reader: N5Reader? = null
		return try {
			N5Helpers.n5Reader(container)?.let {
				reader = it
				if (it is N5HDF5Reader) {
					it.close()
					reader = null
				}
				N5Helpers.n5Writer(container)
			}
		} catch (e: Exception) {
			reader ?: N5Helpers.n5Reader(container)
		}
	}

	@JvmStatic
	fun getWriterIfN5ContainerExists(container: String): N5Writer? {
		return try {
			N5Helpers.n5Reader(container)?.let {
				if (it is N5HDF5Reader) {
					it.close()
				}
				N5Helpers.n5Writer(container)
			}
		} catch (e: Exception) {
			null
		}
	}
}
