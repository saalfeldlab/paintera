package org.janelia.saalfeldlab.paintera.state

import net.imglib2.cache.Invalidate
import org.apache.commons.lang.builder.HashCodeBuilder
import org.janelia.saalfeldlab.paintera.meshes.PainteraTriangleMesh
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey
import org.janelia.saalfeldlab.paintera.meshes.managed.GetMeshFor
import java.util.function.Predicate

interface MeshCacheKey {

    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int

    override fun toString(): String

}

data class IntersectingSourceStateMeshCacheKey<K1 : MeshCacheKey, K2 : MeshCacheKey>(val firstKey: K1, val secondKey: K2) : MeshCacheKey {

    override fun equals(other: Any?): Boolean {
        return (other as? IntersectingSourceStateMeshCacheKey<*, *>)?.let {
            firstKey == it.firstKey && secondKey == it.secondKey
        } ?: let {
            false
        }
    }

    override fun hashCode(): Int {
        return HashCodeBuilder()
            .append(firstKey)
            .append(secondKey)
            .toHashCode()
    }

    override fun toString(): String {
        return "IntersectingSourceMeshCacheKey: ($firstKey) + ($secondKey)"
    }
}

data class WrappedGetMeshFromMeshCacheKey<K1 : MeshCacheKey, K2 : MeshCacheKey>(val getMeshFromCache: GetMeshFor.FromCache<IntersectingSourceStateMeshCacheKey<K1, K2>>) : GetMeshFor<IntersectingSourceStateMeshCacheKey<K1, K2>>, Invalidate<ShapeKey<IntersectingSourceStateMeshCacheKey<K1, K2>>> {

    override fun getMeshFor(key: ShapeKey<IntersectingSourceStateMeshCacheKey<K1, K2>>): PainteraTriangleMesh? {
        return getMeshFromCache.getMeshFor(key)
    }

    override fun invalidate(key: ShapeKey<IntersectingSourceStateMeshCacheKey<K1, K2>>?) {
        getMeshFromCache.invalidate(key)
    }

    override fun invalidateIf(parallelismThreshold: Long, condition: Predicate<ShapeKey<IntersectingSourceStateMeshCacheKey<K1, K2>>>?) {
        TODO("Not yet implemented")
    }

    override fun invalidateAll(parallelismThreshold: Long) {
        getMeshFromCache.invalidateAll(parallelismThreshold)
    }

}
