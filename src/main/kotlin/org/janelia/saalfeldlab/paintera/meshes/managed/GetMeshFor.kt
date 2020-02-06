package org.janelia.saalfeldlab.paintera.meshes.managed

import net.imglib2.cache.Cache
import net.imglib2.cache.CacheLoader
import net.imglib2.cache.Invalidate
import net.imglib2.cache.LoaderCache
import net.imglib2.cache.ref.SoftRefLoaderCache
import net.imglib2.util.Pair
import org.janelia.saalfeldlab.paintera.meshes.PainteraTriangleMesh
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey

interface GetMeshFor<Key> {
    fun getMeshFor(key: ShapeKey<Key>): PainteraTriangleMesh?

    class FromCache<Key>(private val cache: Cache<ShapeKey<Key>?, PainteraTriangleMesh?>)
        : GetMeshFor<Key>, Invalidate<ShapeKey<Key>?> by cache {
        override fun getMeshFor(key: ShapeKey<Key>) = cache[key]

        companion object {
            @JvmStatic
            fun <Key> from(cache: Cache<ShapeKey<Key>?, PainteraTriangleMesh?>) = FromCache(cache)

            @JvmStatic
            @JvmOverloads
            fun <Key> fromLoader(
                loader: CacheLoader<ShapeKey<Key>?, PainteraTriangleMesh?>,
                cache: LoaderCache<ShapeKey<Key>?, PainteraTriangleMesh?> = SoftRefLoaderCache()) = from(cache.withLoader(loader))

            @JvmStatic
            @JvmOverloads
            fun <Key> fromLoaders(
                vararg loader: CacheLoader<ShapeKey<Key>?, PainteraTriangleMesh?>,
                cache: LoaderCache<ShapeKey<Key>?, PainteraTriangleMesh?> = SoftRefLoaderCache()) = fromLoader(
                CacheLoader { key: ShapeKey<Key>? -> key?.let { loader[it.scaleIndex()][it] } },
                cache)

            @JvmStatic
            @JvmOverloads
            fun <Key> fromPairLoader(
                loader: CacheLoader<ShapeKey<Key>?, Pair<FloatArray, FloatArray>?>,
                cache: LoaderCache<ShapeKey<Key>?, PainteraTriangleMesh?> = SoftRefLoaderCache()) = from(cache.withLoader(loader.asPainteraTriangleMeshLoader()))

            @JvmStatic
            @JvmOverloads
            fun <Key> fromPairLoaders(
                vararg loader: CacheLoader<ShapeKey<Key>?, Pair<FloatArray, FloatArray>?>,
                cache: LoaderCache<ShapeKey<Key>?, PainteraTriangleMesh?> = SoftRefLoaderCache()) = fromPairLoader(
                CacheLoader { key: ShapeKey<Key>? -> key?.let { loader[it.scaleIndex()][it] } },
                cache)

            private fun <Key> CacheLoader<ShapeKey<Key>?, Pair<FloatArray, FloatArray>?>.asPainteraTriangleMeshLoader() = CacheLoader { key: ShapeKey<Key>? ->
                key?.let { k -> this[k]?.let { PainteraTriangleMesh(it.a, it.b) } }
            }
        }
    }
}
