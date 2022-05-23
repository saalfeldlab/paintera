package org.janelia.saalfeldlab.util

import bdv.img.cache.CreateInvalidVolatileCell
import bdv.img.cache.VolatileCachedCellImg
import bdv.util.volatiles.SharedQueue
import bdv.util.volatiles.VolatileTypeMatcher
import net.imglib2.RandomAccessibleInterval
import net.imglib2.Volatile
import net.imglib2.cache.Invalidate
import net.imglib2.cache.img.CachedCellImg
import net.imglib2.cache.ref.WeakRefVolatileCache
import net.imglib2.cache.volatiles.CacheHints
import net.imglib2.img.basictypeaccess.AccessFlags
import net.imglib2.img.basictypeaccess.volatiles.VolatileArrayDataAccess
import net.imglib2.type.NativeType

@Deprecated("Use this until cache is exposed in VolatileViews.wrapAsVolatile")
class TmpVolatileHelpers {

    data class RaiWithInvalidate<T> constructor(val rai: RandomAccessibleInterval<T>, val invalidate: Invalidate<Long>?)

    companion object {
        @Deprecated("Use this until cache is exposed in VolatileViews.wrapAsVolatile", ReplaceWith("VolatileViews.wrapAsVolatile(cachedcellImg, queue, hints)"))
        @JvmStatic
        fun <D, T, A> createVolatileCachedCellImgWithInvalidate(
            cachedCellImg: CachedCellImg<D, A>,
            queue: SharedQueue,
            hints: CacheHints,
        ): RaiWithInvalidate<T> where D : NativeType<D>, T : NativeType<T>, T : Volatile<D>, A : VolatileArrayDataAccess<A> {

            val dType = cachedCellImg.createLinkedType()
            val tType = VolatileTypeMatcher.getVolatileTypeForType(dType) as T
            val grid = cachedCellImg.cellGrid
            val cache = cachedCellImg.cache

            val flags = AccessFlags.ofAccess(cachedCellImg.accessType)
            if (!flags.contains(AccessFlags.VOLATILE))
                throw IllegalArgumentException("underlying ${CachedCellImg::class.java.simpleName} must have volatile access type")
            val dirty = flags.contains(AccessFlags.DIRTY)

            val createInvalid = CreateInvalidVolatileCell.get<T, A>(grid, tType, dirty)
            val volatileCache = WeakRefVolatileCache(cache, queue, createInvalid)
            val volatileImg = VolatileCachedCellImg(grid, tType, hints, volatileCache)
            return RaiWithInvalidate<T>(volatileImg, volatileCache)
        }
    }

}
