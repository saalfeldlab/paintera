package org.janelia.saalfeldlab.paintera.cache

import net.imglib2.cache.Invalidate
import java.util.function.Predicate

class InvalidateDelegates<K>(invalidate: Collection<Invalidate<K>>): Invalidate<K> {

	constructor(vararg invalidate: Invalidate<K>) : this(listOf(*invalidate))

	private val invalidate = invalidate.map { it }

	override fun invalidate(key: K) = invalidate.forEach { it.invalidate(key) }
	override fun invalidateAll(parallelismThreshold: Long) = invalidate.forEach { it.invalidateAll(parallelismThreshold) }
	override fun invalidateIf(parallelismThreshold: Long, condition: Predicate<K>) = invalidate.forEach { it.invalidateIf(parallelismThreshold, condition) }

}
