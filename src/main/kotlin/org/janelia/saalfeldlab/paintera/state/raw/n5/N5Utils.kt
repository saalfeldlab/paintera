package org.janelia.saalfeldlab.paintera.state.raw.n5

import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.N5Writer
import org.janelia.saalfeldlab.n5.googlecloud.N5GoogleCloudStorageReader
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Reader
import org.janelia.saalfeldlab.paintera.Paintera.Companion.n5Factory
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

	/**
	 * If an n5 container exists at [uri], return it as [N5Writer] if possible, and [N5Reader] if not.
	 *
	 * @param uri the location of the n5 container
	 * @return [N5Writer] or [N5Reader] if container exists
	 */
	@JvmStatic
	fun getReaderOrWriterIfN5ContainerExists(uri: String): N5Reader? {
		val cachedContainer = getReaderOrWriterIfCached(uri)
		return cachedContainer ?: openReaderOrWriterIfContainerExists(uri)
	}

	/**
	 * If an n5 container exists at [uri], return it as [N5Writer] if possible.
	 *
	 * @param uri the location of the n5 container
	 * @return [N5Writer] if container exists and is openable as a writer.
	 */
	@JvmStatic
	fun getWriterIfN5ContainerExists(uri: String): N5Writer? {
		return getReaderOrWriterIfN5ContainerExists(uri) as? N5Writer
	}

	/**
	 * Retrieves a reader or writer for the given container if it exists.
	 *
	 * If the reader is successfully opened, the container must exist, so we try to open a writer.
	 * This is to ensure that an N5 container isn't created by opening as a writer if one doesn't exist.
	 * If opening a writer is not possible it falls back to again as a reader.
	 *
	 * @param container the path to the container
	 * @return a reader or writer as N5Reader, or null if the container does not exist
	 */
	private fun openReaderOrWriterIfContainerExists(container: String) = try {
		n5Factory.openReader(container)?.let {
			var reader: N5Reader? = it
			if (it is N5HDF5Reader) {
				it.close()
			}
			try {
				n5Factory.openWriter(container)
			} catch (_: Exception) {
				reader ?: n5Factory.openReader(container)
			}
		}
	} catch (e: Exception) {
		null
	}

	/**
	 * If there is a cached N5Reader for [container] than try to open a writer (also may be cached),
	 *  or fallback to the cached reader
	 *
	 * @param container The path to the N5 container.
	 * @return The N5Reader instance.
	 */
	private fun getReaderOrWriterIfCached(container: String): N5Reader? {
		val reader: N5Reader? = n5Factory.getFromCache(container)?.let {
			try {
				n5Factory.openWriter(container)
			} catch (_: Exception) {
				it
			}
		}
		return reader
	}
}
