package org.janelia.saalfeldlab.paintera.stream

import io.github.oshai.kotlinlogging.KotlinLogging

class ARGBStreamSeedSetter(private val stream: AbstractHighlightingARGBStream) {

    @JvmOverloads
    fun incrementStreamSeed(increment: Long = 1L) = changeStreamSeed { it + increment }

    @JvmOverloads
    fun decrementStreamSeed(decrement: Long = 1L) = changeStreamSeed { it - decrement }

    @JvmOverloads
    fun setStreamSeed(seed: Long) = changeStreamSeed { seed }

    private fun changeStreamSeed(seedUpdate: (Long) -> Long): Boolean {
        val seed = stream.seed
        val currentSeed = seedUpdate(seed)
        if (currentSeed == seed)
            return false

        LOG.debug("Updating seed from {} to {}", seed, currentSeed)
        stream.setSeed(currentSeed)
        stream.clearCache()
        return true
    }

    companion object {
        private val LOG = KotlinLogging.logger {}
    }

}
