package org.janelia.saalfeldlab.paintera.meshes.managed

import net.imglib2.cache.Cache
import net.imglib2.cache.CacheLoader
import net.imglib2.cache.Invalidate
import net.imglib2.cache.LoaderCache
import net.imglib2.cache.ref.SoftRefLoaderCache
import org.janelia.saalfeldlab.paintera.meshes.PainteraTriangleMesh
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey
import java.util.function.Predicate

interface GetMeshFor<Key> {
	fun getMeshFor(key: ShapeKey<Key>): PainteraTriangleMesh?

	class MeshCacheWrapper<Key>(private val cache: Cache<ShapeKey<Key>?, PainteraTriangleMesh?>) : Cache<ShapeKey<Key>?, PainteraTriangleMesh?> by cache{
		override fun invalidate(key: ShapeKey<Key>?) {
			cache.invalidate(key)
		}

		override fun invalidateIf(parallelismThreshold: Long, condition: Predicate<ShapeKey<Key>?>?) {
			cache.invalidateIf(parallelismThreshold, condition)
		}

		override fun invalidateAll(parallelismThreshold: Long) {
			cache.invalidateAll(parallelismThreshold)
		}

		override fun invalidateIf(condition: Predicate<ShapeKey<Key>?>?) {
			cache.invalidateIf(condition)
		}

		override fun invalidateAll() {
			cache.invalidateAll()
		}
	}

	class FromCache<Key>(private val cache: Cache<ShapeKey<Key>?, PainteraTriangleMesh?>) : GetMeshFor<Key>, Invalidate<ShapeKey<Key>?> by MeshCacheWrapper(cache) {
		override fun getMeshFor(key: ShapeKey<Key>) = cache[key]

		companion object {
			@JvmStatic
			fun <Key> from(cache: Cache<ShapeKey<Key>?, PainteraTriangleMesh?>) = FromCache(cache)

			@JvmStatic
			@JvmOverloads
			fun <Key> fromLoader(
				loader: CacheLoader<ShapeKey<Key>?, PainteraTriangleMesh?>,
				cache: LoaderCache<ShapeKey<Key>?, PainteraTriangleMesh?> = SoftRefLoaderCache()
			) = from(cache.withLoader(loader))

			@JvmStatic
			@JvmOverloads
			fun <Key> fromLoaders(
				vararg loader: CacheLoader<ShapeKey<Key>?, PainteraTriangleMesh?>,
				cache: LoaderCache<ShapeKey<Key>?, PainteraTriangleMesh?> = SoftRefLoaderCache()
			) = fromLoader(
				{ key: ShapeKey<Key>? -> key?.let { loader[it.scaleIndex()][it] } },
				cache
			)
		}
	}
}
